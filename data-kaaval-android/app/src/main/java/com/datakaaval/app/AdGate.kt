package com.datakaaval.app

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Shows at most 2 full-screen video ads per day, only inside the app, only after the person has
 * been using it for a while, and never on top of other apps or on the limit popup.
 */
object AdGate {

    private const val MAX_PER_DAY = 2
    private const val MIN_GAP_MS = 3L * 3600_000L
    private const val MIN_OPEN_MS = 30_000L

    private val main = Handler(Looper.getMainLooper())
    private var ad: InterstitialAd? = null
    private var loading = false
    private var started = false

    @Volatile
    private var openedAt = 0L

    fun onOpen(ctx: Context) {
        openedAt = System.currentTimeMillis()
        if (!BuildConfig.ADS_ENABLED) return
        val app = ctx.applicationContext
        if (!started) {
            started = true
            thread {
                try {
                    MobileAds.initialize(app) { }
                    main.post { load(app) }
                } catch (e: Exception) {
                    started = false
                }
            }
        } else {
            main.post { load(app) }
        }
    }

    private fun load(app: Context) {
        if (ad != null || loading) return
        loading = true
        try {
            InterstitialAd.load(app, BuildConfig.AD_INTERSTITIAL, AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(loaded: InterstitialAd) {
                        ad = loaded
                        loading = false
                    }

                    override fun onAdFailedToLoad(e: LoadAdError) {
                        ad = null
                        loading = false
                    }
                })
        } catch (e: Exception) {
            loading = false
        }
    }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Called when the person moves between tabs inside the app. */
    fun maybeShow(activity: Activity) {
        if (!BuildConfig.ADS_ENABLED) return
        val shown = ad ?: return
        val now = System.currentTimeMillis()
        if (now - openedAt < MIN_OPEN_MS) return
        val prefs = activity.getSharedPreferences("ads", Context.MODE_PRIVATE)
        val day = today()
        var n = if (prefs.getString("day", "") == day) prefs.getInt("n", 0) else 0
        if (n >= MAX_PER_DAY) return
        if (now - prefs.getLong("last", 0L) < MIN_GAP_MS) return
        n += 1
        prefs.edit().putString("day", day).putInt("n", n).putLong("last", now).apply()
        shown.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                ad = null
                load(activity.applicationContext)
            }

            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                ad = null
                load(activity.applicationContext)
            }
        }
        ad = null
        shown.show(activity)
    }
}
