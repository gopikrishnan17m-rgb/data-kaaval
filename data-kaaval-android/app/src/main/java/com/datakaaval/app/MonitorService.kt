package com.datakaaval.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager

class MonitorService : Service() {

    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private val tick = object : Runnable {
        override fun run() {
            try {
                AlertChecker.check(applicationContext)
            } catch (e: Exception) {
                // keep running
            }
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            handler?.postDelayed(this, if (pm.isInteractive) 8000L else 60000L)
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
