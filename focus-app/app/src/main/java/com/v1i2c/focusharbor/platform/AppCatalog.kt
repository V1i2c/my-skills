package com.v1i2c.focusharbor.platform

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.v1i2c.focusharbor.domain.BlockingDecisionEngine

 data class LaunchableApp(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
)

object AppCatalog {
    fun launchableApps(context: Context, includeSystemApps: Boolean = false): List<LaunchableApp> {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        }

        return resolved.mapNotNull { info ->
            val activityInfo = info.activityInfo ?: return@mapNotNull null
            val appInfo = activityInfo.applicationInfo ?: return@mapNotNull null
            val packageName = activityInfo.packageName ?: return@mapNotNull null
            if (packageName == context.packageName || BlockingDecisionEngine.isProtectedPackage(packageName)) {
                return@mapNotNull null
            }
            val isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
            if (!includeSystemApps && isSystem) return@mapNotNull null
            LaunchableApp(
                packageName = packageName,
                label = info.loadLabel(packageManager).toString().ifBlank { packageName },
                isSystemApp = isSystem,
            )
        }
            .distinctBy(LaunchableApp::packageName)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    fun isKnownBrowser(packageName: String): Boolean = packageName in BrowserAddressBarCatalog.knownPackages
}

object BrowserAddressBarCatalog {
    private val addressBarIds = mapOf(
        "com.android.chrome" to listOf("url_bar"),
        "com.chrome.beta" to listOf("url_bar"),
        "org.chromium.chrome.browser" to listOf("url_bar"),
        "com.sec.android.app.sbrowser" to listOf("location_bar_edit_text"),
        "com.microsoft.emmx" to listOf("url_bar"),
        "com.brave.browser" to listOf("url_bar"),
        "com.opera.browser" to listOf("url_field"),
        "com.opera.touch" to listOf("addressbarEdit"),
        "org.mozilla.firefox" to listOf("url_bar_title", "ADDRESSBAR_URL_BOX"),
        "com.duckduckgo.mobile.android" to listOf("omnibarTextInput"),
        "com.vivaldi.browser" to listOf("url_bar"),
        "com.google.android.googlequicksearchbox" to listOf("googleapp_srp_search_box_text"),
    )

    val knownPackages: Set<String> = addressBarIds.keys + setOf(
        "com.puffin.browser",
        "com.opera.mini.native",
        "com.android.browser",
    )

    fun supportsAddressBar(packageName: String): Boolean = packageName in addressBarIds

    fun findHost(service: android.accessibilityservice.AccessibilityService, packageName: String): String? {
        val ids = addressBarIds[packageName] ?: return null
        val root = service.rootInActiveWindow ?: return null
        for (id in ids) {
            val nodeId = if (id == "ADDRESSBAR_URL_BOX") id else "$packageName:id/$id"
            val nodes = runCatching { root.findAccessibilityNodeInfosByViewId(nodeId) }.getOrNull().orEmpty()
            for (node in nodes) {
                val text = node.text?.toString()?.takeIf(String::isNotBlank)
                    ?: node.contentDescription?.toString()?.takeIf(String::isNotBlank)
                    ?: continue
                if (com.v1i2c.focusharbor.domain.DomainMatcher.normalize(text) != null) return text
            }
        }
        return null
    }
}
