package com.dynamicai.launcher.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class CornerGestureService : Service() {

    private lateinit var wm: WindowManager
    private var leftCorner: View? = null
    private var rightCorner: View? = null

    companion object {
        private const val TAG = "CornerGesture"
        const val SIZE_DP = 24
        const val SWIPE_THRESHOLD = 40f
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addCornerViews()
        Log.e(TAG, "service started")
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun addCornerViews() {
        val sizePx = dp(SIZE_DP)
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        // Лівий нижній кут
        val leftParams = WindowManager.LayoutParams(
            sizePx, sizePx,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = 0
            y = dp(80)
        }

        // Правий нижній кут
        val rightParams = WindowManager.LayoutParams(
            sizePx, sizePx,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 0
            y = dp(80)
        }

        leftCorner = View(this).apply {
            setOnTouchListener(SwipeListener())
        }
        rightCorner = View(this).apply {
            setOnTouchListener(SwipeListener())
        }

        try {
            wm.addView(leftCorner, leftParams)
            wm.addView(rightCorner, rightParams)
        } catch (t: Throwable) {
            Log.e(TAG, "addView failed: ${t.message}")
        }
    }

    private inner class SwipeListener : View.OnTouchListener {
        private var startY = 0f
        private var startX = 0f
        private var triggered = false

        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            if (event == null) return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startX = event.rawX
                    triggered = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (triggered) return true
                    val dy = startY - event.rawY // свайп вгору
                    val dx = Math.abs(event.rawX - startX)
                    if (dy > SWIPE_THRESHOLD && dx < 100) {
                        triggered = true
                        Log.e(TAG, "swipe up detected from corner")
                        // AiHubOverlay.show(applicationContext)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    triggered = false
                }
            }
            return true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try { leftCorner?.let { wm.removeView(it) } } catch (_: Throwable) {}
        try { rightCorner?.let { wm.removeView(it) } } catch (_: Throwable) {}
        super.onDestroy()
    }
}
