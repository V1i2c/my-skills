package com.v1i2c.focusharbor.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.v1i2c.focusharbor.service.FocusAccessibilityService
import com.v1i2c.focusharbor.service.NotificationCaptureService

object PermissionState {
    fun accessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':').any { flat ->
            val component = ComponentName.unflattenFromString(flat) ?: return@any false
            component.packageName == context.packageName &&
                component.className == FocusAccessibilityService::class.java.name
        }
    }

    fun notificationListenerEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        return enabled.split(':').any { flat ->
            val component = ComponentName.unflattenFromString(flat) ?: return@any false
            component.packageName == context.packageName &&
                component.className == NotificationCaptureService::class.java.name
        }
    }

    fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun notificationSettingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.parse("package:${context.packageName}"))
}
