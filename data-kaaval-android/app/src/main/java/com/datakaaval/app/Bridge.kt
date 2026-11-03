package com.datakaaval.app

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowInsetsController
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

class Bridge(private val activity: Activity) {

    @JavascriptInterface
    fun hasUsageAccess(): Boolean = UsageReader.hasAccess(activity)

    @JavascriptInterface
    fun openUsageSettings() {
        activity.runOnUiThread {
            val i = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(i)
        }
    }

    @JavascriptInterface
    fun hasOverlay(): Boolean = Settings.canDrawOverlays(activity)

    @JavascriptInterface
    fun openOverlaySettings() {
        activity.runOnUiThread {
            val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + activity.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(i)
        }
    }

    @JavascriptInterface
    fun hasBatteryExempt(): Boolean {
        val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(activity.packageName)
    }

    @JavascriptInterface
    fun openBatterySettings() {
        activity.runOnUiThread {
            try {
                activity.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + activity.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                activity.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    @JavascriptInterface
    fun usedToday(id: String, includeWifi: Boolean): Double {
        return try { UsageReader.today(activity, includeWifi)[id] ?: 0.0 } catch (e: Exception) { -1.0 }
    }

    @JavascriptInterface
    fun hasNotifications(): Boolean {
        val nm = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.areNotificationsEnabled()
    }

    @JavascriptInterface
    fun requestNotifications() {
        activity.runOnUiThread {
            if (Build.VERSION.SDK_INT >= 33) {
                activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }

    @Suppress("DEPRECATION")
    @JavascriptInterface
    fun setBars(top: String, bottom: String, lightBg: Boolean) {
        activity.runOnUiThread {
            try {
                val w = activity.window
                w.statusBarColor = Color.parseColor(top)
                w.navigationBarColor = Color.parseColor(bottom)
                if (Build.VERSION.SDK_INT >= 30) {
                    val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    w.insetsController?.setSystemBarsAppearance(if (lightBg) mask else 0, mask)
                } else {
                    w.decorView.systemUiVisibility = if (lightBg)
                        (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR) else 0
                }
            } catch (e: Exception) {
                // bad colour string; keep the current bars
            }
        }
    }

    @JavascriptInterface
    fun requestIcons(idsJson: String) {
        thread {
            try {
                val arr = JSONArray(idsJson)
                val ids = ArrayList<String>()
                for (i in 0 until arr.length()) ids.add(arr.getString(i))
                for (chunk in ids.chunked(15)) {
                    val out = IconReader.read(activity, chunk)
                    val web = MainActivity.web
                    web?.post {
                        web.evaluateJavascript("window.onNativeIcons(" + JSONObject.quote(out) + ")", null)
                    }
                }
            } catch (e: Exception) {
                // icons are optional
            }
        }
    }

    @JavascriptInterface
    fun setConfig(json: String) {
        activity.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().putString("json", json).apply()
    }

    @JavascriptInterface
    fun requestUsage(includeWifi: Boolean) {
        thread {
            try {
                val out = UsageReader.readDays(activity, includeWifi)
                AlertChecker.check(activity.applicationContext)
                val web = MainActivity.web
                web?.post {
                    web.evaluateJavascript("window.onNativeUsage(" + JSONObject.quote(out) + ")", null)
                }
            } catch (e: Exception) {
                // ignore: next sync will retry
            }
        }
    }
}
