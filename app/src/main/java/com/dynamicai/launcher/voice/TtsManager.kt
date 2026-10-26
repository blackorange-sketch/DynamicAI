package com.dynamicai.launcher.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TtsManager(private val context: Context) {
    companion object { private const val TAG = "TtsManager" }

    private val settingsStorage = com.dynamicai.launcher.data.SettingsStorage(context)
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null
    private var pendingCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val settings = settingsStorage.load()
                
                // Перевіряємо підтримку української мови
                val langResult = tts?.setLanguage(Locale("uk", "UA"))
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "uk-UA not supported, trying ru-RU")
                    val ruResult = tts?.setLanguage(Locale("ru", "RU"))
                    if (ruResult == TextToSpeech.LANG_MISSING_DATA || ruResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w(TAG, "ru-RU not supported either, fallback to default")
                        tts?.setLanguage(Locale.getDefault())
                    }
                }
                
                tts?.setSpeechRate(settings.ttsSpeed)
                tts?.setPitch(settings.ttsPitch)

                ready = true
                Log.d(TAG, "TTS initialized (speed=${settings.ttsSpeed}, pitch=${settings.ttsPitch})")
                
                // Якщо щось було в черзі, поки двигун вантажився - виконуємо
                pendingText?.let {
                    val cb = pendingCallback
                    pendingText = null
                    pendingCallback = null
                    if (cb != null) speakWithCallback(it, cb) else speak(it)
                }
            } else {
                Log.e(TAG, "TTS init failed with status $status")
            }
        }
    }

    fun reloadSettings() {
        if (!ready) return
        try {
            val settings = settingsStorage.load()
            tts?.setSpeechRate(settings.ttsSpeed)
            tts?.setPitch(settings.ttsPitch)
            Log.d(TAG, "TTS settings reloaded")
        } catch (t: Throwable) {
            Log.e(TAG, "reloadSettings failed", t)
        }
    }

    fun speakWithCallback(text: String, onDone: () -> Unit) {
        try {
            val tts = this.tts
            if (tts == null || !ready) {
                Log.d(TAG, "TTS not ready yet, queueing text and callback")
                pendingText = text
                pendingCallback = onDone
                return
            }

            var completed = false
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            
            // Запобіжник часу (щоб додаток не завис назавжди, якщо двигун мовчить)
            val timeoutRunnable = Runnable {
                if (!completed) {
                    Log.w(TAG, "TTS timeout! Forcing onDone.")
                    completed = true
                    onDone()
                }
            }
            val timeoutMs = maxOf(5000L, (text.length * 150L).coerceAtMost(30000L))
            handler.postDelayed(timeoutRunnable, timeoutMs)

            val id = "voicechat_${System.currentTimeMillis()}"
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { Log.d(TAG, "TTS onStart: $utteranceId") }
                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS onDone: $utteranceId")
                    if (!completed) {
                        completed = true
                        handler.removeCallbacks(timeoutRunnable)
                        onDone()
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS onError: $utteranceId")
                    if (!completed) {
                        completed = true
                        handler.removeCallbacks(timeoutRunnable)
                        onDone()
                    }
                }
            })

            Log.d(TAG, "Executing speak: $text")
            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            
            if (result == TextToSpeech.ERROR) {
                Log.e(TAG, "TTS speak returned ERROR")
                if (!completed) {
                    completed = true
                    handler.removeCallbacks(timeoutRunnable)
                    onDone()
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "speakWithCallback failed", t)
            onDone()
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) {
            pendingText = text
            return
        }
        try {
            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dynamicai_${System.currentTimeMillis()}")
            if (result == TextToSpeech.ERROR) Log.e(TAG, "speak returned ERROR")
            else Log.d(TAG, "Speak command sent: $text")
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
