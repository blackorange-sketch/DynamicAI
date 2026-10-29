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
    private lateinit var chatEngine: com.dynamicai.launcher.llm.ChatEngine
    private lateinit var tts: com.dynamicai.launcher.voice.TtsManager
    private var speechRecognizer: android.speech.SpeechRecognizer? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        startForegroundNotification()

        chatEngine = com.dynamicai.launcher.AppListViewModel.getSharedChatEngine(applicationContext)
        tts = com.dynamicai.launcher.voice.TtsManager(applicationContext)

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

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOrbService)
            setViewTreeViewModelStoreOwner(this@FloatingOrbService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOrbService)
            setContent {
                com.dynamicai.launcher.ui.theme.StoredAppTheme {
                    var isExpanded by remember { mutableStateOf(false) }
                    FloatingOrb(
                        isExpanded = isExpanded,
                        messages = ChatHistoryManager.history.map { it.role to it.content },
                        isThinking = isThinking,
                        onExpandChange = {
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
        }
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


    private fun processQuery(text: String) {
        ChatHistoryManager.addMessage("user", text)
        isThinking = true
        scope.launch {
            try {
                if (!com.dynamicai.launcher.AppListViewModel.getSharedLlm(applicationContext).isReady) {
                    val err = "Модель ШІ не завантажена."
                    ChatHistoryManager.addMessage("assistant", err)
                    isThinking = false
                    tts.speak(err)
                    return@launch
                }
                val history = ChatHistoryManager.history.takeLast(10).joinToString("\n") { 
                    if (it.role == "user") "Користувач: ${it.content}" else "Асистент: ${it.content}" 
                }
                val answer = chatEngine.answerWithAgent(text, history)
                ChatHistoryManager.addMessage("assistant", answer)
                isThinking = false
                tts.speak(answer)
            } catch (e: Exception) {
                ChatHistoryManager.addMessage("assistant", "Помилка: ${e.message}")
                isThinking = false
            }
        }
    }

    private fun startListening() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            android.widget.Toast.makeText(this, "Потрібен дозвіл на мікрофон! Надайте його в налаштуваннях.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer?.destroy()
        speechRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {
                    if (tts.isReady) tts.stop()
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) { isThinking = false }
                override fun onResults(results: android.os.Bundle?) {
                    val text = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                    if (text.isNotBlank()) processQuery(text)
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
        }
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
            putExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE, com.dynamicai.launcher.voice.OfflineSpeech.preferOffline(this@FloatingOrbService))
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
