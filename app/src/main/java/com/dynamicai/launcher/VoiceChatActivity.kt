package com.dynamicai.launcher

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VoiceChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppListViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            VoiceAssistantScreen(vm = vm, onClose = { finish() })
        }
    }
}

@Composable
fun VoiceAssistantScreen(vm: AppListViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var voiceState by remember { mutableStateOf(VoiceState.LISTENING) }
    var partialText by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { voiceState = VoiceState.LISTENING }
                override fun onBeginningOfSpeech() {
                    if (voiceState == VoiceState.SPEAKING) {
                        vm.tts.stop()
                        voiceState = VoiceState.LISTENING
                    }
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) { voiceState = VoiceState.IDLE }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                    partialText = ""
                    if (text.isNotBlank()) {
                        messages = messages + ChatMessage("user", text)
                        processQuery(text, vm, scope) { newState -> voiceState = newState }
                    } else {
                        voiceState = VoiceState.IDLE
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                    partialText = t
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer?.startListening(intent)
    }

    LaunchedEffect(Unit) {
        startListening()
    }

    // Анімація для неонового кола
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse_anim"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A0A0A), Color(0xFF14141E), Color(0xFF0A0A0A))))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AI ASSISTANT", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("✕", color = Color.White.copy(alpha = 0.5f), fontSize = 24.sp, modifier = Modifier.clickable(onClick = onClose))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Чат
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    val isUser = msg.role == "user"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isUser) Color(0xFF2196F3).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                                .padding(12.dp)
                        ) {
                            Text(msg.content, color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Центральний індикатор
            Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                when (voiceState) {
                    VoiceState.LISTENING -> {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                                .clip(CircleShape)
                                .background(Brush.radialGradient(listOf(Color(0xFF4CAF50), Color.Transparent)))
                        )
                    }
                    VoiceState.THINKING -> {
                        CircularProgressIndicator(color = Color(0xFF2196F3), modifier = Modifier.size(40.dp))
                    }
                    VoiceState.SPEAKING -> {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                                .clip(CircleShape)
                                .background(Brush.radialGradient(listOf(Color(0xFFE91E63), Color.Transparent)))
                        )
                    }
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Поле вводу та мікрофон
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.08f)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color(0xFF64B5F6)),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Запитай або скажи...", color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp)
                        inner()
                    }
                )
                IconButton(onClick = {
                    if (query.isNotBlank()) {
                        messages = messages + ChatMessage("user", query)
                        processQuery(query, vm, scope) { newState -> voiceState = newState }
                        query = ""
                    }
                }) {
                    Text("➤", fontSize = 20.sp, color = Color(0xFF64B5F6), modifier = Modifier.padding(12.dp).clickable { /* логіка відправки */ })
                }
                IconButton(onClick = { startListening() }) {
                    Text("🎤", fontSize = 24.sp, modifier = Modifier.padding(12.dp).clickable { startListening() })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Нижні кнопки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionChipIcon("🔍", "Пошук") { ctx.startActivity(Intent(Intent.ACTION_WEB_SEARCH)) }
                ActionChipIcon("🌐", "Переклад") { /* Логіка */ }
                ActionChipIcon("🤖", "AI") { /* Логіка */ }
                ActionChipIcon("🎮", "Wild Rift") { ctx.startActivity(Intent(ctx, WildRiftAdvisorActivity::class.java)) }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

fun processQuery(text: String, vm: AppListViewModel, scope: kotlinx.coroutines.CoroutineScope, onStateChange: (VoiceState) -> Unit) {
    onStateChange(VoiceState.THINKING)
    scope.launch {
        try {
            if (!vm.ensureLlmLoaded()) {
                val err = "Модель ШІ не завантажена. Завантажте її в налаштуваннях."
                // Тут можна додати повідомлення в чат, але для цього треба передавати messages
                onStateChange(VoiceState.SPEAKING)
                vm.tts.speakWithCallback(err) {
                    onStateChange(VoiceState.LISTENING)
                }
                return@launch
            }
            val answer = vm.chatEngine.answer(text)
            onStateChange(VoiceState.SPEAKING)
            vm.tts.speakWithCallback(answer) {
                onStateChange(VoiceState.LISTENING)
            }
        } catch (e: Exception) {
            onStateChange(VoiceState.IDLE)
        }
    }
}

@Composable
fun ActionChipIcon(icon: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(8.dp)
    ) {
        Text(icon, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}

data class ChatMessage(val role: String, val content: String)
enum class VoiceState { IDLE, LISTENING, THINKING, SPEAKING }
