package com.datakaaval.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.LinkedList

/**
 * The cat alert. The moment a limit is reached the cat peeks in from the left edge twice, then waddles in,
 * says which app hit its limit (meow + voice) and waits in a small card. Touches go through to the app
 * underneath everywhere except on the card itself.
 */
object PetOverlay {

    private class Req(
        val name: String, val nums: String, val pct: Int,
        val ta: Boolean, val sound: Boolean, val onClosed: () -> Unit
    )

    private val main = Handler(Looper.getMainLooper())
    private val queue = LinkedList<Req>()
    private var busy = false
    private var bitmap: Bitmap? = null

    private const val PEEK1_AT = 0f
    private const val PEEK2_AT = 920f
    private const val WALK_AT = 1740f
    private const val WALK_MS = 1100f
    private const val TOTAL_MS = 2840L
    private const val STAY_MS = 15000L

    fun prewarm(ctx: Context) {
        val app = ctx.applicationContext
        main.post {
            bitmap(app)
            PetVoice.prewarm(app)
        }
    }

    private fun bitmap(ctx: Context): Bitmap? {
        bitmap?.let { return it }
        return try {
            val o = BitmapFactory.Options()
            o.inScaled = false
            BitmapFactory.decodeResource(ctx.resources, R.drawable.pet, o).also { bitmap = it }
        } catch (e: Exception) {
            null
        }
    }

    fun show(ctx: Context, name: String, nums: String, pct: Int, ta: Boolean, sound: Boolean, onClosed: () -> Unit) {
        val app = ctx.applicationContext
        main.post {
            val r = Req(name, nums, pct, ta, sound, onClosed)
            if (busy) queue.add(r) else display(app, r)
        }
    }

    private fun lerp(a: Float, b: Float, p: Float): Float = a + (b - a) * p
    private fun clamp01(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v
    private fun easeOut(p: Float): Float = 1f - (1f - p) * (1f - p)
    private fun easeIn(p: Float): Float = p * p

    /** 0 -> 1 while the cat leans in, stays 1 while it looks, then 1 -> 0 as it ducks back. */
    private fun peek(t: Float, start: Float, inMs: Float, holdMs: Float, outMs: Float): Float {
        val x = t - start
        return when {
            x <= 0f -> 0f
            x < inMs -> easeOut(x / inMs)
            x < inMs + holdMs -> 1f
            x < inMs + holdMs + outMs -> 1f - easeIn((x - inMs - holdMs) / outMs)
            else -> 0f
        }
    }

    private fun display(ctx: Context, r: Req) {
        val bmp = bitmap(ctx)
        if (bmp == null) {
            r.onClosed()
            next(ctx)
            return
        }
        busy = true
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = ctx.resources.displayMetrics
        val d = dm.density
        fun dp(v: Int): Int = (v * d).toInt()

        val petW = dp(190)
        val petH = (petW * bmp.height / bmp.width.toFloat()).toInt()
        val bottomGap = dp(48)
        val out = -(petW + dp(30)).toFloat()
        val peek1 = -petW * 0.55f
        val peek2 = -petW * 0.30f

        // Stage window: only draws the cat, never takes touches.
        val stage = FrameLayout(ctx)
        val pet = ImageView(ctx)
        pet.setImageBitmap(bmp)
        pet.scaleType = ImageView.ScaleType.FIT_XY
        val plp = FrameLayout.LayoutParams(petW, petH, Gravity.BOTTOM or Gravity.START)
        plp.leftMargin = dp(8)
        plp.bottomMargin = bottomGap
        stage.addView(pet, plp)
        pet.pivotX = petW / 2f
        pet.pivotY = petH.toFloat()
        pet.translationX = out

        val stageLp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        try {
            wm.addView(stage, stageLp)
        } catch (e: Exception) {
            busy = false
            r.onClosed()
            next(ctx)
            return
        }

        var card: View? = null
        var closed = false
        var idle: ValueAnimator? = null
        var timeline: ValueAnimator? = null
        var autoClose: Runnable? = null

        fun finishAll() {
            try { wm.removeView(stage) } catch (e: Exception) { }
            busy = false
            r.onClosed()
            next(ctx)
        }

        fun dismiss() {
            if (closed) return
            closed = true
            autoClose?.let { main.removeCallbacks(it) }
            idle?.cancel()
            timeline?.cancel()
            PetVoice.stop()
            card?.let { c ->
                c.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(180).withEndAction {
                    try { wm.removeView(c) } catch (e: Exception) { }
                }.start()
            }
            val from = pet.translationX
            val bye = ValueAnimator.ofFloat(0f, 1f)
            bye.duration = 420
            bye.addUpdateListener {
                val p = easeIn(it.animatedValue as Float)
                pet.translationX = lerp(from, out, p)
                pet.rotation = lerp(pet.rotation, -6f, p)
            }
            bye.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { finishAll() }
            })
            bye.start()
        }

