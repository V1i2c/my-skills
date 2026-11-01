package com.v1i2c.focusharbor.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.v1i2c.focusharbor.FocusApplication
import com.v1i2c.focusharbor.MainActivity
import com.v1i2c.focusharbor.R

class FocusHarborWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateOne(context, manager, id) }
    }

    private fun updateOne(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val state = (context.applicationContext as FocusApplication).repository.state.value
        val session = state.quickBlock
        val active = session != null && !session.isExpired(System.currentTimeMillis(), SystemClock.elapsedRealtime())
        val views = RemoteViews(context.packageName, R.layout.focus_harbor_widget).apply {
            setTextViewText(R.id.widget_title, "Focus Harbor")
            setTextViewText(
                R.id.widget_status,
                if (active) "Quick Block is running" else if (state.settings.quickBlockApps.isEmpty() && state.settings.quickBlockDomains.isEmpty()) "Choose apps or sites first" else "Ready for a focus session",
            )
            setTextViewText(R.id.widget_action, if (active) "Open focus" else "Start focus")
            val intent = Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_WIDGET_QUICK_BLOCK)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending = PendingIntent.getActivity(
                context,
                widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            setOnClickPendingIntent(R.id.widget_action, pending)
            setOnClickPendingIntent(R.id.widget_root, pending)
        }
        manager.updateAppWidget(widgetId, views)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, FocusHarborWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) FocusHarborWidgetProvider().onUpdate(context, manager, ids)
        }
    }
}
