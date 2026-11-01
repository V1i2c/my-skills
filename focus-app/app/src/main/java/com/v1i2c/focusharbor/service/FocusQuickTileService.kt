package com.v1i2c.focusharbor.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.v1i2c.focusharbor.FocusApplication
import com.v1i2c.focusharbor.MainActivity
import com.v1i2c.focusharbor.domain.DomainMatcher
import com.v1i2c.focusharbor.domain.QuickBlockSession
import com.v1i2c.focusharbor.widget.FocusHarborWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FocusQuickTileService : TileService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        val repository = (application as FocusApplication).repository
        serviceScope.launch {
            repository.awaitReady()
            withContext(Dispatchers.Main) { refreshTile() }
        }
    }

    override fun onClick() {
        super.onClick()
        val repository = (application as FocusApplication).repository
        serviceScope.launch {
            repository.awaitReady()
            val state = repository.state.value
            val nowEpoch = System.currentTimeMillis()
            val nowElapsed = SystemClock.elapsedRealtime()
            val running = state.quickBlock?.let { !it.isExpired(nowEpoch, nowElapsed) } == true
            if (running) {
                val updated = repository.update { it.copy(quickBlock = null) }
                repository.awaitState(updated)
            } else if (state.settings.quickBlockApps.isNotEmpty() || state.settings.quickBlockDomains.isNotEmpty()) {
                val minutes = state.settings.quickBlockMinutes.coerceIn(1L, 365L * 24L * 60L)
                val updated = repository.update { current ->
                    current.copy(quickBlock = QuickBlockSession(
                        startedAtEpochMillis = nowEpoch,
                        startedAtElapsedMillis = nowElapsed,
                        durationMillis = minutes * 60_000L,
                        appPackages = state.settings.quickBlockApps,
                        websiteDomains = state.settings.quickBlockDomains.mapNotNull(DomainMatcher::normalize).toSet(),
                    ))
                }
                repository.awaitState(updated)
            } else {
                withContext(Dispatchers.Main) { openMainForQuickBlock() }
            }
            withContext(Dispatchers.Main) {
                FocusHarborWidgetProvider.updateAll(this@FocusQuickTileService)
                refreshTile()
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val state = (application as FocusApplication).repository.state.value
        val session = state.quickBlock
        val active = session != null && !session.isExpired(System.currentTimeMillis(), SystemClock.elapsedRealtime())
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Quick Block"
        tile.subtitle = if (active) "Focus session running" else "Tap to start"
        tile.contentDescription = if (active) "Quick Block is running. Tap to end it." else "Start Quick Block"
        tile.updateTile()
    }

    private fun openMainForQuickBlock() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_WIDGET_QUICK_BLOCK)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                73,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
