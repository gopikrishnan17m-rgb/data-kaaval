package com.datakaaval.app

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.webkit.JavascriptInterface
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
