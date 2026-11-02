package com.datakaaval.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AlertChecker {

    private const val CHANNEL = "kaaval_alerts"

    private fun fmt(mb: Double): String =
        if (mb >= 1024) String.format(Locale.US, "%.2f GB", mb / 1024) else "${Math.round(mb)} MB"

    fun check(ctx: Context) {
        val raw = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE).getString("json", null) ?: return
        if (!UsageReader.hasAccess(ctx)) return
        val cfg = JSONObject(raw)
        val plan = cfg.optDouble("plan", 0.0)
        val warn = cfg.optDouble("warn", 80.0) / 100.0
        val popup = cfg.optBoolean("popup", true)
        val notifyOn = cfg.optBoolean("notify", false)
        val wifi = cfg.optBoolean("wifi", true)
        val ta = cfg.optString("lang", "en") == "ta"
        val limits = cfg.optJSONObject("limits") ?: JSONObject()
        val names = cfg.optJSONObject("names") ?: JSONObject()

        val today = UsageReader.today(ctx, wifi)
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val fired = ctx.getSharedPreferences("fired", Context.MODE_PRIVATE)
        if (fired.getString("date", "") != date) fired.edit().clear().putString("date", date).apply()

        fun once(key: String): Boolean {
            if (fired.getBoolean(key, false)) return false
            fired.edit().putBoolean(key, true).apply()
            return true
        }

        val it = limits.keys()
        while (it.hasNext()) {
            val id = it.next()
            val lim = limits.optDouble(id, 0.0)
            if (lim <= 0) continue
            val used = today[id] ?: 0.0
            val name = if (id == "hs") (if (ta) "மொபைல் ஹாட்ஸ்பாட்" else "Mobile hotspot") else names.optString(id, id)
            val tag = "${id}@${lim.toInt()}"
            if (used >= lim) {
                once("w$tag")
                if (once("o$tag")) {
                    val title = if (ta) "$name — வரம்பு எட்டியது" else "$name limit reached"
                    val body = if (ta) "இன்றைய வரம்பை எட்டிவிட்டீர்கள்" else "You reached today's limit"
                    alert(ctx, title, body, "${fmt(used)} / ${fmt(lim)}", true, popup, notifyOn, tag.hashCode())
                }
            } else if (used >= lim * warn) {
                if (once("w$tag")) {
                    val title = name
                    val body = if (ta) "${Math.round(used / lim * 100)}% பயன்படுத்தப்பட்டது"
                    else "${Math.round(used / lim * 100)}% of today's limit used"
                    alert(ctx, title, body, "${fmt(used)} / ${fmt(lim)}", false, popup, notifyOn, tag.hashCode())
                }
            }
        }

        if (plan > 0) {
            val total = today.values.sum()
            val tag = "plan@${plan.toInt()}"
            if (total >= plan) {
                once("w$tag")
                if (once("o$tag")) {
                    val title = if (ta) "வரம்பு எட்டியது" else "Daily data limit reached"
                    val body = if (ta) "இன்றைய டேட்டா திட்டம் முழுவதும் தீர்ந்தது" else "You used your whole daily data plan"
                    alert(ctx, title, body, "${fmt(total)} / ${fmt(plan)}", true, popup, notifyOn, tag.hashCode())
                }
            } else if (total >= plan * warn) {
                if (once("w$tag")) {
                    val title = if (ta) "வரம்பு நெருங்குகிறது" else "Almost at your limit"
                    val body = if (ta) "${fmt(plan - total)} மீதம்" else "${fmt(plan - total)} left today"
                    alert(ctx, title, body, "${fmt(total)} / ${fmt(plan)}", false, popup, notifyOn, tag.hashCode())
                }
            }
        }
    }

    private fun alert(
        ctx: Context, title: String, body: String, nums: String,
        over: Boolean, popup: Boolean, notifyOn: Boolean, id: Int
    ) {
        val full = Intent(ctx, AlertActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("title", title).putExtra("body", body)
            .putExtra("nums", nums).putExtra("over", over)

        if (MainActivity.visible) {
            if (over && popup) {
                ctx.startActivity(full)
            } else {
                val web = MainActivity.web
                web?.post {
                    web.evaluateJavascript(
                        "window.onNativeWarn&&window.onNativeWarn(" + JSONObject.quote("$title — $body") + "," + over + ")", null
                    )
                }
            }
            return
        }

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.areNotificationsEnabled()) return
        val wantsFull = over && popup
        if (!wantsFull && !notifyOn) return

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Data alerts", NotificationManager.IMPORTANCE_HIGH)
        )
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val openApp = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java), flags)
        val b = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText("$body · $nums")
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(openApp)
        if (wantsFull) {
            b.setFullScreenIntent(PendingIntent.getActivity(ctx, id + 1, full, flags), true)
        }
        nm.notify(id, b.build())
    }
}
