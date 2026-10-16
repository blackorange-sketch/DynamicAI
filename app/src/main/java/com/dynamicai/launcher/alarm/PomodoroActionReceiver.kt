package com.dynamicai.launcher.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PomodoroActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (intent.action) {
            PomodoroReceiver.ACTION_BREAK -> {
                schedulePomodoro(context, minutes = 5, mode = "break")
            }
            PomodoroReceiver.ACTION_STOP -> {
                nm.cancel(PomodoroReceiver.NOTIF_ID)
            }
        }
    }

    private fun schedulePomodoro(context: Context, minutes: Int, mode: String) {
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        val id = if (mode == "work") 9101 else 9102

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PomodoroReceiver::class.java).apply {
            putExtra("mode", mode)
        }
        val pi = PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }
}
