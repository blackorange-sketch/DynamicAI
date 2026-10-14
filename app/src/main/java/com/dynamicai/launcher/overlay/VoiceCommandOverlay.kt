package com.dynamicai.launcher.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class VoiceCommandOverlay(private val context: Context) {

    companion object {
        private const val TAG = "VoiceOverlay"
        private const val AUTO_EXEC_MS = 2500L
        private const val AUTO_HIDE_MS = 4500L
    }

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var root: View? = null
    private var pendingExec: Runnable? = null
    private var pendingHide: Runnable? = null

    fun canShow(): Boolean {
        // Nubia: Settings.canDrawOverlays() бреше. FloatingDockService працює -> довіряємо try/catch.
        return true
    }

    fun show(heard: String, onExecute: () -> Unit, onCancel: () -> Unit = {}) {
        hide()

        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(16), dp(8), dp(16), dp(8)) }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#E6141414"))
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
            layoutParams = lp
        }

        val label = TextView(context).apply {
            this.text = "\uD83C\uDFA4 \u041f\u043e\u0447\u0443\u0432: \"$heard\""
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(0, 0, 0, dp(10))
        }
        card.addView(label)

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val cancel = TextView(context).apply {
            this.text = "\u0421\u043a\u0430\u0441\u0443\u0432\u0430\u0442\u0438"
            setTextColor(Color.parseColor("#FF8A80"))
            textSize = 14f
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener {
                pendingExec?.let { handler.removeCallbacks(it) }
                pendingHide?.let { handler.removeCallbacks(it) }
                try { onCancel() } catch (_: Throwable) {}
                hide()
            }
        }

        val execute = TextView(context).apply {
            this.text = "\u0412\u0438\u043a\u043e\u043d\u0430\u0442\u0438"
            setTextColor(Color.parseColor("#64B5F6"))
            textSize = 14f
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener {
                pendingExec?.let { handler.removeCallbacks(it) }
                pendingHide?.let { handler.removeCallbacks(it) }
                try { onExecute() } catch (_: Throwable) {}
                hide()
            }
        }

        buttons.addView(cancel)
        buttons.addView(execute)
        card.addView(buttons)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = dp(80)
        }

        try {
            wm.addView(card, params)
            root = card

            val execRun = Runnable {
                pendingExec = null
                try { onExecute() } catch (_: Throwable) {}
                hide()
            }
            pendingExec = execRun
            handler.postDelayed(execRun, AUTO_EXEC_MS)

            val hideRun = Runnable { hide() }
            pendingHide = hideRun
            handler.postDelayed(hideRun, AUTO_HIDE_MS)
        } catch (t: Throwable) {
            Log.e(TAG, "addView failed: ${t.message}")
            try { onExecute() } catch (_: Throwable) {}
        }
    }

    fun hide() {
        pendingExec?.let { handler.removeCallbacks(it) }
        pendingHide?.let { handler.removeCallbacks(it) }
        pendingExec = null
        pendingHide = null
        root?.let {
            try { wm.removeView(it) } catch (_: Throwable) {}
        }
        root = null
    }

    private fun dp(v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()
}
