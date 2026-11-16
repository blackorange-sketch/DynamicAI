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

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        island = getSharedPreferences("floating_orb", Context.MODE_PRIVATE).getString("style", "island") == "island"
        createOverlay()
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
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
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
                    FloatingOrb(
                        isExpanded = isExpanded,
                        messages = ChatHistoryManager.history.map { it.role to it.content },
                        isThinking = isThinking,
                        mood = mood,
                        micLevel = { micLevel },
                        island = island,
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

        windowManager.addView(composeView, params)
    }

    private fun updateWindowSize(expanded: Boolean) {
        if (expanded) {
            // При розгортанні центруємо вікно
            params.gravity = Gravity.CENTER
            params.x = 0
            params.y = 0
            // Робимо вікно фокусованим, щоб працювала клавіатура
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            // При згортанні повертаємо на місце
            params.gravity = Gravity.TOP or Gravity.START
            params.x = currentX.roundToInt()
            params.y = currentY.roundToInt()
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            if (island) placeIsland()
        }
        windowManager.updateViewLayout(composeView, params)
    }

    /** Острівець: по центру вгорі, у смузі статус-бару навколо камери. */
    private fun placeIsland() {
        val density = resources.displayMetrics.density
        val sbId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (sbId > 0) resources.getDimensionPixelSize(sbId) else (24 * density).toInt()
        val islandH = (34 * density).toInt()
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.x = 0
        params.y = ((statusBar - islandH) / 2).coerceAtLeast(0)
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
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
                if (answer == null) {
                    if (!com.dynamicai.launcher.AppListViewModel.getSharedLlm(applicationContext).isReady) {
                        val err = "Модель ШІ не завантажена."
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
        tts.shutdown()
        composeView?.let { windowManager.removeView(it) }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }


    override fun onBind(intent: Intent?): IBinder? = null
}
