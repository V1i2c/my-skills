package com.v1i2c.focusharbor.domain

import java.net.IDN
import java.net.URI
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

object ScheduleEvaluator {
    fun isActiveAt(rule: ScheduleRule, now: ZonedDateTime): Boolean {
        if (!rule.enabled || rule.daysOfWeek.isEmpty()) return false

        val today = now.dayOfWeek.value
        val minute = now.hour * 60 + now.minute
        return if (rule.startMinute < rule.endMinute) {
            today in rule.daysOfWeek && minute >= rule.startMinute && minute < rule.endMinute
        } else if (minute >= rule.startMinute) {
            today in rule.daysOfWeek
        } else if (minute < rule.endMinute) {
            previousIsoDay(today) in rule.daysOfWeek
        } else {
            false
        }
    }

    private fun previousIsoDay(day: Int): Int = if (day == DayOfWeek.MONDAY.value) 7 else day - 1
}

object DomainMatcher {
    /** Returns a normalized hostname, or null when the input is not a valid hostname. */
    fun normalize(input: String): String? {
        val raw = input.trim().removePrefix("*.")
        if (raw.isEmpty()) return null
        val withScheme = if (raw.contains("://")) raw else "https://$raw"
        val parsedHost = runCatching { URI(withScheme).host }.getOrNull()
        val candidate = (parsedHost ?: raw.substringBefore('/').substringBefore('?').substringBefore('#'))
            .trim()
            .lowercase()
            .removePrefix("www.")
            .trimEnd('.')
        if (candidate.isEmpty() || candidate.any { it.isWhitespace() }) return null

        val ascii = runCatching { IDN.toASCII(candidate, IDN.USE_STD3_ASCII_RULES).lowercase() }
            .getOrNull() ?: return null
        if (ascii.length > 253 || '.' !in ascii) return null
        val validLabel = Regex("^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$")
        if (ascii.split('.').any { it.length !in 1..63 || !validLabel.matches(it) }) return null
        return ascii
    }

    fun matches(hostOrUrl: String, configuredDomain: String): Boolean {
        val host = normalize(hostOrUrl) ?: return false
        val domain = normalize(configuredDomain) ?: return false
        return host == domain || host.endsWith(".$domain")
    }
}

object QuotaPeriodKey {
    fun forPeriod(now: ZonedDateTime, period: QuotaPeriod): String = when (period) {
        QuotaPeriod.HOURLY -> now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"))
        QuotaPeriod.DAILY -> now.toLocalDate().toString()
        QuotaPeriod.WEEKLY -> now.toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .toString()
    }
}

object QuotaEvaluator {
    fun isExceeded(quota: UsageQuota, usage: UsageRecord?): Boolean {
        if (usage == null) return false
        val timeExceeded = quota.maximumMinutes?.let { usage.foregroundMillis >= it * 60_000L } ?: false
        val launchesExceeded = quota.maximumLaunches?.let { usage.launches >= it } ?: false
        return timeExceeded || launchesExceeded
    }
}

data class BlockDecision(
    val blocked: Boolean,
    val reason: String = "",
    val profileId: String? = null,
    val profileName: String? = null,
    val blockedDomain: String? = null,
    val isQuickBlock: Boolean = false,
)

object BlockingDecisionEngine {
    private val protectedPackages = setOf(
        "com.android.settings",
        "com.android.systemui",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.dialer",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.incallui",
        "com.android.emergency",
        "com.android.launcher3",
        "com.android.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.google.android.launcher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.v1i2c.focusharbor",
    )
    private val protectedPackageFragments = listOf(
        "systemui",
        "permissioncontroller",
        "packageinstaller",
        "inputmethod",
        "keyboard",
        "launcher",
        "incallui",
        "emergency",
    )

    fun isProtectedPackage(packageName: String): Boolean =
        packageName in protectedPackages || protectedPackageFragments.any { it in packageName.lowercase() }

    fun activeProfiles(
        state: FocusAppState,
        now: ZonedDateTime,
        wifiSsid: String? = null,
        location: GeoPoint? = null,
    ): List<FocusProfile> = state.profiles.filter { profile ->
        val strictSessionActive = profile.strictSession != null
        if (strictSessionActive) return@filter true
        if (!profile.enabled) return@filter false
        val nowMillis = now.toInstant().toEpochMilli()
        if (profile.pauseUntilEpochMillis?.let { nowMillis < it } == true) return@filter false
        val scheduleActive = profile.schedules.isEmpty() || profile.schedules.any { it.isActiveAt(now) }
        if (!scheduleActive) return@filter false
        val wifiActive = profile.wifiSsids.isEmpty() ||
            (wifiSsid != null && profile.wifiSsids.any { normalizeSsid(it) == normalizeSsid(wifiSsid) })
        if (!wifiActive) return@filter false
        val fence = profile.geofence
        fence == null || (location != null && distanceMeters(fence.latitude, fence.longitude, location.latitude, location.longitude) <= fence.radiusMeters)
    }

