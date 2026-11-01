package com.v1i2c.focusharbor.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.v1i2c.focusharbor.FocusApplication
import com.v1i2c.focusharbor.MainActivity
import com.v1i2c.focusharbor.data.FocusRepository
import com.v1i2c.focusharbor.data.StateOperations
import com.v1i2c.focusharbor.domain.BlockDecision
import com.v1i2c.focusharbor.domain.BlockingDecisionEngine
import com.v1i2c.focusharbor.domain.FocusAppState
import com.v1i2c.focusharbor.domain.GeoPoint
import com.v1i2c.focusharbor.domain.QuotaPeriod
import com.v1i2c.focusharbor.domain.QuotaPeriodKey
import com.v1i2c.focusharbor.domain.SubAppFeature
import com.v1i2c.focusharbor.platform.AndroidTriggers
import com.v1i2c.focusharbor.platform.AppCatalog
import com.v1i2c.focusharbor.platform.BrowserAddressBarCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

class FocusAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: FocusRepository
    private var overlayView: View? = null
    private var overlayPackage: String? = null
    private var foregroundPackage: String? = null
    private var foregroundStartedAtElapsed: Long = 0L
    private var lastBlockedPackage: String? = null
    private var lastBlockRecordedAtElapsed: Long = 0L
    private var cachedWifiSsid: String? = null
    private var cachedLocation: GeoPoint? = null
    private var triggerSnapshotAtElapsed: Long = 0L
    private var userPresentRegistered = false

    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_USER_PRESENT) return
            serviceScope.launch {
                repository.update { StateOperations.recordScreenUnlock(it) }
            }
        }
    }

    private val periodicCheck = object : Runnable {
        override fun run() {
            val nowElapsed = SystemClock.elapsedRealtime()
            flushForegroundUsage(nowElapsed)
            foregroundPackage?.let(::evaluatePackage)
            mainHandler.postDelayed(this, CHECK_INTERVAL_MILLIS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = (application as FocusApplication).repository
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 120L
        }
        runCatching {
            ContextCompat.registerReceiver(
                this,
                userPresentReceiver,
                IntentFilter(Intent.ACTION_USER_PRESENT),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            userPresentRegistered = true
        }
        mainHandler.removeCallbacks(periodicCheck)
        mainHandler.post(periodicCheck)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString()?.takeIf(String::isNotBlank) ?: return
        if (packageName == this.packageName && overlayView != null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && packageName != foregroundPackage) {
            val nowElapsed = SystemClock.elapsedRealtime()
            flushForegroundUsage(nowElapsed)
            foregroundPackage = packageName
            foregroundStartedAtElapsed = nowElapsed
            if (packageName != this.packageName && !BlockingDecisionEngine.isProtectedPackage(packageName)) {
                updateUsage(packageName, foregroundDeltaMillis = 0L, launchDelta = 1)
            }
        }

        if (packageName == this.packageName || BlockingDecisionEngine.isProtectedPackage(packageName)) {
            hideOverlay()
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            evaluatePackage(packageName)
        }
    }

    override fun onInterrupt() {
        hideOverlay()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(periodicCheck)
        hideOverlay()
        if (userPresentRegistered) runCatching { unregisterReceiver(userPresentReceiver) }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun evaluatePackage(packageName: String) {
        if (packageName == this.packageName || BlockingDecisionEngine.isProtectedPackage(packageName)) {
            hideOverlay()
            return
        }

        val now = ZonedDateTime.now()
        val state = repository.state.value
        refreshTriggerSnapshot()
        val browserKnown = AppCatalog.isKnownBrowser(packageName)
        val unsupportedBrowser = browserKnown && !BrowserAddressBarCatalog.supportsAddressBar(packageName)
        var decision = BlockingDecisionEngine.shouldBlockPackage(
            state = state,
            packageName = packageName,
            now = now,
            wifiSsid = cachedWifiSsid,
            location = cachedLocation,
            nowElapsedMillis = SystemClock.elapsedRealtime(),
            unsupportedBrowser = unsupportedBrowser,
        )

        if (!decision.blocked && browserKnown && BrowserAddressBarCatalog.supportsAddressBar(packageName)) {
            val host = runCatching { BrowserAddressBarCatalog.findHost(this, packageName) }.getOrNull()
            if (host != null) {
                decision = BlockingDecisionEngine.shouldBlockWebsite(
                    state = state,
                    hostOrUrl = host,
                    now = now,
                    wifiSsid = cachedWifiSsid,
                    location = cachedLocation,
                    nowElapsedMillis = SystemClock.elapsedRealtime(),
                )
            }
        }

        if (!decision.blocked) {
            val activeProfiles = BlockingDecisionEngine.activeProfiles(state, now, cachedWifiSsid, cachedLocation)
            val blockedFeature = if (activeProfiles.any { it.subAppFeatures.isNotEmpty() }) {
                runCatching { SubAppDetector.detect(this, packageName) }.getOrNull()
            } else {
                null
            }
            val matchingProfile = blockedFeature?.let { feature ->
                activeProfiles.firstOrNull { feature in it.subAppFeatures }
            }
            if (matchingProfile != null) {
                decision = BlockDecision(
                    blocked = true,
                    reason = "This section is paused by ${matchingProfile.name}.",
                    profileId = matchingProfile.id,
                    profileName = matchingProfile.name,
                )
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }

        if (decision.blocked) {
            recordBlockedAttempt(packageName)
            showOverlay(packageName, decision)
        } else {
            hideOverlay()
        }
    }

    private fun refreshTriggerSnapshot() {
        val nowElapsed = SystemClock.elapsedRealtime()
        if (nowElapsed - triggerSnapshotAtElapsed < TRIGGER_CACHE_MILLIS) return
        triggerSnapshotAtElapsed = nowElapsed
        val state = repository.state.value
        cachedWifiSsid = if (state.settings.wifiTriggersEnabled) {
            runCatching { AndroidTriggers.currentWifiSsid(this) }.getOrNull()
        } else {
            null
        }
        cachedLocation = if (state.settings.locationTriggersEnabled) {
            runCatching { AndroidTriggers.lastKnownLocation(this) }.getOrNull()
        } else {
            null
        }
    }

    private fun flushForegroundUsage(nowElapsed: Long) {
        val packageName = foregroundPackage ?: return
        if (packageName == this.packageName || BlockingDecisionEngine.isProtectedPackage(packageName)) {
            foregroundStartedAtElapsed = nowElapsed
            return
        }
        if (foregroundStartedAtElapsed <= 0L) {
            foregroundStartedAtElapsed = nowElapsed
            return
        }
        val delta = (nowElapsed - foregroundStartedAtElapsed).coerceAtLeast(0L).coerceAtMost(MAX_SINGLE_USAGE_FLUSH_MILLIS)
        foregroundStartedAtElapsed = nowElapsed
        if (delta > 0L) updateUsage(packageName, foregroundDeltaMillis = delta)
    }

    private fun updateUsage(
        packageName: String,
        foregroundDeltaMillis: Long = 0L,
        launchDelta: Int = 0,
        blockedDelta: Int = 0,
    ) {
        if (packageName in repository.state.value.settings.ignoredStatisticsPackages) return
        val now = ZonedDateTime.now()
        val nowElapsed = SystemClock.elapsedRealtime()
        val currentState = repository.state.value
        val quickBlockActive = currentState.quickBlock?.let { !it.isExpired(now.toInstant().toEpochMilli(), nowElapsed) } == true
        val focusSessionActive = quickBlockActive || BlockingDecisionEngine.activeProfiles(
            currentState,
            now,
            cachedWifiSsid,
            cachedLocation,
        ).isNotEmpty()
        val dateKey = now.toLocalDate().toString()
        serviceScope.launch {
            repository.update { initial ->
                QuotaPeriod.entries.fold(initial) { current, period ->
                    StateOperations.recordUsage(
                        state = current,
                        packageName = packageName,
                        periodKey = QuotaPeriodKey.forPeriod(now, period),
                        period = period,
                        foregroundDeltaMillis = foregroundDeltaMillis,
                        launchDelta = launchDelta,
                        blockedDelta = blockedDelta,
                        dateKey = dateKey,
                        recordFocus = period == QuotaPeriod.DAILY && focusSessionActive,
                    )
                }
            }
        }
    }

    private fun recordBlockedAttempt(packageName: String) {
        val nowElapsed = SystemClock.elapsedRealtime()
        if (lastBlockedPackage != packageName || nowElapsed - lastBlockRecordedAtElapsed > BLOCK_LOG_THROTTLE_MILLIS) {
            lastBlockedPackage = packageName
            lastBlockRecordedAtElapsed = nowElapsed
            updateUsage(packageName, blockedDelta = 1)
        }
    }

    private fun showOverlay(packageName: String, decision: BlockDecision) {
        if (overlayView != null && overlayPackage == packageName) return
        hideOverlay()
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(32), dp(28), dp(32))
            setBackgroundColor(Color.rgb(12, 23, 34))
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
            background = GradientDrawable().apply {
                setColor(Color.rgb(245, 248, 247))
                cornerRadius = dp(28).toFloat()
            }
        }
        val eyebrow = TextView(this).apply {
            text = "FOCUS HARBOR"
            textSize = 12f
            letterSpacing = 0.14f
            setTextColor(Color.rgb(22, 116, 105))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val title = TextView(this).apply {
            text = "Take a mindful pause"
            textSize = 25f
            setTextColor(Color.rgb(20, 38, 48))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(8))
        }
        val detail = TextView(this).apply {
            text = decision.reason.ifBlank { "This app is blocked right now." }
            textSize = 16f
            setTextColor(Color.rgb(76, 91, 99))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        val appLabel = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
        val appName = TextView(this).apply {
            text = appLabel
            textSize = 14f
            setTextColor(Color.rgb(91, 108, 114))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(20))
        }
        val homeButton = Button(this).apply {
            text = "Return to Home"
            contentDescription = "Return to the Android home screen"
            setOnClickListener {
                performGlobalAction(GLOBAL_ACTION_HOME)
                hideOverlay()
            }
        }
        val appButton = Button(this).apply {
            text = "Open Focus Harbor"
            contentDescription = "Open Focus Harbor to review your focus settings"
            setOnClickListener {
                runCatching {
                    startActivity(Intent(this@FocusAccessibilityService, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    })
                }.onFailure { performGlobalAction(GLOBAL_ACTION_HOME) }
                hideOverlay()
            }
        }
        card.addView(eyebrow, matchWrap())
        card.addView(title, matchWrap())
        card.addView(detail, matchWrap())
        card.addView(appName, matchWrap())
        card.addView(homeButton, matchWrap())
        card.addView(appButton, matchWrap())
        root.addView(card, LinearLayout.LayoutParams(matchWidth(), wrapHeight()))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            this.title = "Focus Harbor block screen"
        }
        runCatching {
            windowManager.addView(root, params)
            overlayView = root
            overlayPackage = packageName
        }.onFailure {
            overlayView = null
            overlayPackage = null
        }
    }

    private fun hideOverlay() {
        val view = overlayView ?: return
        overlayView = null
        overlayPackage = null
        runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeViewImmediate(view) }
    }

    private fun matchWrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun matchWidth(): Int = LinearLayout.LayoutParams.MATCH_PARENT
    private fun wrapHeight(): Int = LinearLayout.LayoutParams.WRAP_CONTENT
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val CHECK_INTERVAL_MILLIS = 15_000L
        private const val TRIGGER_CACHE_MILLIS = 30_000L
        private const val MAX_SINGLE_USAGE_FLUSH_MILLIS = 5 * 60_000L
        private const val BLOCK_LOG_THROTTLE_MILLIS = 30_000L
    }
}

