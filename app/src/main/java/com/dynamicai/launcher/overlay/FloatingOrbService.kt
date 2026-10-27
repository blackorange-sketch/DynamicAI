package com.dynamicai.launcher.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class FloatingOrbService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private lateinit var params: WindowManager.LayoutParams
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var currentX = 0f
    private var currentY = 0f

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        startForegroundNotification()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createOverlay()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    private fun createOverlay() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 50
            y = 200
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOrbService)
            setViewTreeViewModelStoreOwner(this@FloatingOrbService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOrbService)
            setContent {
                var isExpanded by remember { mutableStateOf(false) }
                FloatingOrb(
                    isExpanded = isExpanded,
                    onExpandChange = {
                        isExpanded = it
                        updateWindowSize(it)
                    },
                    onDrag = { dx, dy ->
                        currentX -= dx
                        currentY += dy
                        params.x = currentX.roundToInt()
                        params.y = currentY.roundToInt()
                        windowManager.updateViewLayout(composeView, params)
                    }
                )
            }
        }

        windowManager.addView(composeView, params)
    }

    private fun updateWindowSize(expanded: Boolean) {
        params.width = if (expanded) WindowManager.LayoutParams.WRAP_CONTENT else WindowManager.LayoutParams.WRAP_CONTENT
        windowManager.updateViewLayout(composeView, params)
    }

    private fun startForegroundNotification() {
        val channelId = "orb_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "JARVIS Floating Orb", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            Notification.Builder(this)
        }.setContentTitle("JARVIS активний")
         .setContentText("Плаваюча куля готова до команд")
         .setSmallIcon(android.R.drawable.ic_btn_speak_now)
         .build()
        
        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        composeView?.let { windowManager.removeView(it) }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
