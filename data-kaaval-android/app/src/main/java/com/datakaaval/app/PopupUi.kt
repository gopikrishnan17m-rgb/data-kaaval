package com.datakaaval.app

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

data class Pop(
    val title: String,
    val body: String,
    val nums: String,
    val over: Boolean,
    val pct: Float,
    val ta: Boolean
)

object PopupUi {

    fun build(ctx: Context, p: Pop, onOk: () -> Unit, onOpen: () -> Unit): View {
        val density = ctx.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()
        val accent = if (p.over) Color.parseColor("#FF5D6C") else Color.parseColor("#FBBF24")
        val accentSoft = Color.argb(45, Color.red(accent), Color.green(accent), Color.blue(accent))
        val animators = ArrayList<Animator>()

        val root = FrameLayout(ctx)
        root.setBackgroundColor(Color.parseColor("#F00A0F1C"))
        root.alpha = 0f
        root.isClickable = true

        val column = LinearLayout(ctx)
        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER
        column.setPadding(dp(28), dp(28), dp(28), dp(28))
        root.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val stack = FrameLayout(ctx)
        column.addView(stack, LinearLayout.LayoutParams(dp(230), dp(230)))

        fun ringView(): View {
            val v = View(ctx)
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            g.setColor(Color.TRANSPARENT)
            g.setStroke(dp(4), accent)
            v.background = g
            return v
        }
        val rings = listOf(ringView(), ringView())
        for (r in rings) {
            r.alpha = 0f
            stack.addView(r, FrameLayout.LayoutParams(dp(120), dp(120), Gravity.CENTER))
        }

        val badge = TextView(ctx)
        badge.text = "!"
        badge.textSize = 54f
        badge.setTextColor(accent)
        badge.typeface = Typeface.DEFAULT_BOLD
        badge.gravity = Gravity.CENTER
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(accentSoft)
        bg.setStroke(dp(8), accent)
        badge.background = bg
        badge.scaleX = 0f
        badge.scaleY = 0f
        stack.addView(badge, FrameLayout.LayoutParams(dp(120), dp(120), Gravity.CENTER))

        val pctView = TextView(ctx)
        pctView.text = "0%"
        pctView.textSize = 46f
        pctView.setTextColor(accent)
        pctView.typeface = Typeface.DEFAULT_BOLD
        pctView.gravity = Gravity.CENTER

        val title = TextView(ctx)
        title.text = p.title
        title.textSize = 26f
        title.setTextColor(Color.WHITE)
        title.typeface = Typeface.DEFAULT_BOLD
        title.gravity = Gravity.CENTER

        val body = TextView(ctx)
        body.text = p.body
        body.textSize = 16f
        body.setTextColor(Color.parseColor("#B5C0DA"))
        body.gravity = Gravity.CENTER
        body.setPadding(0, dp(8), 0, dp(8))

        val pb = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal)
        pb.max = 1000
        pb.progress = 0
        pb.progressTintList = ColorStateList.valueOf(accent)
        pb.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#27334D"))

        val nums = TextView(ctx)
        nums.text = p.nums
        nums.textSize = 20f
        nums.setTextColor(accent)
        nums.typeface = Typeface.DEFAULT_BOLD
        nums.gravity = Gravity.CENTER
        nums.setPadding(0, dp(10), 0, dp(18))

        fun pill(text: String, fill: Int, fg: Int, click: () -> Unit): TextView {
            val t = TextView(ctx)
            t.text = text
            t.textSize = 17f
            t.setTextColor(fg)
            t.typeface = Typeface.DEFAULT_BOLD
            t.gravity = Gravity.CENTER
            val g = GradientDrawable()
            g.cornerRadius = dp(16).toFloat()
            g.setColor(fill)
            t.background = g
            t.isClickable = true
            t.setOnClickListener { click() }
            return t
        }
        val okBtn = pill(if (p.ta) "சரி" else "OK", accent, Color.parseColor("#0A0F1C")) { onOk() }
        val openBtn = pill(if (p.ta) "ஆப்பைத் திற" else "Open Data Kaaval", Color.parseColor("#1B2539"), Color.WHITE) { onOpen() }

        column.addView(pctView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(pb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)).apply { topMargin = dp(8) })
        column.addView(nums, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(okBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(6) })
        column.addView(openBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(10) })

        val staged = listOf<View>(pctView, title, body, pb, nums, okBtn, openBtn)
        for (v in staged) {
            v.alpha = 0f
            v.translationY = dp(26).toFloat()
        }

        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                for (a in animators) a.cancel()
            }
        })

        root.post {
            root.animate().alpha(1f).setDuration(220).start()

            badge.animate().scaleX(1f).scaleY(1f).setDuration(560)
                .setInterpolator(OvershootInterpolator(2.4f))
                .withEndAction {
                    val pulse = ObjectAnimator.ofPropertyValuesHolder(
                        badge,
                        PropertyValuesHolder.ofFloat("scaleX", 1f, 1.08f),
                        PropertyValuesHolder.ofFloat("scaleY", 1f, 1.08f)
                    )
                    pulse.duration = 800
                    pulse.repeatCount = ValueAnimator.INFINITE
                    pulse.repeatMode = ValueAnimator.REVERSE
                    animators.add(pulse)
                    pulse.start()
                }.start()

            rings.forEachIndexed { i, r ->
                val a = ObjectAnimator.ofPropertyValuesHolder(
                    r,
                    PropertyValuesHolder.ofFloat("scaleX", 1f, 2.0f),
                    PropertyValuesHolder.ofFloat("scaleY", 1f, 2.0f),
                    PropertyValuesHolder.ofFloat("alpha", 0.7f, 0f)
                )
                a.duration = 1800
                a.startDelay = 400L + i * 900L
                a.repeatCount = ValueAnimator.INFINITE
                a.interpolator = DecelerateInterpolator()
                animators.add(a)
                a.start()
            }

            var delay = 180L
            for (v in staged) {
                v.animate().alpha(1f).translationY(0f)
                    .setStartDelay(delay).setDuration(380)
                    .setInterpolator(DecelerateInterpolator()).start()
                delay += 90L
            }

            val shown = if (p.pct > 0f) p.pct else 100f
            val counter = ValueAnimator.ofFloat(0f, shown)
            counter.duration = 1200
            counter.startDelay = 350
            counter.addUpdateListener { pctView.text = "${Math.round(it.animatedValue as Float)}%" }
            animators.add(counter)
            counter.start()

            val bar = ObjectAnimator.ofInt(pb, "progress", 0, (Math.min(shown, 100f) * 10).toInt())
            bar.duration = 1200
            bar.startDelay = 350
            animators.add(bar)
            bar.start()
        }
        return root
    }
}
