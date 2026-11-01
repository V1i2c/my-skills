package com.v1i2c.focusharbor.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.v1i2c.focusharbor.FocusApplication
import com.v1i2c.focusharbor.data.FocusRepository
import com.v1i2c.focusharbor.domain.BlockedNotification
import com.v1i2c.focusharbor.domain.BlockingDecisionEngine
import com.v1i2c.focusharbor.platform.AndroidTriggers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

class NotificationCaptureService : NotificationListenerService() {
    private lateinit var repository: FocusRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        repository = (application as FocusApplication).repository
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return
        if (packageName == this.packageName) return
        val now = ZonedDateTime.now()
        val state = repository.state.value
        val wifiSsid = if (state.settings.wifiTriggersEnabled) AndroidTriggers.currentWifiSsid(this) else null
        val location = if (state.settings.locationTriggersEnabled) AndroidTriggers.lastKnownLocation(this) else null
        val decision = BlockingDecisionEngine.shouldBlockPackage(
            state = state,
            packageName = packageName,
            now = now,
            wifiSsid = wifiSsid,
            location = location,
            nowElapsedMillis = android.os.SystemClock.elapsedRealtime(),
        )
        val profileWantsNotifications = BlockingDecisionEngine.activeProfiles(state, now, wifiSsid, location)
            .any { it.blockNotifications && packageName in it.appPackages }
        if (!decision.blocked || (!state.settings.notificationBlockingEnabled && !profileWantsNotifications)) return

        runCatching { cancelNotification(sbn.key) }
        val extras = sbn.notification.extras
        val record = BlockedNotification(
            packageName = packageName,
            title = extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty().take(300),
            body = (extras?.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)
                ?: extras?.getCharSequence(android.app.Notification.EXTRA_TEXT))?.toString().orEmpty().take(1_000),
            capturedAtEpochMillis = sbn.postTime,
        )
        serviceScope.launch {
            repository.update { current ->
                current.copy(blockedNotifications = (current.blockedNotifications + record).takeLast(MAX_HISTORY_ITEMS))
            }
        }
    }

    companion object {
        private const val MAX_HISTORY_ITEMS = 2_000
    }
}
