package com.datakaaval.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.speech.tts.TextToSpeech
import java.util.Locale

/** The cat's voice: a short meow sound plus a spoken line from the phone's own text-to-speech. */
object PetVoice {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pending: Triple<String, String, Boolean>? = null
    private var pool: SoundPool? = null
    private var meowId = 0
    private var meowLoaded = false

    private val attrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Loads the sound and the speech engine ahead of time so nothing waits when the limit is reached. */
    @Synchronized
    fun prewarm(ctx: Context) {
        val app = ctx.applicationContext
        if (pool == null) {
            val p = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attrs).build()
            p.setOnLoadCompleteListener { _, _, status -> meowLoaded = status == 0 }
            meowId = p.load(app, R.raw.meow, 1)
            pool = p
        }
        if (tts == null) {
            try {
                tts = TextToSpeech(app) { status ->
                    ttsReady = status == TextToSpeech.SUCCESS
                    if (ttsReady) {
                        tts?.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        val w = pending
                        pending = null
                        if (w != null) speakNow(w.first, w.second, w.third)
                    }
                }
            } catch (e: Exception) {
                tts = null
            }
        }
    }

    @Synchronized
    fun meow(rate: Float) {
        try {
            if (meowLoaded) pool?.play(meowId, 1f, 1f, 1, 0, rate)
        } catch (e: Exception) {
            // sound is optional
        }
    }

    /** Speaks [tamil] when the phone has a Tamil voice (and [ta] is true), otherwise [english]. */
    @Synchronized
    fun say(tamil: String, english: String, ta: Boolean) {
        if (tts == null) return
        if (!ttsReady) {
            pending = Triple(tamil, english, ta)
            return
        }
        speakNow(tamil, english, ta)
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
            t.setPitch(1.7f)
            t.setSpeechRate(1.05f)
            t.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "pet")
        } catch (e: Exception) {
            // speech is optional
        }
    }

    @Synchronized
    fun stop() {
        pending = null
        try {
            tts?.stop()
        } catch (e: Exception) {
        }
    }
}
