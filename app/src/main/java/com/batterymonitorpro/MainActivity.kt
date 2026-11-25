package com.batterymonitorpro

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var webView: WebView

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val snapshot = readBatterySnapshot(intent)
            if (::webView.isInitialized) {
                webView.evaluateJavascript(
                    "window.onNativeBatteryUpdate && window.onNativeBatteryUpdate($snapshot);",
                    null
                )
            }
            BatteryWidgetProvider.updateAll(this@MainActivity, snapshot)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = false
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return !url.startsWith("file:///android_asset/")
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(BatteryBridge(), "BatteryNative")
        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        val snapshot = readBatterySnapshot(null)
        webView.post {
            webView.evaluateJavascript(
                "window.onNativeBatteryUpdate && window.onNativeBatteryUpdate($snapshot);",
                null
            )
        }
    }

    override fun onStop() {
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: IllegalArgumentException) {
        }
        super.onStop()
    }

    private inner class BatteryBridge {
        @JavascriptInterface
        fun getBatteryInfo(): String = readBatterySnapshot(null)
    }

    private fun readBatterySnapshot(intent: Intent?): String {
        val batteryIntent = intent ?: registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (level >= 0 && scale > 0) (level * 100f / scale).toInt() else -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val tempTenth = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val health = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1

        val currentUa = try { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) } catch (_: Exception) { Int.MIN_VALUE }
        val chargeUah = try { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) } catch (_: Exception) { Int.MIN_VALUE }

        val statusText = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
            else -> "unknown"
        }
        val healthText = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Хорошее"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Перегрев"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Неисправна"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Перенапряжение"
            BatteryManager.BATTERY_HEALTH_COLD -> "Переохлаждение"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Ошибка"
            else -> "Неизвестно"
        }
        val pluggedText = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "Сеть"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Беспроводная"
            else -> if (plugged == 0) "Не подключено" else "Подключено"
        }

        return JSONObject().apply {
            put("level", if (percent >= 0) percent else JSONObject.NULL)
            put("status", statusText)
            put("plugged", pluggedText)
            put("temperatureC", if (tempTenth >= 0) tempTenth / 10.0 else JSONObject.NULL)
            put("voltageV", if (voltageMv >= 0) voltageMv / 1000.0 else JSONObject.NULL)
            put("currentmA", if (currentUa != Int.MIN_VALUE) currentUa / 1000.0 else JSONObject.NULL)
            put("chargeCountermAh", if (chargeUah != Int.MIN_VALUE) chargeUah / 1000.0 else JSONObject.NULL)
            put("health", healthText)
            put("timestamp", System.currentTimeMillis())
        }.toString()
    }
}
