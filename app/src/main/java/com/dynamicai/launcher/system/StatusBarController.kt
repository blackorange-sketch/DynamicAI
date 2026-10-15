package com.dynamicai.launcher.system

import android.app.StatusBarManager
import android.content.Context
import android.util.Log

class StatusBarController(private val context: Context) {

    companion object {
        private const val TAG = "StatusBarCtrl"
    }

    fun expandNotifications(): Boolean {
        // 1. Пряме API (працює на HOME-застосунках)
        try {
            val sbm = context.getSystemService(Context.STATUS_BAR_SERVICE) as StatusBarManager
            sbm.expandNotificationsPanel()
            Log.d(TAG, "expandNotificationsPanel() OK (direct)")
            return true
        } catch (t: Throwable) {
            Log.w(TAG, "direct failed: ${t.message}")
        }

        // 2. Fallback через reflection
        try {
            val sbm = context.getSystemService(Context.STATUS_BAR_SERVICE)
            val m = sbm.javaClass.getMethod("expandNotificationsPanel")
            m.invoke(sbm)
            Log.d(TAG, "expandNotificationsPanel() OK (reflection)")
            return true
        } catch (t: Throwable) {
            Log.w(TAG, "reflection failed: ${t.message}")
        }

        // 3. Спробувати через AccessibilityService (якщо увімкнено)
        try {
            @Suppress("DEPRECATION")
            val m = Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
            val sbm = context.getSystemService(Context.STATUS_BAR_SERVICE)
            m.invoke(sbm)
            Log.d(TAG, "expandNotificationsPanel() OK (alt)")
            return true
        } catch (t: Throwable) {
            Log.w(TAG, "alt failed: ${t.message}")
        }

        return false
    }
}
