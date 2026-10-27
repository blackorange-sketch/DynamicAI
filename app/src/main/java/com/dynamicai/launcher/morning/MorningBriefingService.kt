package com.dynamicai.launcher.morning

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MorningBriefingService : Service() {
    companion object {
        private const val TAG = "MorningBriefing"
        private const val CHANNEL_ID = "morning_briefing"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, MorningBriefingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Готую ранковий брифінг..."))
        scope.launch { generateBriefing() }
    }

    private suspend fun generateBriefing() {
        try {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val greeting = when {
                hour < 5 -> "Доброї ночі"
                hour < 12 -> "Доброго ранку"
                hour < 18 -> "Доброго дня"
                else -> "Доброго вечора"
            }

            // Отримуємо дані
            val battery = getBatteryLevel()
            val dateStr = SimpleDateFormat("EEEE, d MMMM", Locale("uk", "UA")).format(Date())
            
            // Пробуємо отримати погоду через WeatherRepository
            var weatherText = ""
            try {
                val weatherRepo = com.dynamicai.launcher.data.WeatherRepository(applicationContext)
                val cached = weatherRepo.loadCachedWeather()
                if (cached != null) {
                    weatherText = " Погода: ${cached.temperature}°, ${cached.condition}."
                }
            } catch (_: Exception) {}

            // Формуємо брифінг
            val briefing = "$greeting! Сьогодні $dateStr.$weatherText Заряд: $battery%. Чим можу допомогти?"

            // Оновлюємо нотифікацію
            val notif = buildNotification(briefing)
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, notif)

            // Озвучуємо через TTS
            try {
                val tts = com.dynamicai.launcher.AppListViewModel.getSharedLlm(applicationContext) // ініціалізуємо для TTS
                val ttsManager = com.dynamicai.launcher.voice.TtsManager(applicationContext)
                delay(500)
                ttsManager.speak(briefing)
            } catch (_: Exception) {}

            // Додаємо в історію чату
            ChatHistoryManager.addMessage("assistant", briefing)

            Log.d(TAG, "Briefing: $briefing")
        } catch (e: Exception) {
            Log.e(TAG, "Briefing failed", e)
        } finally {
            delay(3000)
            stopSelf()
        }
    }

    private fun getBatteryLevel(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        return bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun buildNotification(text: String): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Ранковий брифінг", NotificationManager.IMPORTANCE_DEFAULT)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
