package com.dynamicai.launcher.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class KeyboardAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "KeyboardA11y"
    }

    private lateinit var prefs: SharedPreferences

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("dynamicai_runtime", MODE_PRIVATE)
        Log.e(TAG, "service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        try {
            val pkg = event.packageName?.toString() ?: ""
            val cls = event.className?.toString() ?: ""
            val isIme = pkg.contains("inputmethod", ignoreCase = true)
                    || pkg.contains("gboard", ignoreCase = true)
                    || pkg.contains("swiftkey", ignoreCase = true)
                    || pkg.contains("keyboard", ignoreCase = true)
                    || cls.contains("InputMethod", ignoreCase = true)
                    || cls.contains("Keyboard", ignoreCase = true)

            // Також перевіримо через вікна сервісу
            val windows = windows
            val imeVisible = windows?.any { w ->
                w.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD
            } ?: isIme

            if (imeVisible) {
                prefs.edit().putBoolean("keyboard_open", true).apply()
                Log.e(TAG, "keyboard OPEN (pkg=$pkg)")
            } else if (isIme) {
                // Клавіатура закрилась — IME-вікно зникло
                prefs.edit().putBoolean("keyboard_open", false).apply()
                Log.e(TAG, "keyboard CLOSED (pkg=$pkg)")
            }
        } catch (_: Throwable) {}
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try {
            prefs.edit().putBoolean("keyboard_open", false).apply()
        } catch (_: Throwable) {}
        Log.e(TAG, "service destroyed")
    }
}
