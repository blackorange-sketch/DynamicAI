package com.dynamicai.launcher.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import com.dynamicai.launcher.MainActivity

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("text") ?: "Нагадування"
        val id = intent.getIntExtra("id", 0)

        val channelId = "dynamicai_reminder"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= 26) {
            if (nm.getNotificationChannel(channelId) == null) {
                val ch = NotificationChannel(
                    channelId, "Нагадування",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Нагадування від DynamicAI"
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
                    enableVibration(true)
                }
                nm.createNotificationChannel(ch)
            }
        }

        val pi = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("⏰ Нагадування")
            .setContentText(text)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(id, notif)

        try {
            val tts = android.speech.tts.TextToSpeech(context, null)
            tts.language = java.util.Locale("uk", "UA")
            tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "reminder_$id")
        } catch (_: Throwable) {}
    }
}
