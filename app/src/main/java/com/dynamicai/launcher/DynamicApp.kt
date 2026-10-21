package com.dynamicai.launcher

import android.app.Activity
import android.app.Application
import android.graphics.Rect
import android.os.Bundle
import android.view.ViewTreeObserver

class DynamicApp : Application() {

    private val listeners = mutableMapOf<Activity, ViewTreeObserver.OnGlobalLayoutListener>()

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                attachKeyboardListener(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                detachKeyboardListener(activity)
                // Скидаємо стан при закритті Activity
                getSharedPreferences("dynamicai_runtime", MODE_PRIVATE)
                    .edit().putBoolean("keyboard_open", false).apply()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun attachKeyboardListener(activity: Activity) {
        if (listeners.containsKey(activity)) return
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            try {
                val rect = Rect()
                activity.window.decorView.getWindowVisibleDisplayFrame(rect)
                val screenHeight = activity.resources.displayMetrics.heightPixels
                val keypadHeight = screenHeight - rect.bottom
                val isOpen = keypadHeight > screenHeight * 0.15
                getSharedPreferences("dynamicai_runtime", MODE_PRIVATE)
                    .edit().putBoolean("keyboard_open", isOpen).apply()
            } catch (_: Throwable) {}
        }
        activity.window.decorView.viewTreeObserver.addOnGlobalLayoutListener(listener)
        listeners[activity] = listener
    }

    private fun detachKeyboardListener(activity: Activity) {
        listeners.remove(activity)?.let {
            try {
                activity.window.decorView.viewTreeObserver.removeOnGlobalLayoutListener(it)
            } catch (_: Throwable) {}
        }
    }
}
