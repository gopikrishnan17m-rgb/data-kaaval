package com.datakaaval.app

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import java.util.LinkedList

object OverlayPopup {

    private val main = Handler(Looper.getMainLooper())
    private var current: View? = null
    private val queue = LinkedList<Pop>()

    fun canShow(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun show(ctx: Context, p: Pop) {
        val app = ctx.applicationContext
        main.post {
            if (current != null) {
                queue.add(p)
            } else {
                display(app, p)
            }
        }
    }

    private fun display(ctx: Context, p: Pop) {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        var view: View? = null
        val close = {
            try {
                view?.let { wm.removeView(it) }
            } catch (e: Exception) {
            }
            current = null
            val next = queue.poll()
            if (next != null) display(ctx, next)
        }
        val v = PopupUi.build(ctx, p, close) {
            close()
            ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        view = v
        v.isFocusableInTouchMode = true
        v.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                close()
                true
            } else {
                false
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        try {
            wm.addView(v, lp)
            current = v
            v.requestFocus()
            buzz(ctx)
        } catch (e: Exception) {
            current = null
            val i = Intent(ctx, AlertActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("title", p.title).putExtra("body", p.body).putExtra("nums", p.nums)
                .putExtra("over", p.over).putExtra("pct", p.pct).putExtra("ta", p.ta)
            try {
                ctx.startActivity(i)
            } catch (e2: Exception) {
            }
        }
    }

    private fun buzz(ctx: Context) {
        try {
            val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 220, 110, 220), -1))
        } catch (e: Exception) {
        }
    }
}
