package com.dynamicai.launcher.morning

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class WakeUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_USER_PRESENT) {
            Log.d("WakeUpReceiver", "User unlocked screen")
            WakeUpDetector(context).onUserPresent()
        }
    }
}
