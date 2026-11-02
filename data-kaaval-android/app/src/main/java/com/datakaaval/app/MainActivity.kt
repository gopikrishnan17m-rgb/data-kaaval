package com.datakaaval.app

import android.app.Activity
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = WebView(this)
        setContentView(view)
        web = view

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

        val work = PeriodicWorkRequestBuilder<CheckWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(applicationContext)
            .enqueueUniquePeriodicWork("kaaval-check", ExistingPeriodicWorkPolicy.KEEP, work)
    }

    override fun onResume() {
        super.onResume()
        visible = true
        view.onResume()
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
