package com.dynamicai.launcher.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Менеджер Text-to-Speech. Ініціалізується один раз, озвучує фрази.
 */
class TtsManager(context: Context) {

    companion object {
        private const val TAG = "TtsManager"
    }

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("uk", "UA"))
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "uk-UA not supported, fallback to default")
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.0f)

                ready = true
                pendingText?.let {
                    speak(it)
                    pendingText = null
                }
                Log.d(TAG, "TTS initialized")
            } else {
                Log.e(TAG, "TTS init failed")
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return

        if (!ready) {
            pendingText = text
            return
        }

        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dynamicai_${System.currentTimeMillis()}")
            Log.d(TAG, "Speak: $text")
        } catch (t: Throwable) {
            Log.e(TAG, "speak failed", t)
        }
    }

    fun stop() {
        try { tts?.stop() } catch (_: Throwable) {}
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Throwable) {}
        tts = null
        ready = false
    }

    val isReady: Boolean get() = ready
}
