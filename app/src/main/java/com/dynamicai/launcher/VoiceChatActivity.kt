package com.dynamicai.launcher

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                    if (text.isNotBlank()) {
                        messages = messages + ChatMessage("user", text)
                        processQuery(text, vm, scope, { voiceState = it }, { messages = messages + it }) { recognizer?.stopListening(); recognizer?.cancel() }
                    } else {
                        voiceState = VoiceState.IDLE
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
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
        vm.tts // прогрів
        startListening() 
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(colors = listOf(Color(0xFF14141E), Color(0xFF050505)), radius = 1200f))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFF64B5F6).copy(alpha = 0.1f), Color.Transparent)),
                radius = 400f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.3f)
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFE91E63).copy(alpha = 0.1f), Color.Transparent)),
                radius = 400f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.7f)
            )
        }

        // Фонова куля
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            val bgOrbColor = when (voiceState) {
                VoiceState.LISTENING -> Color(0xFF4CAF50)
                VoiceState.SPEAKING -> Color(0xFFE91E63)
                VoiceState.THINKING -> Color(0xFF2196F3)
                else -> Color.Gray
            }
            Box(
                modifier = Modifier
                    .padding(bottom = 100.dp)
                    .size(300.dp)
                    .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(bgOrbColor.copy(alpha = 0.15f), Color.Transparent)))
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Голосовий асистент", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("✕", color = Color.White.copy(alpha = 0.6f), fontSize = 22.sp, modifier = Modifier.padding(8.dp).clickable(onClick = onClose))
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = if (isUser) 0.15f else 0.08f))
                                .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                                .padding(14.dp)
                        ) {
                            Text(msg.content, color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color.White),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Запитай або скажи...", color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp)
                        inner()
                    }
                )
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF64B5F6).copy(alpha = 0.15f)).clickable { 
                        if (query.isNotBlank()) {
                            messages = messages + ChatMessage("user", query)
                            processQuery(query, vm, scope, { voiceState = it }, { messages = messages + it }) { recognizer?.stopListening(); recognizer?.cancel() }
                            query = ""
                        }
                    },
                    contentAlignment = Alignment.Center
                ) { Text("➤", color = Color(0xFF64B5F6), fontSize = 20.sp) }
                
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(if (voiceState == VoiceState.LISTENING) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color.Transparent).clickable { startListening() },
                    contentAlignment = Alignment.Center
                ) { Text("🎤", fontSize = 20.sp) }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LiquidChip("Пошук", Color(0xFF4285F4)) { ctx.startActivity(Intent(Intent.ACTION_WEB_SEARCH)) }
                LiquidChip("Переклад", Color(0xFFFF9800)) { }
                LiquidChip("AI", Color(0xFF2196F3)) { }
                LiquidChip("Wild Rift", Color(0xFF9C27B0)) { ctx.startActivity(Intent(ctx, WildRiftAdvisorActivity::class.java)) }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun LiquidChip(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.2f))
            .border(0.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

fun processQuery(
    text: String, vm: AppListViewModel, scope: kotlinx.coroutines.CoroutineScope,
    onStateChange: (VoiceState) -> Unit, onMessage: (ChatMessage) -> Unit, stopListening: () -> Unit
) {
    onStateChange(VoiceState.THINKING)
    stopListening()
    scope.launch {
        try {
            if (!vm.ensureLlmLoaded()) {
                val err = "Модель ШІ не завантажена."
                onMessage(ChatMessage("assistant", err))
                onStateChange(VoiceState.SPEAKING)
                vm.tts.speakWithCallback(err) { onStateChange(VoiceState.LISTENING) }
                return@launch
            }
            val answer = vm.chatEngine.answerWithAgent(text)
            onStateChange(VoiceState.SPEAKING)
            vm.tts.speakWithCallback(answer) { onStateChange(VoiceState.LISTENING) }
            onMessage(ChatMessage("assistant", answer))
        } catch (e: Exception) {
            onMessage(ChatMessage("assistant", "Помилка: ${e.message}"))
            onStateChange(VoiceState.IDLE)
        }
    }
}

data class ChatMessage(val role: String, val content: String)
enum class VoiceState { IDLE, LISTENING, THINKING, SPEAKING }
