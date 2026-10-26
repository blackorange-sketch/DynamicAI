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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

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

    LaunchedEffect(Unit) { startListening() }

    // Анімація для HUD (скануюче коло)
    val infiniteTransition = rememberInfiniteTransition(label = "hud")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "rot"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )

    val primaryColor = Color(0xFF00E5FF) // Неоновий блакитний
    val accentColor = if (voiceState == VoiceState.SPEAKING) Color(0xFFFF4081) else if (voiceState == VoiceState.LISTENING) Color(0xFF00E676) else primaryColor

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050505)) // Майже чорний
    ) {
        // Футуристична сітка на фоні
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 60.dp.toPx()
            val lineColor = Color.White.copy(alpha = 0.03f)
            var x = 0f
            while (x < size.width) {
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NEURAL INTERFACE v2.0",
                    color = primaryColor.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "[X]",
                    color = primaryColor.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable(onClick = onClose)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Чат
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                .clip(RoundedCornerShape(4.dp)) // Гострі кути для футуризму
                                .background(if (isUser) primaryColor.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f))
                                .border(1.dp, if (isUser) primaryColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = msg.content, 
                                color = Color.White, 
                                fontSize = 15.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Футуристичний індикатор (HUD)
            Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(100.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }) {
                    // Зовнішнє коло
                    drawCircle(color = accentColor.copy(alpha = 0.2f), style = Stroke(width = 2.dp.toPx()))
                    // Скануючі дуги
                    drawArc(color = accentColor, startAngle = rotation, sweepAngle = 60f, useCenter = false, style = Stroke(width = 4.dp.toPx()))
                    drawArc(color = accentColor, startAngle = rotation + 180f, sweepAngle = 60f, useCenter = false, style = Stroke(width = 4.dp.toPx()))
                    // Внутрішнє ядро
                    drawCircle(color = accentColor.copy(alpha = 0.8f), radius = 15.dp.toPx())
                }
                
                if (voiceState == VoiceState.THINKING) {
                    CircularProgressIndicator(color = primaryColor, modifier = Modifier.size(120.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Поле вводу (Термінал)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .border(1.dp, primaryColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(">", color = primaryColor, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    textStyle = TextStyle(color = primaryColor, fontSize = 16.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(primaryColor),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Введіть запит...", color = primaryColor.copy(alpha = 0.3f), fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                        inner()
                    }
                )
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.2f)).clickable { 
                        if (query.isNotBlank()) {
                            messages = messages + ChatMessage("user", query)
                            processQuery(query, vm, scope) { newState -> voiceState = newState }
                            query = ""
                        }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = primaryColor, fontSize = 16.sp)
                }
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(if (voiceState == VoiceState.LISTENING) accentColor.copy(alpha = 0.3f) else Color.Transparent).clickable { startListening() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎤", fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Нижні кнопки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FuturisticChip("🔍", "ПОШУК") { ctx.startActivity(Intent(Intent.ACTION_WEB_SEARCH)) }
                FuturisticChip("🌐", "ПЕРЕКЛАД") { /* Логіка */ }
                FuturisticChip("🤖", "ШІ") { /* Логіка */ }
                FuturisticChip("🎮", "WILD RIFT") { ctx.startActivity(Intent(ctx, WildRiftAdvisorActivity::class.java)) }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun FuturisticChip(icon: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Text(icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color(0xFF00E5FF).copy(alpha = 0.7f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
    }
}

fun processQuery(text: String, vm: AppListViewModel, scope: kotlinx.coroutines.CoroutineScope, onStateChange: (VoiceState) -> Unit) {
    onStateChange(VoiceState.THINKING)
    scope.launch {
        try {
            if (!vm.ensureLlmLoaded()) {
                val err = "Модель ШІ не завантажена. Завантажте її в налаштуваннях."
                onStateChange(VoiceState.SPEAKING)
                vm.tts.speakWithCallback(err) { onStateChange(VoiceState.LISTENING) }
                return@launch
            }
            val answer = vm.chatEngine.answer(text)
            onStateChange(VoiceState.SPEAKING)
            vm.tts.speakWithCallback(answer) { onStateChange(VoiceState.LISTENING) }
        } catch (e: Exception) {
            onStateChange(VoiceState.IDLE)
        }
    }
}

data class ChatMessage(val role: String, val content: String)
enum class VoiceState { IDLE, LISTENING, THINKING, SPEAKING }