        fun buildCard(): View {
            val box = LinearLayout(ctx)
            box.orientation = LinearLayout.VERTICAL
            box.setPadding(dp(18), dp(16), dp(18), dp(14))
            val bg = GradientDrawable()
            bg.cornerRadius = dp(22).toFloat()
            bg.setColor(Color.parseColor("#F2101A2E"))
            bg.setStroke(dp(2), Color.parseColor("#FF5D6C"))
            box.background = bg
            box.elevation = dp(10).toFloat()

            val title = TextView(ctx)
            title.text = if (r.ta) "${r.name} வரம்பு எட்டியது!" else "${r.name} limit reached!"
            title.textSize = 19f
            title.setTextColor(Color.WHITE)
            title.typeface = Typeface.DEFAULT_BOLD
            box.addView(title)

            val nums = TextView(ctx)
            nums.text = r.nums + "  ·  " + r.pct + "%"
            nums.textSize = 16f
            nums.setTextColor(Color.parseColor("#FF8A95"))
            nums.typeface = Typeface.DEFAULT_BOLD
            nums.setPadding(0, dp(4), 0, dp(12))
            box.addView(nums)

            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            fun btn(text: String, fill: Int, fg: Int, click: () -> Unit): TextView {
                val t = TextView(ctx)
                t.text = text
                t.textSize = 15f
                t.setTextColor(fg)
                t.typeface = Typeface.DEFAULT_BOLD
                t.gravity = Gravity.CENTER
                val g = GradientDrawable()
                g.cornerRadius = dp(14).toFloat()
                g.setColor(fill)
                t.background = g
                t.setOnClickListener { click() }
                return t
            }
            val ok = btn(if (r.ta) "சரி" else "OK", Color.parseColor("#FF5D6C"), Color.parseColor("#0A0F1C")) { dismiss() }
            val open = btn(if (r.ta) "ஆப்பைத் திற" else "Open app", Color.parseColor("#27334D"), Color.WHITE) {
                dismiss()
                try {
                    ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                }
            }
            row.addView(ok, LinearLayout.LayoutParams(0, dp(44), 1f))
            row.addView(open, LinearLayout.LayoutParams(0, dp(44), 1.4f).apply { leftMargin = dp(10) })
            box.addView(row)
            return box
        }

        fun arrive() {
            if (closed) return
            // little landing squash
            val squash = ValueAnimator.ofFloat(0f, 1f)
            squash.duration = 380
            squash.addUpdateListener {
                val p = it.animatedValue as Float
                val k = Math.sin(p * Math.PI).toFloat()
                pet.scaleY = 1f - 0.07f * k
                pet.scaleX = 1f + 0.05f * k
            }
            squash.start()

            val cardW = Math.min(dm.widthPixels - dp(24), dp(360))
            val c = buildCard()
            card = c
            val lp = WindowManager.LayoutParams(
                cardW, ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            lp.y = bottomGap + petH + dp(4)
            try {
                wm.addView(c, lp)
                c.alpha = 0f
                c.scaleX = 0.6f
                c.scaleY = 0.6f
                c.pivotX = dp(40).toFloat()
                c.pivotY = dp(120).toFloat()
                c.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320)
                    .setInterpolator(OvershootInterpolator(1.6f)).start()
            } catch (e: Exception) {
                card = null
            }

            if (r.sound) {
                PetVoice.meow(1.05f)
                if (r.ta) {
                    PetVoice.say("மியாவ்! ${r.name} டேட்டா வரம்பு முடிந்துவிட்டது!", "Meow! ${r.name} data limit reached!", true)
                } else {
                    PetVoice.say("Meow! ${r.name} data limit reached!", "Meow! ${r.name} data limit reached!", false)
                }
            }

            val bob = ValueAnimator.ofFloat(0f, 1f)
            bob.duration = 1500
            bob.repeatCount = ValueAnimator.INFINITE
            bob.interpolator = LinearInterpolator()
            bob.addUpdateListener {
                val s = Math.sin(it.animatedValue as Float * 2 * Math.PI).toFloat()
                pet.translationY = -dp(4) * (0.5f + 0.5f * s)
                pet.rotation = 2.5f * s
            }
            idle = bob
            bob.start()

            val ac = Runnable { dismiss() }
            autoClose = ac
            main.postDelayed(ac, STAY_MS)
        }

        // Cat starts moving this instant; first meow is immediate.
        if (r.sound) PetVoice.meow(1.0f)
        var secondMeow = false
        val tl = ValueAnimator.ofFloat(0f, TOTAL_MS.toFloat())
        tl.duration = TOTAL_MS
        tl.interpolator = LinearInterpolator()
        tl.addUpdateListener {
            val t = it.animatedValue as Float
            var tx = out
            var rot = 0f
            var ty = 0f
            val v1 = peek(t, PEEK1_AT, 280f, 300f, 240f)
            val v2 = peek(t, PEEK2_AT, 260f, 280f, 220f)
            if (t < WALK_AT) {
                if (v1 > 0f) {
                    tx = lerp(out, peek1, v1)
                    rot = 9f * v1 + 2.5f * v1 * Math.sin(t / 55.0).toFloat()
                }
                if (v2 > 0f) {
                    tx = lerp(out, peek2, v2)
                    rot = 11f * v2 + 2.5f * v2 * Math.sin(t / 55.0).toFloat()
                }
                if (!secondMeow && t >= PEEK2_AT) {
                    secondMeow = true
                    if (r.sound) PetVoice.meow(1.15f)
                }
            } else {
                val wp = clamp01((t - WALK_AT) / WALK_MS)
                tx = lerp(out, 0f, easeOut(wp))
                val ph = (wp * 5.0 * Math.PI).toFloat()
                rot = 6f * Math.sin(ph.toDouble()).toFloat()
                ty = -dp(9) * Math.abs(Math.sin(ph.toDouble())).toFloat()
            }
            pet.translationX = tx
            pet.translationY = ty
            pet.rotation = rot
        }
        tl.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(a: Animator) { cancelled = true }
            override fun onAnimationEnd(a: Animator) {
                if (!cancelled) {
                    pet.translationX = 0f
                    pet.translationY = 0f
                    pet.rotation = 0f
                    arrive()
                }
            }
        })
        timeline = tl
        tl.start()
    }

    private fun next(ctx: Context) {
        val n = queue.poll()
        if (n != null) display(ctx, n)
    }
}
