package com.datakaaval.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import org.json.JSONObject

class MonitorService : Service() {

    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private var fgPkg: String? = null
    private var lastEv = System.currentTimeMillis() - 30_000L

    private fun updateForeground() {
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val ev = usm.queryEvents(lastEv, now)
            val e = UsageEvents.Event()
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) fgPkg = e.packageName
            }
            lastEv = now - 1500L
        } catch (e: Exception) {
            // keep the last known app
        }
    }

    @Volatile private var pending = true

    /** Poll fast while a limit is still waiting to be reached, so the popup comes the moment it is crossed. */
    private fun nextDelay(interactive: Boolean): Long {
        try {
            val raw = getSharedPreferences("cfg", Context.MODE_PRIVATE).getString("json", null) ?: return 20000L
            val limits = JSONObject(raw).optJSONObject("limits") ?: JSONObject()
            val hasHs = limits.optDouble("hs", 0.0) > 0
            if (interactive) return if (pending) 1500L else 15000L
            return if (hasHs && pending) 5000L else if (pending) 20000L else 60000L
        } catch (e: Exception) {
            return 10000L
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val interactive = pm.isInteractive
            var err = ""
            try {
                if (interactive) updateForeground()
                pending = AlertChecker.check(applicationContext)
            } catch (e: Exception) {
                err = e.javaClass.simpleName + ": " + (e.message ?: "")
            }
            getSharedPreferences("diag", Context.MODE_PRIVATE).edit()
                .putLong("hb", System.currentTimeMillis())
                .putString("fg", fgPkg ?: "")
                .putString("err", err)
                .apply()
            handler?.postDelayed(this, nextDelay(interactive))
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("kaaval_monitor", "Data limit watch", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, "kaaval_monitor")
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Data Kaaval")
            .setContentText("Watching your data limits")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
        PetOverlay.prewarm(this)
        thread = HandlerThread("kaaval-monitor").also { it.start() }
        handler = Handler(thread!!.looper)
        handler?.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(ctx: Context) {
            try {
                ctx.startForegroundService(Intent(ctx, MonitorService::class.java))
            } catch (e: Exception) {
                // not allowed from here; it will start when the app is opened
            }
        }
    }
}
