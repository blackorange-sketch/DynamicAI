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

class PomodoroReceiver : BroadcastReceiver() {
    companion object {
        const val CHANNEL_ID = "dynamicai_pomodoro"
        const val NOTIF_ID = 8501
        const val ACTION_BREAK = "com.dynamicai.launcher.POMODORO_BREAK"
        const val ACTION_STOP = "com.dynamicai.launcher.POMODORO_STOP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val mode = intent.getStringExtra("mode") ?: "work"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= 26) {
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID, "Pomodoro",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "\u0422\u0430\u0439\u043c\u0435\u0440 \u0444\u043e\u043a\u0443\u0441\u0443"
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
                    enableVibration(true)
                }
                nm.createNotificationChannel(ch)
            }
        }

        val title: String
        val text: String
        if (mode == "work") {
            title = "\u2705 \u0427\u0430\u0441 \u043f\u0435\u0440\u0435\u0440\u0432\u0438!"
            text = "\u0412\u0456\u0434\u043f\u043e\u0447\u0438\u043d\u044c 5 \u0445\u0432\u0438\u043b\u0438\u043d"
        } else {
            title = "\ud83d\udcaa \u041f\u043e\u0432\u0435\u0440\u0442\u0430\u0439\u0441\u044f \u0434\u043e \u0440\u043e\u0431\u043e\u0442\u0438"
            text = "\u041d\u043e\u0432\u0438\u0439 \u0444\u043e\u043a\u0443\u0441-\u0441\u0435\u0430\u043d\u0441"
        }

        val openPi = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val breakPi = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, PomodoroActionReceiver::class.java).setAction(ACTION_BREAK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopPi = PendingIntent.getBroadcast(
            context, 2,
            Intent(context, PomodoroActionReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openPi)
            .setAutoCancel(false)
            .setOngoing(true)

        if (mode == "work") {
            builder.addAction(android.R.drawable.ic_media_play, "\u041f\u0435\u0440\u0435\u0440\u0432\u0430 5 \u0445\u0432", breakPi)
        } else {
            builder.addAction(android.R.drawable.ic_media_play, "\u0429\u0435 25 \u0445\u0432", breakPi)
        }
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "\u0421\u0442\u043e\u043f", stopPi)

        nm.notify(NOTIF_ID, builder.build())

        try {
            val tts = android.speech.tts.TextToSpeech(context, null)
            tts.language = java.util.Locale("uk", "UA")
            val speak = if (mode == "work") "\u0427\u0430\u0441 \u043f\u0435\u0440\u0435\u0440\u0432\u0438" else "\u041f\u043e\u0432\u0435\u0440\u0442\u0430\u0439\u0441\u044f \u0434\u043e \u0440\u043e\u0431\u043e\u0442\u0438"
            tts.speak(speak, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "pomodoro")
        } catch (_: Throwable) {}
    }
}
