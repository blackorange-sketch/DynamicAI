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
import com.dynamicai.launcher.folder.FolderStorage
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
    private val folderStorage by lazy { FolderStorage(applicationContext) }

    @Volatile private var apps: List<AppInfo> = emptyList()
    private var searchOverlayView: android.view.View? = null
    private var searchOverlayParams: WindowManager.LayoutParams? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val commandParser by lazy { com.dynamicai.launcher.command.CommandParser() }
    private val llmEngine by lazy { com.dynamicai.launcher.llm.LiteRtLlmEngine(applicationContext) }
    private val modelDownloader by lazy { com.dynamicai.launcher.llm.ModelDownloader(applicationContext) }

    private val commandExecutor by lazy { com.dynamicai.launcher.command.CommandExecutor(applicationContext) }

    private val dockPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "apps") {
            try {
                rebuildContent()
                log("dock updated")
            } catch (t: Throwable) {
                log("dock update failed: ${t.message}")
            }
        }
    }
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
        isRunning = true
        log("onCreate start")
        super.onCreate()
        try {
            settings = settingsStorage.load()
            applicationContext
                .getSharedPreferences("dynamicai_settings", Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(prefsListener)

            instance = this
            try {
            applicationContext.getSharedPreferences("dynamicai_dock", Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(dockPrefsListener)
        } catch (_: Throwable) {}

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
        isRunning = false
        log("onDestroy")
        try {
            applicationContext
                .getSharedPreferences("dynamicai_settings", Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(prefsListener)
            try {
                applicationContext.getSharedPreferences("dynamicai_dock", Context.MODE_PRIVATE)
                    .unregisterOnSharedPreferenceChangeListener(dockPrefsListener)
            } catch (_: Throwable) {}
        } catch (_: Exception) {}
        try { hideSearchOverlay() } catch (_: Exception) {}
        try { if (::rootLayout.isInitialized) windowManager.removeView(rootLayout) } catch (_: Exception) {}
        if (instance === this) instance = null
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


    private fun launchSearchActivity() {
        try {
            val i = Intent(this, com.dynamicai.launcher.SearchOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(i)
        } catch (t: Throwable) {
            log("launchSearchActivity failed: ${t.message}")
        }
    }

    private fun showSearchOverlay() {
        if (searchOverlayView != null) return
        try {
            // ===== Overlay =====
            val overlay = android.widget.FrameLayout(this).apply {
                setBackgroundColor(0x66000000.toInt())
                isFocusableInTouchMode = true
                isFocusable = true
            }

            // Закриття по тапу
            overlay.setOnClickListener { hideSearchOverlay() }

            // Back
            overlay.setOnKeyListener { _, keyCode, event ->
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK &&
                    event.action == android.view.KeyEvent.ACTION_UP) {
                    hideSearchOverlay()
                    true
                } else false
            }

            // ===== Контейнер (зверху) =====
            val container = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(dp(12), dp(60), dp(12), dp(12))
                isClickable = false
            }

            // ===== Поле введення =====
            val editText = android.widget.EditText(this).apply {
                hint = "Пошук або команда…"
                setHintTextColor(0x80FFFFFF.toInt())
                setTextColor(android.graphics.Color.WHITE)
                textSize = 17f
                setSingleLine(true)
                setPadding(dp(20), dp(14), dp(20), dp(14))
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(0xF01F1F1F.toInt(), 0xE00D0D0D.toInt())
                ).apply {
                    cornerRadius = dp(24).toFloat()
                    setStroke(dp(1), 0x44FFFFFF)
                }
            }
            container.addView(editText)

            // ===== Підказки =====
            val resultsList = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(0, dp(8), 0, 0)
            }
            container.addView(resultsList)

            // ===== LayoutParams — container притиснутий догори =====
            val containerParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.TOP
            }

            overlay.addView(container, containerParams)

            // ===== Логіка підказок =====
            editText.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val query = s?.toString().orEmpty().trim()
                    resultsList.removeAllViews()
                    if (query.isBlank()) return
                    val lower = query.lowercase()

                    com.dynamicai.launcher.suggestion.ActionCatalog.all
                        .map { a -> a to a.keywords.maxOf { kw -> com.dynamicai.launcher.command.FuzzyMatcher.score(kw, lower) } }
                        .filter { it.second >= 55 }
                        .sortedByDescending { it.second }
                        .take(3)
                        .forEach { (a, _) ->
                            resultsList.addView(makeResultRow("⚡", a.label) {
                                commandExecutor.execute(a.build(), apps)
                                hideSearchOverlay()
                            })
                        }

                    com.dynamicai.launcher.command.SettingsRoutes.all().keys
                        .map { it to com.dynamicai.launcher.command.FuzzyMatcher.score(it, lower) }
                        .filter { it.second >= 60 }
                        .sortedByDescending { it.second }
                        .take(2)
                        .forEach { (route, _) ->
                            resultsList.addView(makeResultRow("⚙", "Налаштування: $route") {
                                commandExecutor.execute(
                                    com.dynamicai.launcher.command.Command.OpenSettings(route),
                                    apps
                                )
                                hideSearchOverlay()
                            })
                        }

                    apps.filter { it.label.contains(query, ignoreCase = true) }
                        .take(6)
                        .forEach { app ->
                            resultsList.addView(makeResultRow("📱", app.label) {
                                packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    try { startActivity(this) } catch (_: Exception) {}
                                }
                                hideSearchOverlay()
                            })
                        }
                }
            })

            // ===== Enter =====
            editText.setOnEditorActionListener { _, actionId, event ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN)) {
                    val text = editText.text.toString().trim()
                    if (text.isNotBlank()) {
                        val cmd = commandParser.parse(text)
                        val result = commandExecutor.execute(cmd, apps)
                        if (result is com.dynamicai.launcher.command.CommandResult.Success) {
                            hideSearchOverlay()
                        } else if (result is com.dynamicai.launcher.command.CommandResult.Error) {
                            android.widget.Toast.makeText(
                                this@FloatingDockService, result.message,
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    true
                } else false
            }

            // ===== WindowManager =====
            val type = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                dimAmount = 0.5f
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }

            windowManager.addView(overlay, params)
            searchOverlayView = overlay
            searchOverlayParams = params

            // Фокус + клавіатура
            mainHandler.postDelayed({
                try {
                    overlay.requestFocus()
                    editText.requestFocus()
                    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                } catch (_: Throwable) {}
            }, 100)
        } catch (t: Throwable) {
            log("showSearchOverlay failed: ${t.message}")
        }
    }

    private fun makeResultRow(emoji: String, label: String, onClick: () -> Unit): android.view.View {
        val row = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            setOnClickListener { onClick() }

            // Glass-фон
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xE0202020.toInt(), 0xD00D0D0D.toInt())
            ).apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), 0x33FFFFFF)
            }

            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(4), 0, dp(4))
            }
        }

        // Emoji-іконка
        row.addView(android.widget.TextView(this).apply {
            text = emoji
            textSize = 20f
            setPadding(0, 0, dp(14), 0)
        })

        // Текст
        row.addView(android.widget.TextView(this).apply {
            text = label
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })

        return row
    }

    private fun hideSearchOverlay() {
        try {
            searchOverlayView?.let { windowManager.removeView(it) }
        } catch (_: Throwable) {}
        searchOverlayView = null
        searchOverlayParams = null
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
            alpha = 0.15f
            elevation = dp(2).toFloat()
        }

        // Обгортка з підкладкою glass
        val wrapper = FrameLayout(this).apply {
            setPadding(dp(4), dp(8), dp(settings.overlayEdgePaddingDp), dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                expanded = true
                rebuildContent()
            }
        }

        // Glass-підкладка навколо риски
        val handleCard = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xE61F1F1F.toInt(), 0xCC0D0D0D.toInt())
            ).apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), 0x44FFFFFF.toInt())
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            addView(shadow)
            addView(handle)
        }

        wrapper.addView(handleCard)
        return wrapper
    }

    private fun buildExpanded(): View {
        // Внутрішня колонка з іконками
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
            gravity = Gravity.CENTER_HORIZONTAL
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
            launchSearchActivity()
        })

        column.addView(spacer(dp(8)))

        // Іконки з дока — і застосунки, і папки
        val dockPkgs = dockStorage.getDockApps()
        val folders = folderStorage.get()
        var rendered = 0
        val maxItems = 8

        for (pkg in dockPkgs) {
            if (rendered >= maxItems) break
            if (pkg.startsWith("folder:")) {
                val fid = pkg.removePrefix("folder:")
                val folder = folders.firstOrNull { it.id == fid } ?: continue
                val firstApp = apps.firstOrNull { it.packageName in folder.packageNames }
                if (firstApp != null) {
                    column.addView(makeFolderIcon(folder.name, firstApp))
                    column.addView(spacer(dp(8)))
                    rendered++
                }
            } else {
                val app = apps.firstOrNull { it.packageName == pkg } ?: continue
                column.addView(makeAppIcon(app))
                column.addView(spacer(dp(8)))
                rendered++
            }
        }

        // Обгортка з підкладкою (темна напівпрозора, заокруглена)
        val wrapper = FrameLayout(this).apply {
            setPadding(dp(settings.overlayEdgePaddingDp), dp(8), 0, dp(8))
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xE61F1F1F.toInt(), 0xCC0D0D0D.toInt())
            ).apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(26).toFloat()
                setStroke(dp(1), 0x44FFFFFF.toInt())
            }
            addView(column)
        }

        wrapper.addView(card)
        return wrapper
    }

    private fun spacer(px: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, px)
    }


    private fun launchScreenTranslate() {
        try {
            expanded = false
            rebuildContent()

            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                try {
                    val app = applicationContext
                    // Завантажуємо LLM якщо треба
                    if (!llmEngine.isReady && modelDownloader.isDownloaded) {
                        try { llmEngine.initialize(modelDownloader.modelFile) } catch (_: Throwable) {}
                    }
                    val translator = com.dynamicai.launcher.ocr.ScreenTranslator(app, llmEngine)
                    val blocks = translator.captureAndTranslate(
                        ocrLangs = "ukr+eng",
                        targetLang = "en"
                    )
                    if (blocks.isEmpty()) {
                        android.widget.Toast.makeText(
                            app,
                            "Не вдалось перекласти (перевір OCR-мови)",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        com.dynamicai.launcher.ocr.ScreenTranslateOverlay.show(
                            context = app,
                            blocks = blocks,
                            targetLang = "en"
                        )
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("FloatingDock", "translate failed: ${t.message}", t)
                }
            }
        } catch (t: Throwable) {
            log("launchScreenTranslate failed: ${t.message}")
        }
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

    private fun makeFolderIcon(name: String, firstApp: AppInfo): View {
        val size = settings.overlayIconSizeDp
        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(size + 8), dp(size + 8))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x14FFFFFF, 0x05FFFFFF)
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(1), 0x55FFFFFF.toInt())
            }
            isClickable = true
            setOnClickListener {
                // Відкриваємо лаунчер із папкою
                val intent = Intent(this@FloatingDockService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                try { startActivity(intent) } catch (_: Exception) {}
            }
        }
        val icon = ImageView(this).apply {
            val bmp = firstApp.bitmap
                ?: firstApp.icon.toBitmap(dp(size * 7 / 10), dp(size * 7 / 10))
            setImageBitmap(bmp)
            layoutParams = FrameLayout.LayoutParams(dp(size * 7 / 10), dp(size * 7 / 10)).apply {
                gravity = Gravity.CENTER
            }
        }
        frame.addView(icon)
        return frame
    }

    private fun makeAppIcon(app: AppInfo): View {
        val size = settings.overlayIconSizeDp
        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(size + 8), dp(size + 8))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x14FFFFFF, 0x05FFFFFF)
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(1), 0x33FFFFFF)
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
            val bmp = app.bitmap
                ?: app.icon.toBitmap(dp(size), dp(size))
            setImageBitmap(bmp)
            layoutParams = FrameLayout.LayoutParams(dp(size), dp(size)).apply {
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
        @Volatile var isRunning: Boolean = false

        const val NOTIF_ID = 1001

        @Volatile var lastLog: String = "—"

        fun log(msg: String) {
            lastLog = msg
            Log.d("FloatingDock", msg)
        }

        @Volatile private var instance: FloatingDockService? = null

        fun hide(ctx: Context) {
            instance?.rootLayout?.visibility = View.GONE
        }

        fun show(ctx: Context) {
            instance?.rootLayout?.visibility = View.VISIBLE
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