    fun shouldBlockPackage(
        state: FocusAppState,
        packageName: String,
        now: ZonedDateTime,
        wifiSsid: String? = null,
        location: GeoPoint? = null,
        nowElapsedMillis: Long = System.nanoTime() / 1_000_000L,
        unsupportedBrowser: Boolean = false,
    ): BlockDecision {
        if (packageName.isBlank() || isProtectedPackage(packageName)) return BlockDecision(false)

        val quickBlock = state.quickBlock
        val nowEpoch = now.toInstant().toEpochMilli()
        if (quickBlock != null && !quickBlock.isExpired(nowEpoch, nowElapsedMillis) && packageName in quickBlock.appPackages) {
            return BlockDecision(true, "Quick Block is active", isQuickBlock = true)
        }

        for (profile in activeProfiles(state, now, wifiSsid, location)) {
            val selected = packageName in profile.appPackages
            if (profile.allowList && !selected) {
                return BlockDecision(true, "Not on the profile allowlist", profile.id, profile.name)
            }
            if (!profile.allowList && selected) {
                return BlockDecision(true, profile.blockMessage, profile.id, profile.name)
            }
            if (unsupportedBrowser && profile.blockUnsupportedBrowsers && profile.websiteDomains.isNotEmpty()) {
                return BlockDecision(true, "This browser cannot be checked for blocked websites", profile.id, profile.name)
            }
            val quota = profile.quotas[packageName] ?: continue
            val periodKey = QuotaPeriodKey.forPeriod(now, quota.period)
            val usage = state.usage.firstOrNull {
                it.packageName == packageName && it.periodKey == periodKey && it.period == quota.period
            }
            if (QuotaEvaluator.isExceeded(quota, usage)) {
                return BlockDecision(true, "Usage limit reached", profile.id, profile.name)
            }
        }
        return BlockDecision(false)
    }

    fun shouldBlockWebsite(
        state: FocusAppState,
        hostOrUrl: String,
        now: ZonedDateTime,
        wifiSsid: String? = null,
        location: GeoPoint? = null,
        nowElapsedMillis: Long = System.nanoTime() / 1_000_000L,
    ): BlockDecision {
        val normalizedHost = DomainMatcher.normalize(hostOrUrl) ?: return BlockDecision(false)
        val quickBlock = state.quickBlock
        val nowEpoch = now.toInstant().toEpochMilli()
        if (quickBlock != null && !quickBlock.isExpired(nowEpoch, nowElapsedMillis)) {
            val matched = quickBlock.websiteDomains.firstOrNull { DomainMatcher.matches(normalizedHost, it) }
            if (matched != null) {
                return BlockDecision(true, "Quick Block is active", blockedDomain = normalizedHost, isQuickBlock = true)
            }
        }

        if (state.settings.adultContentBlockingEnabled) {
            val adultDomain = state.settings.adultDomains.firstOrNull { DomainMatcher.matches(normalizedHost, it) }
            if (adultDomain != null) {
                return BlockDecision(true, "This website is on your local blocked-domain list", blockedDomain = normalizedHost)
            }
        }

        for (profile in activeProfiles(state, now, wifiSsid, location)) {
            val matched = profile.websiteDomains.firstOrNull { DomainMatcher.matches(normalizedHost, it) }
            if (profile.allowList && matched == null) {
                return BlockDecision(true, "This website is not on the profile allowlist", profile.id, profile.name, normalizedHost)
            }
            if (!profile.allowList && matched != null) {
                return BlockDecision(true, profile.blockMessage, profile.id, profile.name, normalizedHost)
            }
        }
        return BlockDecision(false)
    }

    fun normalizeSsid(value: String): String = value.trim().removeSurrounding("\"").trim().lowercase()

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2).let { it * it }
        val boundedA = a.coerceIn(0.0, 1.0)
        return 2 * earthRadiusMeters * kotlin.math.atan2(kotlin.math.sqrt(boundedA), kotlin.math.sqrt(1 - boundedA))
    }
}

data class GeoPoint(val latitude: Double, val longitude: Double)
