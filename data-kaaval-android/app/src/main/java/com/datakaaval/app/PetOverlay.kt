package com.datakaaval.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.util.LinkedList

/**
 * The mascot alert. A transparent web page (assets/pet.html) plays the animation on top of every app:
 * liquid sweeps in from the left edge, the mascot peeks twice, hops in, and talks. The page never takes
 * touches; only the two small buttons (a separate native window) do.
 */
object PetOverlay {

    private class Req(
        val name: String, val nums: String, val pct: Int,
        val ta: Boolean, val sound: Boolean, val quick: Boolean, val onClosed: () -> Unit, val fallback: () -> Unit
    )

    private const val STAY_AFTER_SPEECH_MS = 5000L
    private const val STAY_HARD_MAX_MS = 25000L
    private const val IDLE_FREE_MS = 8 * 60_000L

    private val main = Handler(Looper.getMainLooper())
    private val queue = LinkedList<Req>()
    private var web: WebView? = null
    @Volatile private var pageReady = false
    private var cur: Req? = null
    private var buttons: LinearLayout? = null
    private var closing = false
    private var autoClose: Runnable? = null
    private var appCtx: Context? = null

    private val freeWeb = Runnable {
        if (cur == null) destroyWeb()
    }

    /** Called while some limit is still waiting to be reached, so the mascot can appear with no loading delay. */
    fun prewarm(ctx: Context) {
        val app = ctx.applicationContext
        main.post {
            appCtx = app
            PetVoice.prewarm(app)
            ensureWeb(app)
            main.removeCallbacks(freeWeb)
            main.postDelayed(freeWeb, IDLE_FREE_MS)
        }
    }

    private fun destroyWeb() {
        try {
            web?.destroy()
        } catch (e: Exception) {
        }
        web = null
        pageReady = false
    }

