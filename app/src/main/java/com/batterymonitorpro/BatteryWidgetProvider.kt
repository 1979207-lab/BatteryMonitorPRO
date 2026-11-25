package com.batterymonitorpro

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
            BatteryWidgetWorker.WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(BatteryWidgetWorker.WORK_NAME)
        super.onDisabled(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED -> refresh(context)
        }
    }

    companion object {
        fun refresh(context: Context) {
            val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = sticky?.getIntExtra("level", -1) ?: -1
            val scale = sticky?.getIntExtra("scale", 100) ?: 100
            val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
            val statusCode = sticky?.getIntExtra("status", -1) ?: -1
            val status = when (statusCode) {
                2 -> "Заряжается"
                5 -> "Заряжена"
                3 -> "Разряжается"
                4 -> "Не заряжается"
                else -> "Состояние неизвестно"
            }
            val tempTenth = sticky?.getIntExtra("temperature", -1) ?: -1
            val voltageMv = sticky?.getIntExtra("voltage", -1) ?: -1
            val data = JSONObject().apply {
                put("level", percent)
                put("statusText", status)
                put("temperatureC", if (tempTenth >= 0) tempTenth / 10.0 else JSONObject.NULL)
                put("voltageV", if (voltageMv >= 0) voltageMv / 1000.0 else JSONObject.NULL)
            }
            updateAll(context, data.toString())
        }

        fun updateAll(context: Context, json: String) {
            val manager = AppWidgetManager.getInstance(context)
            val data = try { JSONObject(json) } catch (_: Exception) { JSONObject() }
            val level = data.optInt("level", -1)
            val status = when (data.optString("status")) {
                "charging" -> "Заряжается"
                "discharging" -> "Разряжается"
                "full" -> "Заряжена"
                "not_charging" -> "Не заряжается"
                else -> data.optString("statusText", "Состояние неизвестно")
            }
            val tempText = if (data.has("temperatureC") && !data.isNull("temperatureC"))
                "${data.optDouble("temperatureC").toInt()}°C" else "—°C"
            val voltageText = if (data.has("voltageV") && !data.isNull("voltageV"))
                String.format(java.util.Locale.US, "%.2f V", data.optDouble("voltageV")) else "— V"
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pending = launch?.let {
                PendingIntent.getActivity(context, 0, it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }

            val bigIds = manager.getAppWidgetIds(ComponentName(context, BatteryWidgetProvider::class.java))
            bigIds.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.battery_widget)
                views.setTextViewText(R.id.widget_percent, if (level >= 0) "$level%" else "—")
                views.setTextViewText(R.id.widget_status, status)
                views.setTextViewText(R.id.widget_temperature, "Температура: $tempText")
                views.setTextViewText(R.id.widget_voltage, "Напряжение: $voltageText")
                if (pending != null) views.setOnClickPendingIntent(R.id.widget_percent, pending)
                manager.updateAppWidget(id, views)
            }

            val narrowIds = manager.getAppWidgetIds(ComponentName(context, NarrowBatteryWidgetProvider::class.java))
            narrowIds.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.battery_widget_narrow)
                views.setTextViewText(R.id.widget_narrow_percent, if (level >= 0) "$level%" else "—")
                views.setTextViewText(R.id.widget_narrow_status, "$status • $tempText")
                if (pending != null) views.setOnClickPendingIntent(R.id.widget_narrow_percent, pending)
                manager.updateAppWidget(id, views)
            }
        }
    }
}