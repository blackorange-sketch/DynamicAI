package com.dynamicai.launcher.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * AccessibilityService для перехоплення свайпу знизу вгору.
 *
 * Створює прозору зону висотою 24dp вздовж нижнього краю екрана.
 * Слухає свайпи в цій зоні і викликає колбек.
 */
class RecentGestureService : AccessibilityService() {

    companion object {
        private const val TAG = "RecentGesture"

        @Volatile var isConnected: Boolean = false
            private set

        @Volatile var onSwipeUp: (() -> Unit)? = null

        fun isEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${RecentGestureService::class.java.name}"
            val setting = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return setting.contains(expected)
        }
    }

    private lateinit var windowManager: WindowManager
    private var gestureView: View? = null

    // Для визначення свайпу
    private var downY = 0f
    private var downTime = 0L
    private var tracking = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        Log.d(TAG, "Service connected")

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        attachGestureZone()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // не використовуємо
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        isConnected = false
        try {
            gestureView?.let { windowManager.removeView(it) }
        } catch (_: Throwable) {}
        gestureView = null
        Log.d(TAG, "Service destroyed")
    }

    private fun attachGestureZone() {
        if (gestureView != null) return

        val view = object : View(this) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downY = event.rawY
                        downTime = System.currentTimeMillis()
                        tracking = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!tracking) return false
                        val dy = downY - event.rawY
                        // Свайп вгору >= 60 px
                        if (dy > 60f) {
                            tracking = false
                            try {
                                onSwipeUp?.invoke()
                            } catch (t: Throwable) {
                                Log.e(TAG, "onSwipeUp failed", t)
                            }
                            return true
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        tracking = false
                        return true
                    }
                }
                return super.onTouchEvent(event)
            }
        }

        val density = resources.displayMetrics.density
        val heightPx = (24 * density).toInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            heightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        try {
            windowManager.addView(view, params)
            gestureView = view
            Log.d(TAG, "Gesture zone attached: ${heightPx}px")
        } catch (t: Throwable) {
            Log.e(TAG, "attachGestureZone failed", t)
        }
    }
}
