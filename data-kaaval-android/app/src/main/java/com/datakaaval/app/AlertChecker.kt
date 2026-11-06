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

    /** Returns true while at least one limit is still waiting to be reached (so the watcher should poll fast). */
    @Synchronized
    fun check(ctx: Context): Boolean {
        var pending = false
        val raw = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE).getString("json", null) ?: return false
        if (!UsageReader.hasAccess(ctx)) return false
        val cfg = JSONObject(raw)
        val plan = cfg.optDouble("plan", 0.0)
        val limits = cfg.optJSONObject("limits") ?: JSONObject()
        if (plan <= 0 && limits.length() == 0) return false

        val warn = cfg.optDouble("warn", 80.0) / 100.0
        val popup = cfg.optBoolean("popup", true)
        val notifyOn = cfg.optBoolean("notify", false)
        val wifi = cfg.optBoolean("wifi", true)
        val ta = cfg.optString("lang", "en") == "ta"
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
            val pct = Math.round(used / lim * 100).toInt()
            if (used < lim) {
                pending = true
                if (popup) PetOverlay.prewarm(ctx)
            }
            if (used >= lim) {
                once("w$tag")
                if (once("o$tag")) {
                    val title = if (ta) "$name — வரம்பு எட்டியது" else "$name limit reached"
                    val body = if (ta) "இன்றைய வரம்பை எட்டிவிட்டீர்கள்" else "You reached today's limit"
                    alert(ctx, title, body, "${fmt(used)} / ${fmt(lim)}", pct, true, popup, notifyOn, tag.hashCode(), ta, name)
                }
            } else if (used >= lim * warn) {
                if (once("w$tag")) {
                    val body = if (ta) "$pct% பயன்படுத்தப்பட்டது" else "$pct% of today's limit used"
                    alert(ctx, name, body, "${fmt(used)} / ${fmt(lim)}", pct, false, popup, notifyOn, tag.hashCode(), ta)
                }
            }
        }

        if (plan > 0) {
            val total = today.values.sum()
            val tag = "plan@${plan.toInt()}"
            val pct = Math.round(total / plan * 100).toInt()
            if (total < plan) pending = true
            if (total >= plan) {
                once("w$tag")
                if (once("o$tag")) {
                    val title = if (ta) "தினசரி வரம்பு எட்டியது" else "Daily data limit reached"
                    val body = if (ta) "இன்றைய டேட்டா திட்டம் முழுவதும் தீர்ந்தது" else "You used your whole daily data plan"
                    alert(ctx, title, body, "${fmt(total)} / ${fmt(plan)}", pct, true, popup, notifyOn, tag.hashCode(), ta, if (ta) "தினசரி டேட்டா" else "Daily data")
                }
            } else if (total >= plan * warn) {
                if (once("w$tag")) {
                    val title = if (ta) "வரம்பு நெருங்குகிறது" else "Almost at your limit"
                    val body = if (ta) "${fmt(plan - total)} மீதம்" else "${fmt(plan - total)} left today"
                    alert(ctx, title, body, "${fmt(total)} / ${fmt(plan)}", pct, false, popup, notifyOn, tag.hashCode(), ta)
                }
            }
        }
        return pending
    }

    /** Used by the "Test popup" button so the person can check the popup works over other apps. */
    fun test(ctx: Context) {
        val ta = try {
            JSONObject(ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE).getString("json", "{}") ?: "{}")
                .optString("lang", "en") == "ta"
        } catch (e: Exception) { false }
        alert(
            ctx,
            if (ta) "YouTube — வரம்பு எட்டியது" else "YouTube limit reached",
            if (ta) "இன்றைய வரம்பை எட்டிவிட்டீர்கள்" else "You reached today's limit",
            "12 MB / 10 MB", 120, true, true, false, 4242, ta, "YouTube"
        )
    }

    private fun alert(
        ctx: Context, title: String, body: String, nums: String, pct: Int,
        over: Boolean, popup: Boolean, notifyOn: Boolean, id: Int, ta: Boolean, name: String = title
    ) {
        if (OverlayManager.canDraw(ctx)) {
            if (over && popup) {
                val cfg = try {
                    JSONObject(ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE).getString("json", "{}") ?: "{}")
                } catch (e: Exception) { JSONObject() }
                val style = cfg.optString("style", "pet")
                val sound = cfg.optBoolean("petSound", true)
                val quick = cfg.optString("intro", "quick") == "quick"
                when (style) {
                    "full" -> OverlayManager.showLimit(ctx, title, body, nums, pct, ta)
                    "both" -> PetOverlay.show(ctx, name, nums, pct, ta, sound, quick,
                        { OverlayManager.showLimit(ctx, title, body, nums, pct, ta) },
                        { OverlayManager.showLimit(ctx, title, body, nums, pct, ta) })
                    else -> PetOverlay.show(ctx, name, nums, pct, ta, sound, quick,
                        { OverlayManager.showLimit(ctx, title, body, nums, pct, ta) },
                        { })
                }
            } else OverlayManager.showBanner(ctx, title, "$body · $nums")
            if (notifyOn) notifyPlain(ctx, title, body, nums, id)
            return
        }

        val full = Intent(ctx, AlertActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("title", title).putExtra("body", body)
            .putExtra("nums", nums).putExtra("over", over)
            .putExtra("pct", pct.toFloat()).putExtra("ta", ta)

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

        val wantsFull = over && popup
        if (!wantsFull && !notifyOn) return
        post(ctx, title, body, nums, id, if (wantsFull) full else null)
    }

    private fun notifyPlain(ctx: Context, title: String, body: String, nums: String, id: Int) {
        post(ctx, title, body, nums, id, null)
    }

    private fun post(ctx: Context, title: String, body: String, nums: String, id: Int, full: Intent?) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.areNotificationsEnabled()) return
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
        if (full != null) {
            b.setFullScreenIntent(PendingIntent.getActivity(ctx, id + 1, full, flags), true)
        }
        nm.notify(id, b.build())
    }
}
