package com.batterymonitorpro

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import org.json.JSONObject

class BatteryWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val request = PeriodicWorkRequestBuilder<BatteryWidgetWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BatteryWidgetWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(BatteryWidgetWorker.WORK_NAME)
        super.onDisabled(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val sticky = context.registerReceiver(null, IntentFilterCompat.batteryChanged())
        val level = sticky?.getIntExtra("level", -1) ?: -1
        val scale = sticky?.getIntExtra("scale", 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        val statusCode = sticky?.getIntExtra("status", -1) ?: -1
        val state = when (statusCode) {
            2 -> "Заряжается"
            5 -> "Заряжена"
            3 -> "Разряжается"
            else -> "Состояние неизвестно"
        }
        updateAll(context, JSONObject().put("level", pct).put("statusText", state).toString())
    }

    companion object {
        fun updateAll(context: Context, json: String) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, BatteryWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            val data = try { JSONObject(json) } catch (_: Exception) { JSONObject() }
            val level = data.optInt("level", -1)
            val status = when (data.optString("status")) {
                "charging" -> "Заряжается"
                "discharging" -> "Разряжается"
                "full" -> "Заряжена"
                "not_charging" -> "Не заряжается"
                else -> data.optString("statusText", "Состояние неизвестно")
            }
            val temp = if (data.has("temperatureC") && !data.isNull("temperatureC"))
                " • ${data.optDouble("temperatureC").toInt()}°C" else ""
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.battery_widget)
                views.setTextViewText(R.id.widget_percent, if (level >= 0) "$level%" else "—")
                views.setTextViewText(R.id.widget_status, status + temp)
                val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (launch != null) {
                    val pending = PendingIntent.getActivity(
                        context, 0, launch,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_percent, pending)
                    views.setOnClickPendingIntent(R.id.widget_status, pending)
                }
                manager.updateAppWidget(id, views)
            }
        }
    }
}

private object IntentFilterCompat {
    fun batteryChanged() = android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
}
