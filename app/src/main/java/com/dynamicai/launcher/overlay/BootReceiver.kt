package com.dynamicai.launcher.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            val prefs = context.getSharedPreferences("dynamicai_settings", Context.MODE_PRIVATE)
            val wasEnabled = prefs.getBoolean("overlayEnabled", false)
            if (wasEnabled && Settings.canDrawOverlays(context)) {
                FloatingDockService.start(context)
            }
        } catch (_: Throwable) {}
        try { com.dynamicai.launcher.macro.MacroTriggerService.sync(context, fromBoot = true) } catch (_: Throwable) {}
    }
}