private object SubAppDetector {
    private val resourceIds = mapOf(
        SubAppFeature.YOUTUBE_SHORTS to Pair("com.google.android.youtube", listOf("reel_player_page_container")),
        SubAppFeature.INSTAGRAM_REELS to Pair("com.instagram.android", listOf("comment_count")),
        SubAppFeature.INSTAGRAM_STORIES to Pair("com.instagram.android", listOf("reel_viewer_timestamp")),
        SubAppFeature.SNAPCHAT_STORIES to Pair("com.snapchat.android", listOf("df_large_story")),
        SubAppFeature.SNAPCHAT_SPOTLIGHT to Pair("com.snapchat.android", listOf("spotlight_attribution_title", "spotlight_container")),
        SubAppFeature.WHATSAPP_STATUS to Pair("com.whatsapp", listOf("status_playback_image", "status_playback_video")),
        SubAppFeature.WHATSAPP_CHANNELS to Pair("com.whatsapp", listOf("newsletter_quick_forwarding_pill_container_key", "follow_channel_button")),
    )

    fun detect(service: AccessibilityService, packageName: String): SubAppFeature? {
        val root = service.rootInActiveWindow ?: return null
        return resourceIds.entries.firstOrNull { (feature, target) ->
            if (target.first != packageName) return@firstOrNull false
            target.second.any { id ->
                val nodes = runCatching { root.findAccessibilityNodeInfosByViewId("$packageName:id/$id") }
                    .getOrNull()
                    .orEmpty()
                nodes.isNotEmpty()
            }
        }?.key
    }
}
