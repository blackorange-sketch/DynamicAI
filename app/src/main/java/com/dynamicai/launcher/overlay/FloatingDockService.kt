package com.dynamicai.launcher.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.graphics.drawable.toBitmap
import com.dynamicai.launcher.MainActivity
import com.dynamicai.launcher.data.AppRepository
import com.dynamicai.launcher.data.DockStorage
import com.dynamicai.launcher.data.LauncherSettings
import com.dynamicai.launcher.data.SettingsStorage
import com.dynamicai.launcher.model.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingDockService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var rootLayout: FrameLayout
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var settings: LauncherSettings

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repo by lazy { AppRepository(applicationContext) }
    private val dockStorage by lazy { DockStorage(applicationContext) }
    private val settingsStorage by lazy { SettingsStorage(applicationContext) }

    @Volatile private var apps: List<AppInfo> = emptyList()
    @Volatile private var expanded = false

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "updatedAt") {
            try {
                settings = settingsStorage.load()
                rebuildContent()
                applyPosition()
                windowManager.updateViewLayout(rootLayout, params)
                log("prefs updated")
            } catch (t: Throwable) {
                log("prefs update failed: ${t.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        log("onCreate start")
        super.onCreate()
        try {
            settings = settingsStorage.load()
            applicationContext
                .getSharedPreferences("dynamicai_settings", Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(prefsListener)

            startForeground(NOTIF_ID, buildNotification())
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            attachDock()
            log("onCreate done")

            scope.launch {
                try {
                    apps = repo.loadInstalledApps()
                    rebuildContent()
                    log("apps loaded: ${apps.size}")
                } catch (t: Throwable) {
                    log("load apps: ${t.message}")
                }
            }
        } catch (t: Throwable) {
            log("onCreate failed: ${t::class.simpleName} — ${t.message}")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("onStartCommand")
        return START_STICKY
    }

    override fun onDestroy() {
        log("onDestroy")
        try {
            applicationContext
                .getSharedPreferences("dynamicai_settings", Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (_: Exception) {}
        try { if (::rootLayout.isInitialized) windowManager.removeView(rootLayout) } catch (_: Exception) {}
        scope.cancel()
        super.onDestroy()
    }

    // ==== UI ====

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun attachDock() {
        rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
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
        )
        applyPosition()

        rebuildContent()
        windowManager.addView(rootLayout, params)
        log("addView done, gravity=${params.gravity} y=${params.y}")
    }

    private fun applyPosition() {
        val horizontal = if (settings.overlayPosition == "left") Gravity.START else Gravity.END
        params.gravity = Gravity.CENTER_VERTICAL or horizontal
        params.x = 0
        val pct = settings.overlayOffsetYPercent.coerceIn(0, 100)
        params.y = ((pct - 50) * 8).coerceIn(-400, 400)
    }

    private fun rebuildContent() {
        if (!::rootLayout.isInitialized) return
        rootLayout.removeAllViews()
        if (expanded) rootLayout.addView(buildExpanded()) else rootLayout.addView(buildHandle())
    }

    private fun buildHandle(): View {
        val visualWidth = dp(3)
        val visualHeight = dp(settings.overlayHandleLengthDp)

        // Сама риска з тінню
        val handle = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(visualWidth, visualHeight).apply {
                gravity = Gravity.CENTER
            }
            background = GradientDrawable().apply {
                cornerRadius = visualWidth / 2f
                setColor(Color.WHITE)
            }
            alpha = settings.overlayHandleAlphaPercent / 100f
            elevation = dp(4).toFloat()
            // Тінь через elevation на Android 5+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, visualWidth / 2f)
                    }
                }
                clipToOutline = false
            }
        }

        // Тіньовий шар — трохи ширший за риску, напівпрозорий чорний
        val shadow = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                visualWidth + dp(4),
                visualHeight + dp(4)
            ).apply {
                gravity = Gravity.CENTER
            }
            background = GradientDrawable().apply {
                cornerRadius = (visualWidth + dp(4)) / 2f
                setColor(Color.BLACK)
            }
            alpha = 0.25f
            elevation = dp(6).toFloat()
        }

        // Обгортка: більша площа натискання
        val wrapper = FrameLayout(this).apply {
            setPadding(dp(2), dp(8), dp(settings.overlayEdgePaddingDp), dp(8))
            addView(shadow)
            addView(handle)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                expanded = true
                rebuildContent()
            }
        }
        return wrapper
    }

    private fun buildExpanded(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(settings.overlayEdgePaddingDp), dp(4))
        }

        // Кнопка закриття
        column.addView(makeCircleButton(
            size = settings.overlayIconSizeDp,
            bgColor = 0x33202020,
            borderColor = 0x59FFFFFF.toInt(),
            iconRes = android.R.drawable.ic_menu_close_clear_cancel,
            iconTint = Color.WHITE
        ) {
            expanded = false
            rebuildContent()
        })

        column.addView(spacer(dp(8)))

        // Кнопка пошуку
        column.addView(makeCircleButton(
            size = settings.overlayIconSizeDp,
            bgColor = 0x402196F3.toInt(),
            borderColor = 0x9964B5F6.toInt(),
            iconRes = android.R.drawable.ic_menu_search,
            iconTint = Color.parseColor("#64B5F6")
        ) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
        })

        column.addView(spacer(dp(8)))

        // Іконки застосунків з дока
        val dockPkgs = dockStorage.getDockApps()
        apps.filter { it.packageName in dockPkgs }.take(6).forEach { app ->
            column.addView(makeAppIcon(app))
            column.addView(spacer(dp(8)))
        }

        return column
    }

    private fun spacer(px: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, px)
    }

    private fun makeCircleButton(
        size: Int,
        bgColor: Int,
        borderColor: Int,
        iconRes: Int,
        iconTint: Int,
        onTap: () -> Unit
    ): View {
        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
                setStroke(dp(1), borderColor)
            }
            isClickable = true
            setOnClickListener { onTap() }
        }
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(iconTint)
            layoutParams = FrameLayout.LayoutParams(dp(size / 2), dp(size / 2)).apply {
                gravity = Gravity.CENTER
            }
        }
        frame.addView(icon)
        return frame
    }

    private fun makeAppIcon(app: AppInfo): View {
        val size = settings.overlayIconSizeDp
        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#EFEFEF"))
                setStroke(dp(1), Color.WHITE)
            }
            isClickable = true
            setOnClickListener {
                packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { startActivity(this) } catch (_: Exception) {}
                }
            }
        }
        val icon = ImageView(this).apply {
            setImageBitmap(app.icon.toBitmap(dp(size), dp(size)))
            layoutParams = FrameLayout.LayoutParams(dp(size - 8), dp(size - 8)).apply {
                gravity = Gravity.CENTER
            }
        }
        frame.addView(icon)
        return frame
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
            Log.d("FloatingDock", msg)
        }

        fun start(ctx: Context) {
            try {
                val i = Intent(ctx, FloatingDockService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
                else ctx.startService(i)
                log("start() called")
            } catch (t: Throwable) {
                log("start() failed: ${t::class.simpleName} — ${t.message}")
            }
        }

        fun stop(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, FloatingDockService::class.java))
                log("stop() called")
            } catch (t: Throwable) {
                log("stop() failed: ${t.message}")
            }
        }
    }
}