    private fun ensureWeb(app: Context): WebView {
        web?.let { return it }
        val w = WebView(app)
        w.setBackgroundColor(Color.TRANSPARENT)
        w.overScrollMode = WebView.OVER_SCROLL_NEVER
        w.isVerticalScrollBarEnabled = false
        w.isHorizontalScrollBarEnabled = false
        w.settings.javaScriptEnabled = true
        w.settings.allowFileAccess = false
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(app))
            .build()
        w.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // the system reclaimed the page; build a fresh one next time
                pageReady = false
                web = null
                return true
            }
        }
        w.addJavascriptInterface(PetBridge(), "KaavalPet")
        pageReady = false
        w.loadUrl("https://appassets.androidplatform.net/assets/pet.html")
        web = w
        return w
    }

    class PetBridge {
        @JavascriptInterface
        fun ready(s: String) { pageReady = true }

        @JavascriptInterface
        fun sfx(name: String) { PetVoice.play(name) }

        @JavascriptInterface
        fun arrived(s: String) { main.post { onArrived() } }

        @JavascriptInterface
        fun closed(s: String) { main.post { finish() } }
    }

    fun show(ctx: Context, name: String, nums: String, pct: Int, ta: Boolean, sound: Boolean, quick: Boolean,
             fallback: () -> Unit, onClosed: () -> Unit) {
        val app = ctx.applicationContext
        main.post {
            appCtx = app
            val r = Req(name, nums, pct, ta, sound, quick, onClosed, fallback)
            if (cur != null) queue.add(r) else display(app, r)
        }
    }

    private fun js(code: String) {
        try {
            web?.evaluateJavascript(code, null)
        } catch (e: Exception) {
        }
    }

    private fun display(app: Context, r: Req) {
        cur = r
        closing = false
        main.removeCallbacks(freeWeb)
        PetVoice.prewarm(app)
        val w = ensureWeb(app)
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        try {
            (w.parent as? ViewGroup)?.removeView(w)
            wm.addView(w, lp)
        } catch (e: Exception) {
            abort(r)
            return
        }
        val json = JSONObject()
            .put("name", r.name).put("nums", r.nums).put("pct", r.pct)
            .put("ta", r.ta).put("sound", r.sound).put("quick", r.quick).toString()
        var waited = 0
        val starter = object : Runnable {
            override fun run() {
                if (cur !== r) return
                if (pageReady) {
                    js("window.petStart($json)")
                } else if (waited >= 2000) {
                    abort(r)
                } else {
                    waited += 40
                    main.postDelayed(this, 40)
                }
            }
        }
        starter.run()
    }

    /** The page could not be shown: tidy up and let the caller use the plain full-screen popup instead. */
    private fun abort(r: Req) {
        removeStage()
        cur = null
        try {
            r.fallback()
        } catch (e: Exception) {
        }
        next()
    }

    private fun removeStage() {
        val app = appCtx ?: return
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        buttons?.let {
            try { wm.removeView(it) } catch (e: Exception) { }
        }
        buttons = null
        web?.let {
            try { wm.removeView(it) } catch (e: Exception) { }
        }
        autoClose?.let { main.removeCallbacks(it) }
        autoClose = null
    }

    private fun onArrived() {
        val r = cur ?: return
        if (closing) return
        val app = appCtx ?: return
        showButtons(app, r)
        // the mascot leaves 5 seconds after it has finished telling the person
        val ac = Runnable { dismiss() }
        autoClose = ac
        main.postDelayed(ac, STAY_HARD_MAX_MS)
        val afterSpeech = Runnable {
            js("window.petSpeaking(false)")
            if (cur === r && !closing) {
                autoClose?.let { main.removeCallbacks(it) }
                autoClose = ac
                main.postDelayed(ac, STAY_AFTER_SPEECH_MS)
            }
        }
        if (r.sound) {
            val line = if (r.ta) "ஐயோ! ${r.name} டேட்டா வரம்பு முடிந்துவிட்டது!" else "Oh no! ${r.name} data limit reached!"
            val en = "Oh no! ${r.name} data limit reached!"
            PetVoice.say(line, en, r.ta, { js("window.petSpeaking(true)") }, { afterSpeech.run() })
        } else {
            autoClose?.let { main.removeCallbacks(it) }
            main.postDelayed(ac, STAY_AFTER_SPEECH_MS)
        }
    }

    private fun showButtons(app: Context, r: Req) {
        val d = app.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * d).toInt()
        val wdp = app.resources.displayMetrics.widthPixels / d
        val bubbleW = Math.min(wdp - 24f, 360f)
        val row = LinearLayout(app)
        row.orientation = LinearLayout.HORIZONTAL
        fun btn(text: String, fill: Int, fg: Int, click: () -> Unit): TextView {
            val t = TextView(app)
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
        val open = btn(if (r.ta) "ஆப்பைத் திற" else "Open app", Color.parseColor("#2A3858"), Color.WHITE) {
            dismiss()
            try {
                app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
            }
        }
        row.addView(ok, LinearLayout.LayoutParams(0, dp(44), 1f))
        row.addView(open, LinearLayout.LayoutParams(0, dp(44), 1.4f).apply { leftMargin = dp(10) })
        row.alpha = 0f
        val lp = WindowManager.LayoutParams(
            dp((bubbleW - 36f).toInt()), ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.y = dp(192 + 14)
        try {
            val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.addView(row, lp)
            buttons = row
            row.animate().alpha(1f).setStartDelay(220).setDuration(240).start()
        } catch (e: Exception) {
            buttons = null
        }
    }

    private fun dismiss() {
        if (closing) return
        closing = true
        autoClose?.let { main.removeCallbacks(it) }
        autoClose = null
        PetVoice.stop()
        val app = appCtx
        val b = buttons
        if (app != null && b != null) {
            try {
                (app.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(b)
            } catch (e: Exception) {
            }
            buttons = null
        }
        js("window.petLeave()")
        // safety net in case the page never reports that it finished
        val r = cur
        main.postDelayed({ if (cur === r && closing) finish() }, 1600)
    }

    private fun finish() {
        val r = cur ?: return
        removeStage()
        js("window.petReset()")
        cur = null
        closing = false
        try {
            r.onClosed()
        } catch (e: Exception) {
        }
        main.removeCallbacks(freeWeb)
        main.postDelayed(freeWeb, IDLE_FREE_MS)
        next()
    }

    private fun next() {
        val app = appCtx ?: return
        val n = queue.poll()
        if (n != null) display(app, n)
    }
}
