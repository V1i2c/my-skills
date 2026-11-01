package com.v1i2c.focusharbor.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.v1i2c.focusharbor.domain.GeoPoint

object AndroidTriggers {
    fun currentWifiSsid(context: Context): String? {
        val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasNearbyWifi = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        if (!hasLocation && !hasNearbyWifi) return null
        return runCatching {
            @Suppress("DEPRECATION")
            val ssid = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .connectionInfo?.ssid
            ssid?.takeUnless { it.isBlank() || it == "<unknown ssid>" }?.removeSurrounding("\"")
        }.getOrNull()
    }

    fun lastKnownLocation(context: Context): GeoPoint? {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null
        return runCatching {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            manager.getProviders(true)
                .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
                .maxByOrNull { it.time }
                ?.let { GeoPoint(it.latitude, it.longitude) }
        }.getOrNull()
    }

    fun isCharging(context: Context): Boolean = runCatching {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        plugged != 0
    }.getOrDefault(false)
}
