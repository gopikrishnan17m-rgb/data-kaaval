package com.datakaaval.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.WindowInsets
import android.widget.FrameLayout
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {

    companion object {
        @Volatile var visible = false
        @Volatile var web: WebView? = null
    }

    private lateinit var view: WebView
    private lateinit var root: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = WebView(this)
        root = FrameLayout(this)
        root.setBackgroundColor(0xFF0A0F1C.toInt())
        root.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
        web = view

        // Android 15+ always draws edge to edge, so keep the page clear of the status bar, navigation bar and keyboard.
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom))
                WindowInsets.CONSUMED
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom
                )
                @Suppress("DEPRECATION")
                insets.consumeSystemWindowInsets()
            }
        }

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.allowFileAccess = false
        view.setBackgroundColor(0xFF0A0F1C.toInt())

        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse? {
                return loader.shouldInterceptRequest(request.url)
            }
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean = true
        }
        view.addJavascriptInterface(Bridge(this), "KaavalNative")
        view.loadUrl("https://appassets.androidplatform.net/assets/index.html")

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        }

        val work = PeriodicWorkRequestBuilder<CheckWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(applicationContext)
            .enqueueUniquePeriodicWork("kaaval-check", ExistingPeriodicWorkPolicy.KEEP, work)
    }

    /** Paints the area behind the status bar and navigation bar with the app's background colours. */
    fun setChrome(top: Int, bottom: Int) {
        root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom))
    }

    override fun onResume() {
        super.onResume()
        visible = true
        AdGate.onOpen(this)
        view.onResume()
        MonitorService.start(this)
        view.evaluateJavascript("window.onNativeResume&&window.onNativeResume()", null)
    }

    override fun onPause() {
        visible = false
        view.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (web === view) web = null
        super.onDestroy()
    }
}
