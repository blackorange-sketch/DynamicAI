package com.dynamicai.launcher.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.dynamicai.launcher.MainActivity
import com.dynamicai.launcher.command.CommandExecutor
import com.dynamicai.launcher.command.CommandParser
import com.dynamicai.launcher.data.AppRepository
import com.dynamicai.launcher.data.SettingsStorage
import com.dynamicai.launcher.model.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WakeWordService : Service() {

    companion object {
        private const val TAG = "WakeWord"
        private const val CHANNEL_ID = "wake_word_channel"
        private const val NOTIF_ID = 7711
        const val ACTION_START = "com.dynamicai.launcher.WAKE_START"
        const val ACTION_STOP = "com.dynamicai.launcher.WAKE_STOP"

        fun start(ctx: Context) {
            val i = Intent(ctx, WakeWordService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
        fun stop(ctx: Context) {
            val i = Intent(ctx, WakeWordService::class.java).setAction(ACTION_STOP)
            ctx.startService(i)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val parser = CommandParser()
    private val executor by lazy { CommandExecutor(this) }
    private var recognizer: SpeechRecognizer? = null
    private var apps: List<AppInfo> = emptyList()
    private var listening = false
    private var restartJob: Job? = null
    private var wakeWord: String = ""
    private var enabled = true
    private val overlay by lazy { com.dynamicai.launcher.overlay.VoiceCommandOverlay(this) }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        scope.launch {
            apps = try { AppRepository(this@WakeWordService).loadCached() } catch (_: Throwable) { emptyList() }
            reloadSettings()
            startListening()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { enabled = false; stopSelf(); return START_NOT_STICKY }
            ACTION_START -> {
                enabled = true
                if (!listening) startListening()
            }
        }
        return START_STICKY
    }

    private fun reloadSettings() {
        val st = SettingsStorage(this).load()
        wakeWord = st.wakeWord.lowercase().trim()
        enabled = st.wakeWordEnabled
        if (!enabled) stopSelf()
    }

    private fun startListening() {
        if (!enabled) return
        if (wakeWord.isBlank()) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "SpeechRecognizer недоступний")
            return
        }

        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}

                    override fun onPartialResults(partialResults: Bundle?) {
                        val text = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull() ?: return
                        Log.d(TAG, "partial: \"$text\"")
                    }

                    override fun onResults(results: Bundle?) {
                        val text = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull() ?: ""
                        handleText(text)
                        scheduleRestart(300)
                    }

                    override fun onError(error: Int) {
                        val name = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
                            SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "PERMISSIONS"
                            SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "NETWORK_TIMEOUT"
                            SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "BUSY"
                            SpeechRecognizer.ERROR_SERVER -> "SERVER"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SPEECH_TIMEOUT"
                            else -> error.toString()
                        }
                        Log.d(TAG, "onError: $error ($name)")
                        scheduleRestart(500)
                    }
                })
            }
        }

        listening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // WEB_SEARCH краще для коротких команд, ніж FREE_FORM
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
            // спробуй ru-RU якщо uk-UA не розпізнає
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "uk-UA")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            // Довші паузи — користувач має встигнути договорити
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
        }
        try { recognizer?.startListening(intent) } catch (t: Throwable) {
            Log.w(TAG, "startListening: ${t.message}")
            listening = false
            scheduleRestart(1000)
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        listening = false
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(delayMs)
            if (enabled) startListening()
        }
    }

    private fun handleText(raw: String) {
        reloadSettings()
        if (!enabled || wakeWord.isBlank()) return
        val lc = raw.lowercase()
        val idx = lc.indexOf(wakeWord)
        if (idx < 0) return
        var command = raw.substring(idx + wakeWord.length).trim()
        command = command.trimStart(',', '.', '!', '?', ':', ';', '-', ' ').trim()
        if (command.isBlank()) return
        Log.d(TAG, "wake=$wakeWord command=$command")

        val exec: () -> Unit = {
            try {
                val cmd = parser.parse(command)
                executor.execute(cmd, apps)
            } catch (t: Throwable) {
                Log.e(TAG, "execute failed", t)
            }
        }

        if (overlay.canShow()) {
            overlay.show(command, onExecute = exec, onCancel = {
                Log.d(TAG, "cancelled by user")
            })
        } else {
            exec()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Голосова активація",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply { setShowBadge(false) }
                )
            }
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, CHANNEL_ID)
        else
            @Suppress("DEPRECATION") Notification.Builder(this)
        return b
            .setContentTitle("Голосова активація активна")
            .setContentText("Слухаю \"$wakeWord\"")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Вимкнути", stop)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        try { recognizer?.stopListening() } catch (_: Throwable) {}
        try { recognizer?.destroy() } catch (_: Throwable) {}
        recognizer = null
        restartJob?.cancel()
        overlay.hide()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
