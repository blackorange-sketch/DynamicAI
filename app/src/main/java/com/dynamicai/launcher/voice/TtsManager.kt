package com.dynamicai.launcher.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Менеджер Text-to-Speech. Ініціалізується один раз, озвучує фрази.
 */
class TtsManager(private val context: Context) {

    companion object {
        private const val TAG = "TtsManager"
    }

    private val settingsStorage = com.dynamicai.launcher.data.SettingsStorage(context)

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val settings = settingsStorage.load()

                val result = tts?.setLanguage(Locale("uk", "UA"))
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "uk-UA not supported, fallback to default")
                    tts?.setLanguage(Locale.getDefault())
                }

                tts?.setSpeechRate(settings.ttsSpeed)
                tts?.setPitch(settings.ttsPitch)

                ready = true
                pendingText?.let {
                    speak(it)
                    pendingText = null
                }
                Log.d(TAG, "TTS initialized (speed=${settings.ttsSpeed}, pitch=${settings.ttsPitch})")
            } else {
                Log.e(TAG, "TTS init failed")
            }
        }
    }

    /**
     * Оновити швидкість/тон з налаштувань.
     */
    fun reloadSettings() {
        if (!ready) return
        try {
            val settings = settingsStorage.load()
            tts?.setSpeechRate(settings.ttsSpeed)
            tts?.setPitch(settings.ttsPitch)
            Log.d(TAG, "TTS settings reloaded: speed=${settings.ttsSpeed}, pitch=${settings.ttsPitch}")
        } catch (t: Throwable) {
            Log.e(TAG, "reloadSettings failed", t)
        }
    }

    fun speakWithCallback(text: String, onDone: () -> Unit) {
        try {
            val tts = this.tts ?: run { onDone(); return }
            val id = "voicechat_${System.currentTimeMillis()}"
            tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { onDone() }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { onDone() }
            })
            tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, id)
        } catch (_: Throwable) { onDone() }
    }

    fun stop() {
        try { tts?.stop() } catch (_: Throwable) {}
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
