package com.datakaaval.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Entry point used by AlertChecker. The full-screen popup itself lives in OverlayPopup / PopupUi. */
object OverlayManager {

    private val main = Handler(Looper.getMainLooper())

    fun canDraw(ctx: Context): Boolean = OverlayPopup.canShow(ctx)

    fun showLimit(ctx: Context, title: String, body: String, nums: String, pct: Int, ta: Boolean) {
        OverlayPopup.show(ctx, Pop(title, body, nums, true, pct.toFloat(), ta))
    }

    /** Small animated banner at the top of the screen, used for "close to limit" warnings. */
    fun showBanner(ctx: Context, title: String, body: String) {
        val app = ctx.applicationContext
        main.post {
            try {
                val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val d = app.resources.displayMetrics.density
                val card = LinearLayout(app).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding((18 * d).toInt(), (14 * d).toInt(), (18 * d).toInt(), (14 * d).toInt())
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#FBBF24"))
                        cornerRadius = 18 * d
                    }
                    elevation = 12 * d
                }
                card.addView(TextView(app).apply {
                    text = title; textSize = 16f; setTextColor(Color.parseColor("#1A1200")); typeface = Typeface.DEFAULT_BOLD
                })
                card.addView(TextView(app).apply {
                    text = body; textSize = 14f; setTextColor(Color.parseColor("#3A2C00"))
                })
                val lp = WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                )
                lp.gravity = Gravity.TOP
                lp.y = (36 * d).toInt()
                val wrap = FrameLayout(app).apply {
                    setPadding((14 * d).toInt(), 0, (14 * d).toInt(), 0)
                    addView(card)
                }
                wm.addView(wrap, lp)
                wrap.translationY = -160 * d
                wrap.animate().translationY(0f).setDuration(380).setInterpolator(DecelerateInterpolator()).start()
                main.postDelayed({
                    wrap.animate().translationY(-160 * d).alpha(0f).setDuration(300)
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(a: Animator) {
                                try { wm.removeViewImmediate(wrap) } catch (e: Exception) { }
                            }
                        }).start()
                }, 4800)
            } catch (e: Exception) {
                // overlay permission missing; nothing to show
            }
        }
    }
}
