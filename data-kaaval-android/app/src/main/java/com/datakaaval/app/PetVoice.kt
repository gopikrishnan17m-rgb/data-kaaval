package com.datakaaval.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** The mascot's sounds (splash, pop, chirp) and its spoken line from the phone's own text-to-speech. */
object PetVoice {

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pending: Array<Any>? = null
    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    private val loaded = HashSet<Int>()
    private var onStart: (() -> Unit)? = null
    private var onDone: (() -> Unit)? = null
    private var fallback: Runnable? = null

    @Synchronized
    fun prewarm(ctx: Context) {
        val app = ctx.applicationContext
        if (pool == null) {
            val p = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            ).build()
            p.setOnLoadCompleteListener { _, id, status -> if (status == 0) synchronized(this) { loaded.add(id) } }
            ids["hum"] = p.load(app, R.raw.hum, 1)
            pool = p
        }
        if (tts == null) {
            try {
                tts = TextToSpeech(app) { status ->
                    ttsReady = status == TextToSpeech.SUCCESS
                    if (ttsReady) {
                        val t = tts
                        t?.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        t?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) { main.post { onStart?.invoke() } }
                            override fun onDone(utteranceId: String?) { main.post { onDone?.invoke() } }
                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) { main.post { onDone?.invoke() } }
                        })
                        val w = pending
                        pending = null
                        if (w != null) speakNow(w[0] as String, w[1] as String, w[2] as Boolean)
                    }
                }
            } catch (e: Exception) {
                tts = null
            }
        }
    }

    @Synchronized
    fun play(name: String) {
        try {
            val id = ids[name] ?: return
            if (loaded.contains(id)) pool?.play(id, 1f, 1f, 1, 0, 1f)
        } catch (e: Exception) {
            // sound is optional
        }
    }

    /**
     * Speaks [tamil] when the phone has a Tamil voice (and [ta] is true), otherwise [english].
     * [start] and [done] let the mascot move its mouth only while the voice is talking.
     */
    @Synchronized
    fun say(tamil: String, english: String, ta: Boolean, start: () -> Unit, done: () -> Unit) {
        onStart = start
        onDone = done
        if (tts == null) {
            silentTalk()
            return
        }
        if (!ttsReady) {
            pending = arrayOf(tamil, english, ta)
            return
        }
        speakNow(tamil, english, ta)
    }

    private fun silentTalk() {
        main.post { onStart?.invoke() }
        val r = Runnable { onDone?.invoke() }
        fallback = r
        main.postDelayed(r, 2400)
    }

    private fun speakNow(tamil: String, english: String, ta: Boolean) {
        val t = tts ?: return
        try {
            var phrase = english
            var ok = false
            if (ta) {
                val r = t.setLanguage(Locale("ta", "IN"))
                ok = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                if (ok) phrase = tamil
            }
            if (!ok) t.setLanguage(Locale.US)
            t.setPitch(1.55f)
            t.setSpeechRate(1.05f)
            if (t.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "pet") != TextToSpeech.SUCCESS) silentTalk()
        } catch (e: Exception) {
            silentTalk()
        }
    }

    @Synchronized
    fun stop() {
        pending = null
        onStart = null
        onDone = null
        fallback?.let { main.removeCallbacks(it) }
        fallback = null
        try {
            tts?.stop()
        } catch (e: Exception) {
        }
    }
}
