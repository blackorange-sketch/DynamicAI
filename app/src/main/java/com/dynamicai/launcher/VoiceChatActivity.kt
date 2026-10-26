package com.dynamicai.launcher

import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.Intent
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.dynamicai.launcher.voice.Anime3DAvatar
import androidx.compose.foundation.layout.*
import com.dynamicai.launcher.voice.AvatarSettings
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.voice.ChatMessage
import com.dynamicai.launcher.voice.VoiceChatStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

enum class VoiceState { LISTENING, THINKING, SPEAKING, IDLE }

class VoiceChatActivity : FragmentActivity() {
    private val vm: AppListViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VoiceChatScreen(vm, onClose = { finish() }) }
    }
}

@Composable
fun VoiceChatScreen(vm: AppListViewModel, onClose: () -> Unit) {
    val appContext = LocalContext.current
    var showAvatarSettings by remember { mutableStateOf(false) }


        
    val ctx = LocalContext.current
    val storage = remember { VoiceChatStorage(ctx) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messages by remember { mutableStateOf<List<ChatMessage>>(storage.load()) }
    var voiceState by remember { mutableStateOf(VoiceState.LISTENING) }
    var webSearchEnabled by remember { mutableStateOf(true) }
    var partialText by remember { mutableStateOf("") }
    val queryChannel = remember { Channel<String>(Channel.CONFLATED) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val recognizerRef = remember { mutableStateOf<SpeechRecognizer?>(null) }
    val listeningRef = remember { mutableStateOf(false) }
    val autoStopJobRef = remember { mutableStateOf<Job?>(null) }

    // Save history on change
    LaunchedEffect(messages) { storage.save(messages) }

    // Auto-scroll
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Auto-stop after 5 minutes of silence
    fun resetAutoStop() {
        autoStopJobRef.value?.cancel()
        autoStopJobRef.value = scope.launch {
            delay(5 * 60_000L)
            onClose()
        }
    }
    LaunchedEffect(Unit) { resetAutoStop() }





    // STT listener factory
    val sttListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {
                if (voiceState == VoiceState.SPEAKING) {
                    vm.tts.stop()
                    voiceState = VoiceState.LISTENING
                }
            }
            override fun onRmsChanged(rms: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(e: Int) {
                listeningRef.value = false
            }
            override fun onResults(results: Bundle?) {
                listeningRef.value = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                partialText = ""
                if (text.isNotBlank()) {
                    queryChannel.trySend(text)
                }
            }
            override fun onPartialResults(partial: Bundle?) {
                val t = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                partialText = t
                if (voiceState == VoiceState.SPEAKING && t.isNotBlank()) {
                    vm.tts.stop()
                    voiceState = VoiceState.LISTENING
                }
            }
            override fun onEvent(e: Int, p: Bundle?) {}
        }
    }

    fun startListening() {
        if (listeningRef.value) return
        val recognizer = recognizerRef.value ?: SpeechRecognizer.createSpeechRecognizer(ctx).also {
            recognizerRef.value = it
        }
        recognizer.setRecognitionListener(sttListener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            recognizer.startListening(intent)
            listeningRef.value = true
            errorText = null
        } catch (t: Throwable) {
            errorText = "STT помилка: ${t.message}"
        }
    }

    // Обробка STT через Channel
    LaunchedEffect(Unit) {
        queryChannel.receiveAsFlow().collect { text ->
            resetAutoStop()
            messages = messages + ChatMessage("user", text)
            voiceState = VoiceState.THINKING
            
            // Чекаємо, поки LLM завантажиться в пам'ять
            if (!vm.ensureLlmLoaded()) {
                val errMsg = "Модель ШІ не завантажена. Завантажте її в налаштуваннях."
                messages = messages + ChatMessage("assistant", errMsg)
                voiceState = VoiceState.SPEAKING
                vm.tts.speakWithCallback(errMsg) {
                    scope.launch(Dispatchers.Main) {
                        if (voiceState == VoiceState.SPEAKING) {
                            voiceState = VoiceState.LISTENING
                            startListening()
                        }
                    }
                }
                return@collect 
            }

            try {
                val answer = vm.chatEngine.answer(text)
                messages = messages + ChatMessage("assistant", answer)
                voiceState = VoiceState.SPEAKING
                vm.tts.speakWithCallback(answer) {
                    scope.launch(Dispatchers.Main) {
                        if (voiceState == VoiceState.SPEAKING) {
                            voiceState = VoiceState.LISTENING
                            startListening()
                        }
                    }
                }
            } catch (t: Throwable) {
                errorText = "Помилка: ${t.message}"
                voiceState = VoiceState.LISTENING
                startListening()
            }
        }
    }

    // Start listening when activity opens
    LaunchedEffect(Unit) { 
        AvatarSettings.load(ctx)
        startListening() 
    }

    DisposableEffect(Unit) {
        onDispose {
            autoStopJobRef.value?.cancel()
            vm.tts.stop()
            try { recognizerRef.value?.destroy() } catch (_: Throwable) {}
        }
    }

    // UI
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
                

        // 3D-аватар на задньому фоні
        Anime3DAvatar(
            voiceState = voiceState,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.65f) // Займає 65% висоти екрану
                .align(Alignment.BottomCenter) // Притискаємо до низу
        )

        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎙️ Голосова розмова", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("⚙️", color = Color.White.copy(alpha = 0.6f), fontSize = 20.sp,
                    modifier = Modifier.padding(8.dp).clickable { showAvatarSettings = true })
                Text("✕", color = Color.White.copy(alpha = 0.6f), fontSize = 22.sp,
                    modifier = Modifier.padding(8.dp).clickable(onClick = onClose))
            }
            Spacer(Modifier.height(8.dp))

            // History
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (msg.role == "user") Arrangement.End else Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (msg.role == "user") Color(0xFF2196F3).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f))
                                .padding(12.dp)
                        ) {
                            Text(msg.text, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Web toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔍 Web search", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    if (webSearchEnabled) "ON" else "OFF",
                    color = if (webSearchEnabled) Color(0xFF4CAF50) else Color.Gray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { webSearchEnabled = !webSearchEnabled }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Pulse circle
            Box(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                
            }

            // Status + partial
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = when (voiceState) {
                        VoiceState.LISTENING -> if (partialText.isNotBlank()) "🎤 $partialText" else "🎤 Слухаю…"
                        VoiceState.THINKING -> "🧠 Думаю…"
                        VoiceState.SPEAKING -> "🔊 Відповідаю…"
                        VoiceState.IDLE -> ""
                    },
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Medium
                )
                
                // Кнопка з'являється лише під час мовлення ШІ
                if (voiceState == VoiceState.SPEAKING) {
                    Text(
                        text = "⏹ Зупинити",
                        color = Color(0xFFFF8A80),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                vm.tts.stop()
                                voiceState = VoiceState.LISTENING
                                startListening()
                            }
                            .padding(8.dp)
                    )
                }
            }
            if (errorText != null) {
                Text(errorText!!, color = Color(0xFFFF8A80), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun PulseCircle(state: VoiceState) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val color = when (state) {
        VoiceState.LISTENING -> Color(0xFF2196F3)
        VoiceState.THINKING -> Color(0xFFFF9800)
        VoiceState.SPEAKING -> Color(0xFF4CAF50)
        VoiceState.IDLE -> Color.Gray
    }
    Box(
        modifier = Modifier
            .size(120.dp)
            .scale(if (state == VoiceState.IDLE) 1f else scale)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(color.copy(alpha = 0.6f), color.copy(alpha = 0.1f))))
    )

    

                
        // Діалог налаштувань аватара
        if (showAvatarSettings) {
            AlertDialog(
                onDismissRequest = { 
                    showAvatarSettings = false 
                    AvatarSettings.save(appContext)
                },
                title = { Text("Налаштування аватара") },
                text = {
                    Column {
                        Text("Розмір: ${String.format("%.1f", AvatarSettings.scale)}")
                        Slider(
                            value = AvatarSettings.scale,
                            onValueChange = { AvatarSettings.scale = it },
                            valueRange = 0.5f..2.0f
                        )
                        
                        Text("Зсув вгору/вниз: ${AvatarSettings.offsetY.toInt()}")
                        Slider(
                            value = AvatarSettings.offsetY,
                            onValueChange = { AvatarSettings.offsetY = it },
                            valueRange = -500f..500f
                        )
                        
                        Text("Зсув вліво/вправо: ${AvatarSettings.offsetX.toInt()}")
                        Slider(
                            value = AvatarSettings.offsetX,
                            onValueChange = { AvatarSettings.offsetX = it },
                            valueRange = -300f..300f
                        )
                        
                        Text("Поворот: ${AvatarSettings.rotationY.toInt()}°")
                        Slider(
                            value = AvatarSettings.rotationY,
                            onValueChange = { AvatarSettings.rotationY = it },
                            valueRange = 0f..360f
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { 
                        showAvatarSettings = false 
                        AvatarSettings.save(appContext)
                    }) {
                        Text("Готово")
                    }
                }
            )
        }

}
