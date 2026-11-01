package com.v1i2c.focusharbor.domain

import kotlinx.serialization.Serializable
import java.time.ZonedDateTime
import java.util.UUID

private const val MAX_STRICT_MINUTES = 365L * 24L * 60L

@Serializable
data class FocusAppState(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val profiles: List<FocusProfile> = emptyList(),
    val quickBlock: QuickBlockSession? = null,
    val usage: List<UsageRecord> = emptyList(),
    val dailyStats: List<DailyStats> = emptyList(),
    val blockedNotifications: List<BlockedNotification> = emptyList(),
    val settings: FocusSettings = FocusSettings(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class FocusProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Focus profile",
    val enabled: Boolean = false,
    val appPackages: Set<String> = emptySet(),
    val websiteDomains: Set<String> = emptySet(),
    val subAppFeatures: Set<SubAppFeature> = emptySet(),
    val schedules: List<ScheduleRule> = emptyList(),
    val quotas: Map<String, UsageQuota> = emptyMap(),
    val allowList: Boolean = false,
    val blockNotifications: Boolean = false,
    val autoBlockNewApps: Boolean = false,
    val blockUnsupportedBrowsers: Boolean = false,
    val wifiSsids: Set<String> = emptySet(),
    val geofence: GeofenceRule? = null,
    val pauseUntilEpochMillis: Long? = null,
    val blockMessage: String = "This app is paused for your focus.",
    val strictConfig: StrictModeConfig = StrictModeConfig(),
    val strictSession: StrictModeSession? = null,
)

/** ISO-8601 weekdays: Monday = 1, Sunday = 7. End times are exclusive. */
@Serializable
data class ScheduleRule(
    val daysOfWeek: Set<Int>,
    val startMinute: Int,
    val endMinute: Int,
    val enabled: Boolean = true,
) {
    init {
        require(daysOfWeek.all { it in 1..7 }) { "Weekday values must be in 1..7" }
        require(startMinute in 0..1439) { "Start minute must be within the day" }
        require(endMinute in 0..1439) { "End minute must be within the day" }
        require(startMinute != endMinute) { "Start and end times must differ" }
    }

    fun isActiveAt(now: ZonedDateTime): Boolean = ScheduleEvaluator.isActiveAt(this, now)
}

@Serializable
data class UsageQuota(
    val maximumMinutes: Long? = null,
    val maximumLaunches: Int? = null,
    val period: QuotaPeriod = QuotaPeriod.DAILY,
) {
    init {
        require(maximumMinutes == null || maximumMinutes > 0) { "Time quota must be positive" }
        require(maximumLaunches == null || maximumLaunches > 0) { "Launch quota must be positive" }
        require(maximumMinutes != null || maximumLaunches != null) { "At least one quota is required" }
    }
}

@Serializable
enum class QuotaPeriod { HOURLY, DAILY, WEEKLY }

@Serializable
data class UsageRecord(
    val periodKey: String,
    val packageName: String,
    val period: QuotaPeriod = QuotaPeriod.DAILY,
    val foregroundMillis: Long = 0L,
    val launches: Int = 0,
    val blockedAttempts: Int = 0,
)

@Serializable
data class DailyStats(
    val dateKey: String,
    val screenUnlocks: Int = 0,
    val focusMinutes: Long = 0L,
    val focusMillisRemainder: Long = 0L,
)

@Serializable
data class QuickBlockSession(
    val startedAtEpochMillis: Long,
    val startedAtElapsedMillis: Long,
    val durationMillis: Long,
    val appPackages: Set<String> = emptySet(),
    val websiteDomains: Set<String> = emptySet(),
) {
    init {
        require(durationMillis > 0L) { "Quick Block duration must be positive" }
    }

    fun isExpired(nowEpochMillis: Long, nowElapsedMillis: Long): Boolean {
        val elapsed = if (nowElapsedMillis >= startedAtElapsedMillis) {
            nowElapsedMillis - startedAtElapsedMillis
        } else {
            nowEpochMillis - startedAtEpochMillis
        }
        return elapsed >= durationMillis
    }

    fun remainingMillis(nowEpochMillis: Long, nowElapsedMillis: Long): Long {
        val elapsed = if (nowElapsedMillis >= startedAtElapsedMillis) {
            nowElapsedMillis - startedAtElapsedMillis
        } else {
            nowEpochMillis - startedAtEpochMillis
        }
        return (durationMillis - elapsed).coerceAtLeast(0L)
    }
}

@Serializable
data class StrictModeConfig(
    val enabled: Boolean = false,
    val lockEditing: Boolean = true,
    val timerMinutes: Long? = null,
    val requireScheduleEnd: Boolean = false,
    val requireCharger: Boolean = false,
    val requirePin: Boolean = false,
    val pinVerifier: PinVerifier? = null,
    val cooldownMinutes: Long = 0L,
    val emergencyCooldownMinutes: Long = 5L,
) {
    init {
        require(timerMinutes == null || timerMinutes in 1L..MAX_STRICT_MINUTES) { "Strict timer must be 1 minute to 1 year" }
        require(cooldownMinutes in 0L..MAX_STRICT_MINUTES) { "Cooldown must be between zero and 1 year" }
        require(emergencyCooldownMinutes in 1L..1440L) { "Emergency cooldown must be 1–1440 minutes" }
    }
}

@Serializable
data class StrictModeSession(
    val startedAtEpochMillis: Long,
    val startedAtElapsedMillis: Long,
    val timerEndsAtEpochMillis: Long? = null,
    val releaseCooldownUntilEpochMillis: Long? = null,
    val emergencyStartedAtEpochMillis: Long? = null,
    val emergencyStartedAtElapsedMillis: Long? = null,
)

@Serializable
data class PinVerifier(
    val saltBase64: String,
    val digestBase64: String,
    val iterations: Int = PinHasher.DEFAULT_ITERATIONS,
)

@Serializable
enum class SubAppFeature {
    YOUTUBE_SHORTS,
    INSTAGRAM_REELS,
    INSTAGRAM_STORIES,
    SNAPCHAT_STORIES,
    SNAPCHAT_SPOTLIGHT,
    WHATSAPP_STATUS,
    WHATSAPP_CHANNELS,
}

@Serializable
data class GeofenceRule(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
) {
    init {
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)
        require(radiusMeters in 50f..100_000f)
    }
}

@Serializable
data class BlockedNotification(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    val title: String,
    val body: String,
    val capturedAtEpochMillis: Long,
    val isRead: Boolean = false,
)

@Serializable
data class FocusSettings(
    val notificationBlockingEnabled: Boolean = false,
    val adultContentBlockingEnabled: Boolean = false,
    val adultDomains: Set<String> = emptySet(),
    val ignoredStatisticsPackages: Set<String> = emptySet(),
    val wifiTriggersEnabled: Boolean = false,
    val locationTriggersEnabled: Boolean = false,
    val quickBlockApps: Set<String> = emptySet(),
    val quickBlockDomains: Set<String> = emptySet(),
    val quickBlockMinutes: Long = 25L,
)
