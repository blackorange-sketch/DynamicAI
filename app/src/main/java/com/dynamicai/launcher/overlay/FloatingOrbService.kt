package com.dynamicai.launcher.overlay

import com.dynamicai.launcher.Edition
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.MotionEvent
import com.dynamicai.launcher.music.MediaHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import android.widget.Toast
import android.view.Gravity
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.roundToInt
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
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.dynamicai.launcher.llm.ChatEngine
import com.dynamicai.launcher.ui.theme.StoredAppTheme
import com.dynamicai.launcher.voice.OfflineSpeech
import com.dynamicai.launcher.voice.TtsManager

class FloatingOrbService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    companion object {
        @Volatile var instance: FloatingOrbService? = null
    }
    /** Вікно додане через службу спеціальних можливостей (шар над статус-баром — дотики біля камери працюють). */
    private var overAccessibility = false
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
    
    private val scope = CoroutineScope(Dispatchers.Main)
    private var isThinking by mutableStateOf(false)
    private var mood by mutableStateOf(BuddyMood.IDLE)
    private var micLevel by mutableFloatStateOf(0f)
    private var lastActive = System.currentTimeMillis()
    private var moodJob: Job? = null
    /** Стиль: острівець угорі біля камери або плаваючий персонаж. */
    private var island = true
    /** Розгорнута панель острівця (сповіщення, музика, швидкі дії). */
    private var panelOpen by mutableStateOf(false)
    private var panelFocusable = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            if (level >= 0) IslandHub.setBattery(level * 100 / scale, charging)
        }
    }
    private lateinit var chatEngine: ChatEngine
    private lateinit var tts: TtsManager
    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        startForegroundNotification()

        chatEngine = com.dynamicai.launcher.AppListViewModel.getSharedChatEngine(applicationContext)
        tts = TtsManager(applicationContext)

        instance = this
        island = getSharedPreferences("floating_orb", Context.MODE_PRIVATE).getString("style", "island") == "island"
        createOverlay()
        if (island) {
            // Джерела острівця: батарея, музика, сповіщення, ліхтарик
            try { registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) } catch (_: Throwable) {}
            // Екран блокування і AOD: острівець ховається, панель закривається
            try {
                registerReceiver(screenReceiver, IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
                })
            } catch (_: Throwable) {}
            refreshLocked()
            MediaHub.ensureStarted(applicationContext)
            IslandHub.syncAll(applicationContext)
            // Нове сповіщення будить персонажа
            scope.launch { IslandHub.peek.collect { if (it != null) wake() } }
        }
        // Після 2 хв бездіяльності персонаж засинає (і майже не малює кадрів)
        scope.launch {
            while (isActive) {
                delay(15_000)
                if (mood == BuddyMood.IDLE && System.currentTimeMillis() - lastActive > 120_000) mood = BuddyMood.SLEEP
            }
        }
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    private fun createOverlay() {
        // Статус-бар лежить над звичайними overlay-вікнами і забирає дотики біля камери.
        // Вікно спецможливостей (TYPE_ACCESSIBILITY_OVERLAY) — вище за нього, тож острівець ставимо туди, якщо служба увімкнена.
        val acc = if (island) com.dynamicai.launcher.accessibility.AppAutomationService.instance else null
        overAccessibility = acc != null
        windowManager = (acc ?: this).getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = when {
            overAccessibility -> WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else -> WindowManager.LayoutParams.TYPE_PHONE
        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Початкова позиція (правий нижній кут)
        currentX = screenWidth - 150f
        currentY = screenHeight - 400f

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = currentX.toInt()
            y = currentY.toInt()
        }
        if (island) placeIsland()

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOrbService)
            setViewTreeViewModelStoreOwner(this@FloatingOrbService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOrbService)
            setContent {
                StoredAppTheme {
                    var isExpanded by remember { mutableStateOf(false) }
                    // «Визирання» острівця гасне саме
                    val peek by IslandHub.peek.collectAsState()
                    LaunchedEffect(peek?.id) {
                        val p = peek ?: return@LaunchedEffect
                        delay(4500)
                        IslandHub.clearPeek(p.id)
                    }
                    if (island && !isExpanded) {
                        if (panelOpen) {
                            IslandPanel(
                                mood = mood, micLevel = { micLevel },
                                onClose = { setPanel(false) },
                                onOpenChat = {
                                    setPanel(false)
                                    isExpanded = true
                                    updateWindowSize(true)
                                },
                                onVoice = { setPanel(false); startListening() },
                                onReplyFocus = { setPanelFocusable(it) }
                            )
                        } else {
                            IslandCompact(
                                mood = mood, micLevel = { micLevel },
                                onTap = { wake(); setPanel(true) },
                                onLongPress = { startListening() },
                                onSwipeDown = { wake(); setPanel(true) },
                                onWindowSize = { w, h -> setIslandWindow(w.value, h.value) }
                            )
                        }
                    } else FloatingOrb(
                        isExpanded = isExpanded,
                        messages = ChatHistoryManager.history.map { it.role to it.content },
                        isThinking = isThinking,
                        mood = mood,
                        micLevel = { micLevel },
                        island = false,
                        onExpandChange = {
                            wake()
                            isExpanded = it
                            updateWindowSize(it)
                        },
                        onDrag = { dx, dy ->
                            val displayMetrics = resources.displayMetrics
                            val screenWidth = displayMetrics.widthPixels
                            val screenHeight = displayMetrics.heightPixels
                        
                            currentX += dx
                            currentY += dy
                        
                            // Обмеження, щоб не вилітало за екран
                            currentX = currentX.coerceIn(0f, screenWidth - 150f)
                            currentY = currentY.coerceIn(0f, screenHeight - 150f)
                        
                            params.x = currentX.roundToInt()
                            params.y = currentY.roundToInt()
                            windowManager.updateViewLayout(composeView, params)
                        },
                        onSendMessage = { text -> processQuery(text) },
                        onMicClick = { startListening() }
                    )
                }
            }
        }

        // Повноекранна програма ховає статус-бар — ховаємо й острівець
        composeView?.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val fs = !insets.isVisible(android.view.WindowInsets.Type.statusBars())
                if (fs != statusBarHidden) { statusBarHidden = fs; updateHidden() }
            }
            v.onApplyWindowInsets(insets)
        }
        landscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        updateHidden()
        // Дотик поза панеллю закриває її
        composeView?.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE && panelOpen) setPanel(false)
            false
        }
        windowManager.addView(composeView, params)
    }

    /** Перебудувати вікно (увімкнули/вимкнули службу спецможливостей). */
    fun rebuildOverlay() {
        if (!island) return
        val old = composeView
        try { old?.let { windowManager.removeViewImmediate(it) } } catch (_: Throwable) {}
        composeView = null
        panelOpen = false
        panelFocusable = false
        try { createOverlay() } catch (t: Throwable) { android.util.Log.e("FloatingOrb", "rebuild failed: ${t.message}") }
    }

    private fun setPanel(open: Boolean) {
        if (open) { refreshLocked(); if (lockedNow) return }   // не відкривати на екрані блокування
        if (panelOpen == open) return
        panelOpen = open
        panelFocusable = false
        if (open) {
            // На весь екран: анімація панелі не змінює розмір вікна (без ривків), дотик по затемненню закриває
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.gravity = Gravity.TOP
            params.x = 0
            params.y = 0
            applyPanelFlags()
        } else {
            placeIsland()
        }
        try { windowManager.updateViewLayout(composeView, params) } catch (_: Throwable) {}
        updateHidden()
    }

    private fun applyPanelFlags() {
        var f = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!panelFocusable) f = f or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        params.flags = f
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }

    /** Поле відповіді на сповіщення потребує клавіатури — вікно тимчасово стає фокусованим. */
    private fun setPanelFocusable(focusable: Boolean) {
        if (!panelOpen || panelFocusable == focusable) return
        panelFocusable = focusable
        applyPanelFlags()
        try { windowManager.updateViewLayout(composeView, params) } catch (_: Throwable) {}
    }

    private fun updateWindowSize(expanded: Boolean) {
        if (expanded) {
            // При розгортанні центруємо вікно
            params.gravity = Gravity.CENTER
            params.x = 0
            params.y = 0
            // Робимо вікно фокусованим, щоб працювала клавіатура
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            // При згортанні повертаємо на місце
            params.gravity = Gravity.TOP or Gravity.START
            params.x = currentX.roundToInt()
            params.y = currentY.roundToInt()
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            if (island) placeIsland()
        }
        windowManager.updateViewLayout(composeView, params)
    }

    /** Острівець: по центру вгорі, у смузі статус-бару навколо камери. */
    private fun placeIsland() {
        val density = resources.displayMetrics.density
        val sbId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (sbId > 0) resources.getDimensionPixelSize(sbId) else (24 * density).toInt()
        val islandH = (ISLAND_H.value * density).toInt()
        params.width = islandWpx.takeIf { it > 0 } ?: (108 * density).toInt()
        params.height = islandHpx.takeIf { it > 0 } ?: islandH
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.x = 0
        // Без спецможливостей статус-бар перехоплює дотики — ставимо острівець одразу під ним
        params.y = if (overAccessibility) ((statusBar - islandH) / 2).coerceAtLeast(0) else statusBar + (2 * density).toInt()
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (hiddenNow) params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    }

    private var statusBarHidden = false
    /** Екран вимкнено (AOD) або заблоковано — острівця не видно. */
    private var lockedNow = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            refreshLocked()
            if (lockedNow && panelOpen) setPanel(false)
            updateHidden()
        }
    }

    private fun refreshLocked() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        lockedNow = !pm.isInteractive || km.isKeyguardLocked
    }
    private var landscape = false
    private var hiddenNow = false

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        landscape = newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        updateHidden()
    }

    /** Острівець зникає в горизонтальному і повноекранному режимі (ігри, відео), коли панель закрита. */
    private fun updateHidden() {
        val v = composeView ?: return
        val hide = island && ((!panelOpen && params.width != WindowManager.LayoutParams.WRAP_CONTENT && (landscape || statusBarHidden)) || lockedNow)
        if (hide == hiddenNow) return
        hiddenNow = hide
        v.animate().alpha(if (hide) 0f else 1f).setDuration(160).start()
        params.flags = if (hide) params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            else params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        try { windowManager.updateViewLayout(v, params) } catch (_: Throwable) {}
    }

    private var islandWpx = 0
    private var islandHpx = 0

    /** Розмір вікна компактного острівця (dp). Викликається двічі на перехід: перед анімацією і після неї. */
    private fun setIslandWindow(wDp: Float, hDp: Float) {
        val d = resources.displayMetrics.density
        val w = (wDp * d).toInt(); val h = (hDp * d).toInt()
        if (w == islandWpx && h == islandHpx) return
        islandWpx = w; islandHpx = h
        if (panelOpen || !island) return
        params.width = w
        params.height = h
        try { windowManager.updateViewLayout(composeView, params) } catch (_: Throwable) {}
    }

    /** Будь-яка взаємодія будить персонажа. */
    private fun wake() {
        lastActive = System.currentTimeMillis()
        if (mood == BuddyMood.SLEEP) mood = BuddyMood.IDLE
    }

    /** Показати настрій на час, потім повернутись у спокій. */
    private fun flash(m: BuddyMood, ms: Long) {
        moodJob?.cancel()
        mood = m
        moodJob = scope.launch {
            delay(ms)
            if (mood == m) mood = BuddyMood.IDLE
        }
    }

    /** Відповідь готова: стрибок радості, потім «говорить», поки звучить озвучка. */
    private fun deliver(answer: String) {
        moodJob?.cancel()
        mood = BuddyMood.HAPPY
        moodJob = scope.launch {
            delay(900)
            mood = BuddyMood.SPEAK
            tts.speakWithCallback(answer) {
                scope.launch { if (mood == BuddyMood.SPEAK) mood = BuddyMood.IDLE; lastActive = System.currentTimeMillis() }
            }
        }
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


    private fun processQuery(text: String) {
        wake()
        ChatHistoryManager.addMessage("user", text)
        isThinking = true
        moodJob?.cancel()
        mood = BuddyMood.THINK
        scope.launch {
            try {
                val history = ChatHistoryManager.history.takeLast(10).joinToString("\n") {
                    if (it.role == "user") "Користувач: ${it.content}" else "Асистент: ${it.content}"
                }
                // Спершу хмарний агент (не потребує локальної моделі), потім локальний
                var answer: String? = null
                if (chatEngine.cloudAgentEligible(text)) {
                    val r = chatEngine.cloudAgent(text, history)
                    if (r != ChatEngine.CLOUD_FAILED) answer = r
                }
                if (answer == null && Edition.CLOUD_ONLY) {
                    val r = chatEngine.simpleAnswer(text, history = history)
                    answer = if (r == ChatEngine.CLOUD_FAILED) "Хмара недоступна. Перевірте інтернет і ключ." else r
                }
                // Локальна модель не завантажена — пробуємо хмару
                if (answer == null && !com.dynamicai.launcher.AppListViewModel.getSharedLlm(applicationContext).isReady) {
                    val r = chatEngine.cloudFallback(text, history)
                    if (r != ChatEngine.CLOUD_FAILED) answer = r
                }
                if (answer == null) {
                    if (!com.dynamicai.launcher.AppListViewModel.getSharedLlm(applicationContext).isReady) {
                        val err = "Локальна модель не завантажена, а хмара недоступна (перевірте ключ і інтернет)."
                        ChatHistoryManager.addMessage("assistant", err)
                        isThinking = false
                        flash(BuddyMood.ERROR, 2200)
                        tts.speak(err)
                        return@launch
                    }
                    answer = chatEngine.answerWithAgent(text, history)
                }
                ChatHistoryManager.addMessage("assistant", answer)
                isThinking = false
                deliver(answer)
            } catch (e: Exception) {
                ChatHistoryManager.addMessage("assistant", "Помилка: ${e.message}")
                isThinking = false
                flash(BuddyMood.ERROR, 2200)
            }
        }
    }

    private fun startListening() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Потрібен дозвіл на мікрофон! Надайте його в налаштуваннях.", Toast.LENGTH_LONG).show()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        wake()
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    moodJob?.cancel()
                    mood = BuddyMood.LISTEN
                }
                override fun onBeginningOfSpeech() {
                    if (tts.isReady) tts.stop()
                }
                override fun onRmsChanged(rmsdB: Float) {
                    // rmsdB зазвичай −2…10 — нормуємо в 0…1 для очей і аури
                    micLevel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    micLevel = 0f
                    mood = BuddyMood.THINK
                }
                override fun onError(error: Int) {
                    isThinking = false
                    micLevel = 0f
                    flash(BuddyMood.ERROR, 1500)
                }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                    if (text.isNotBlank()) processQuery(text) else mood = BuddyMood.IDLE
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, OfflineSpeech.preferOffline(this@FloatingOrbService))
        }
        speechRecognizer?.startListening(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        if (island) try { unregisterReceiver(batteryReceiver) } catch (_: Throwable) {}
        if (island) try { unregisterReceiver(screenReceiver) } catch (_: Throwable) {}
        tts.shutdown()
        instance = null
        try { composeView?.let { windowManager.removeView(it) } } catch (_: Throwable) {}
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }


    override fun onBind(intent: Intent?): IBinder? = null
}
