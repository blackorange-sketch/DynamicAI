package com.dynamicai.launcher.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dynamicai.launcher.MainActivity
import com.dynamicai.launcher.data.DockStorage
import com.dynamicai.launcher.data.SettingsStorage
import com.dynamicai.launcher.data.LauncherSettings
import com.dynamicai.launcher.model.AppInfo
import com.dynamicai.launcher.data.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingDockService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var dockView: ComposeView
    private lateinit var params: WindowManager.LayoutParams
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repo by lazy { AppRepository(applicationContext) }
    private val dockStorage by lazy { DockStorage(applicationContext) }
    private val settingsStorage by lazy { SettingsStorage(applicationContext) }
    private val settingsState = mutableStateOf(LauncherSettings())
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "updatedAt") {
            val new = settingsStorage.load()
            settingsState.value = new
            updateLayoutParams(new)
        }
    }
    private val allApps = mutableStateOf<List<AppInfo>>(emptyList())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        log("onCreate start")
        super.onCreate()
        try {
            savedStateController.performAttach()
            savedStateController.performRestore(android.os.Bundle())
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED

            val loaded = settingsStorage.load()
            log("Loaded settings: $loaded")
            settingsState.value = loaded
            applicationContext.getSharedPreferences("dynamicai_settings", Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(prefsListener)

            startForeground(NOTIF_ID, buildNotification())
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            attachDock()

            scope.launch {
                try {
                    allApps.value = repo.loadInstalledApps()
                } catch (t: Throwable) {
                    android.util.Log.e("FloatingDock", "loadApps failed", t)
                }
            }
        } catch (t: Throwable) {
            log("onCreate failed: ${t.message}")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        log("onDestroy")
        try {
            applicationContext.getSharedPreferences("dynamicai_settings", Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (_: Exception) {}
        try { if (::dockView.isInitialized) windowManager.removeView(dockView) } catch (_: Exception) {}
        scope.cancel()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    private fun attachDock() {
        dockView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingDockService)
            setViewTreeSavedStateRegistryOwner(this@FloatingDockService)
            val settings = settingsState.value
            setContent {
                val s = settingsState.value
                DockOverlayContent(
                    appsFlow = { allApps.value },
                    getDockPkgs = { dockStorage.getDockApps() },
                    iconSizeDp = s.overlayIconSizeDp,
                    handleLengthDp = s.overlayHandleLengthDp,
                    handleAlphaPercent = s.overlayHandleAlphaPercent,
                    onLaunch = { pkg ->
                        packageManager.getLaunchIntentForPackage(pkg)?.apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try { startActivity(this) } catch (_: Exception) {}
                        }
                    },
                    onOpenSearch = {
                        startActivity(Intent(this@FloatingDockService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        })
                    }
                )
            }
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            applyPosition(settingsStorage.load())
        }

        log("addView: type=${params.type} flags=${params.flags} gravity=${params.gravity} x=${params.x} y=${params.y}")
        windowManager.addView(dockView, params)
    }

    private fun applyPosition(s: LauncherSettings) {
        val horizontalGravity = if (s.overlayPosition == "left") Gravity.START else Gravity.END
        params.gravity = Gravity.CENTER_VERTICAL or horizontalGravity
        params.x = 0
        // Зміщення від центру: -400..+400 px (безпечний діапазон)
        val pct = s.overlayOffsetYPercent.coerceIn(0, 100)
        params.y = ((pct - 50) * 8).coerceIn(-400, 400)
        log("applyPosition: pct=$pct y=${params.y} gravity=${params.gravity}")
    }

    private fun updateLayoutParams(s: LauncherSettings) {
        if (!::params.isInitialized) return
        try {
            applyPosition(s)
            if (::dockView.isInitialized) windowManager.updateViewLayout(dockView, params)
        } catch (t: Throwable) {
            log("updateLayoutParams: ${t.message}")
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "dynamicai_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(channelId, "DynamicAI Dock", NotificationManager.IMPORTANCE_MIN)
                )
            }
        }
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, channelId)
            .setContentTitle("DynamicAI Dock активний")
            .setContentText("Натисни, щоб відкрити лаунчер")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val NOTIF_ID = 1001
        @Volatile var lastLog: String = "—"
        fun log(msg: String) {
            lastLog = msg
            log(msg)
        }
        fun start(ctx: Context) {
            log("start() called")
            try {
                val i = Intent(ctx, FloatingDockService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    log("calling startForegroundService")
                    ctx.startForegroundService(i)
                } else {
                    log("calling startService")
                    ctx.startService(i)
                }
            } catch (t: Throwable) {
                log("start() failed: ${t.message}")
            }
        }
        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, FloatingDockService::class.java))
        }
    }
}
