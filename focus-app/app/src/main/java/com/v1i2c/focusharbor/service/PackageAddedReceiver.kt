package com.v1i2c.focusharbor.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.v1i2c.focusharbor.FocusApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PackageAddedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED || intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
        val packageName = intent.data?.schemeSpecificPart?.takeIf(String::isNotBlank) ?: return
        if (packageName == context.packageName) return
        val isUserApp = runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            info.flags and ApplicationInfo.FLAG_SYSTEM == 0
        }.getOrDefault(false)
        if (!isUserApp) return

        val pendingResult = goAsync()
        val repository = (context.applicationContext as FocusApplication).repository
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.update { current ->
                    current.copy(profiles = current.profiles.map { profile ->
                        if (!profile.autoBlockNewApps) profile
                        else if (profile.allowList) profile.copy(appPackages = profile.appPackages - packageName)
                        else profile.copy(appPackages = profile.appPackages + packageName)
                    })
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
