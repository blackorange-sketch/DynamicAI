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
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.math.sin

class VoiceChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppListViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            VoiceChatScreen(vm = vm, onClose = { finish() })
        }
    }
}

@Composable
fun VoiceChatScreen(vm: AppListViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var voiceState by remember { mutableStateOf(VoiceState.LISTENING) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    val shouldListen = remember { mutableStateOf(true) }

    fun stopListening() {
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
        } catch (_: Exception) {}
    }

    fun startListening() {
        if (!shouldListen.value) return
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
                override fun onError(error: Int) {
                    voiceState = VoiceState.IDLE
                    // Перезапускаємо мікрофон ТІЛЬКИ якщо екран активний
                    if (shouldListen.value) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (shouldListen.value && voiceState == VoiceState.IDLE) {
                                try { startListening() } catch (_: Exception) {}
                            }
                        }, 1500)
                    }
                }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        messages = messages + ChatMessage("user", text)
                        val historyStr = messages.dropLast(1).takeLast(10).joinToString("\n") { msg ->
                            if (msg.role == "user") "Користувач: ${msg.content}" else "Асистент: ${msg.content}"
                        }
                        processQuery(
                            text, historyStr, vm, scope,
                            { voiceState = it },
                            { messages = messages + it },
                            { stopListening() }
                        )
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    shouldListen.value = true
                    startListening()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    shouldListen.value = false
                    try {
                        recognizer?.stopListening()
                        recognizer?.cancel()
                    } catch (_: Exception) {}
                }
                Lifecycle.Event.ON_DESTROY -> {
                    shouldListen.value = false
                    try { recognizer?.destroy() } catch (_: Exception) {}
                    recognizer = null
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            shouldListen.value = false
            try { recognizer?.destroy() } catch (_: Exception) {}
            recognizer = null
        }
    }

    LaunchedEffect(Unit) { 
        vm.tts // прогрів
        if (messages.isEmpty()) {
            val greeting = when ((0..2).random()) {
                0 -> "Системи онлайн. Чим можу допомогти?"
                1 -> "Слухаю вас."
                else -> "Я готовий."
            }
            messages = messages + ChatMessage("assistant", greeting)
            
            // Говоримо привітання і ТІЛЬКИ ПОТІМ вмикаємо мікрофон
            vm.tts.speakWithCallback(greeting) {
                if (shouldListen.value) startListening()
            }
        } else {
            if (shouldListen.value) startListening()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050505))
    ) {
        // Делікатні скляні плями на фоні (Liquid Glass)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFF64B5F6).copy(alpha = 0.05f), Color.Transparent)),
                radius = 800f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.2f)
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFE91E63).copy(alpha = 0.05f), Color.Transparent)),
                radius = 800f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.8f)
            )
        }

        // JARVIS Орб на задньому плані
        JarvisOrb(
            voiceState = voiceState,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
        )

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

            // Статус "Думаю..."
            val currentStatus = messages.lastOrNull { it.role == "status" }
            if (currentStatus != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2196F3).copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(currentStatus.content, color = Color(0xFF64B5F6), fontSize = 13.sp)
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages.filter { it.role != "status" }) { msg ->
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
                            val historyStr = messages.dropLast(1).takeLast(10).joinToString("\n") { msg ->
                                if (msg.role == "user") "Користувач: ${msg.content}" else "Асистент: ${msg.content}"
                            }
                            processQuery(
                                query, historyStr, vm, scope,
                                { voiceState = it },
                                { messages = messages + it },
                                { stopListening() }
                            )
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
fun JarvisOrb(voiceState: VoiceState, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis")
    
    val primaryColor by animateColorAsState(
        targetValue = when (voiceState) {
            VoiceState.LISTENING -> Color(0xFF00E676)
            VoiceState.SPEAKING -> Color(0xFFFF4081)
            VoiceState.THINKING -> Color(0xFF00B0FF)
            else -> Color(0xFF64B5F6)
        },
        animationSpec = tween(1000),
        label = "color"
    )

    val rotGyro1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Restart), label = "rot_g1"
    )
    val rotGyro2 by infiniteTransition.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart), label = "rot_g2"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.9f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )
    val scanY by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart), label = "scan"
    )

    Canvas(modifier = modifier) {
        val center = this.center
        val baseRadius = size.minDimension * 0.28f

        // 1. Зовнішнє світіння (Halo)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryColor.copy(alpha = 0.15f), Color.Transparent),
                center = center,
                radius = baseRadius * 2.2f
            ),
            radius = baseRadius * 2.2f,
            center = center
        )

        // 2. Внутрішнє світіння (Ядро) - БЕЗ чорного кольору
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.8f),
                    primaryColor.copy(alpha = 0.2f),
                    Color.Transparent
                ),
                center = center,
                radius = baseRadius * 0.9f * pulse
            ),
            radius = baseRadius * 0.9f * pulse,
            center = center
        )

        // 3. ЗАДНЯ ПІВКУЛЯ СІТКИ (Тьмяні лінії)
        val latCount = 4
        for (i in 1 until latCount) {
            val ratio = (i.toFloat() / latCount) * 2f - 1f
            val y = center.y + ratio * baseRadius
            val circleR = baseRadius * sqrt((1 - ratio * ratio).toFloat())
            // Задня дуга
            drawArc(
                color = primaryColor.copy(alpha = 0.15f),
                startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(center.x - circleR, y - circleR * 0.25f),
                size = androidx.compose.ui.geometry.Size(circleR * 2, circleR * 0.5f),
                style = Stroke(width = 1.dp.toPx())
            )
        }
        // Задні довготні лінії
        for (i in 0 until 4) {
            val angle = rotGyro1 + i * 45f
            rotate(angle, center) {
                drawOval(
                    color = primaryColor.copy(alpha = 0.15f),
                    topLeft = androidx.compose.ui.geometry.Offset(center.x - baseRadius * 0.8f, center.y - baseRadius),
                    size = androidx.compose.ui.geometry.Size(baseRadius * 1.6f, baseRadius * 2f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        // 4. ПЕРЕДНЯ ПІВКУЛЯ СІТКИ (Яскраві лінії)
        for (i in 1 until latCount) {
            val ratio = (i.toFloat() / latCount) * 2f - 1f
            val y = center.y + ratio * baseRadius
            val circleR = baseRadius * sqrt((1 - ratio * ratio).toFloat())
            // Передня дуга
            drawArc(
                color = primaryColor.copy(alpha = 0.7f),
                startAngle = 0f, sweepAngle = 180f, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(center.x - circleR, y - circleR * 0.25f),
                size = androidx.compose.ui.geometry.Size(circleR * 2, circleR * 0.5f),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
        // Передні довготні лінії
        for (i in 0 until 4) {
            val angle = rotGyro1 + i * 45f
            rotate(angle, center) {
                drawArc(
                    color = primaryColor.copy(alpha = 0.7f),
                    startAngle = 0f, sweepAngle = 180f, useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(center.x - baseRadius * 0.8f, center.y - baseRadius),
                    size = androidx.compose.ui.geometry.Size(baseRadius * 1.6f, baseRadius * 2f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        // 5. Гіроскопічні кільця (Об'ємні)
        // Кільце 1
        rotate(rotGyro2, center) {
            drawOval(
                color = primaryColor.copy(alpha = 0.9f),
                topLeft = androidx.compose.ui.geometry.Offset(center.x - baseRadius * 1.2f, center.y - baseRadius * 0.3f),
                size = androidx.compose.ui.geometry.Size(baseRadius * 2.4f, baseRadius * 0.6f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        // Кільце 2 (нахилене)
        rotate(45f, center) {
            rotate(rotGyro1, center) {
                drawOval(
                    color = primaryColor.copy(alpha = 0.5f),
                    topLeft = androidx.compose.ui.geometry.Offset(center.x - baseRadius * 1.1f, center.y - baseRadius * 0.2f),
                    size = androidx.compose.ui.geometry.Size(baseRadius * 2.2f, baseRadius * 0.4f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        // 6. Скляний відблиск (Блік для ефекту 3D)
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.4f), Color.Transparent),
                start = androidx.compose.ui.geometry.Offset(center.x - baseRadius * 0.5f, center.y - baseRadius * 0.5f),
                end = androidx.compose.ui.geometry.Offset(center.x, center.y)
            ),
            radius = baseRadius * 0.6f,
            center = androidx.compose.ui.geometry.Offset(center.x - baseRadius * 0.3f, center.y - baseRadius * 0.3f)
        )

        // 7. Скануючий промінь
        val scanYPos = center.y - baseRadius + (baseRadius * 2 * scanY)
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.6f), Color.Transparent)
            ),
            start = androidx.compose.ui.geometry.Offset(center.x - baseRadius * 0.8f, scanYPos),
            end = androidx.compose.ui.geometry.Offset(center.x + baseRadius * 0.8f, scanYPos),
            strokeWidth = 2.dp.toPx()
        )

        // 8. Зовнішній пунктир
        drawCircle(
            color = primaryColor.copy(alpha = 0.3f),
            radius = baseRadius * 1.45f,
            center = center,
            style = Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 12f), 0f)
            )
        )
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
    text: String, history: String, vm: AppListViewModel, scope: kotlinx.coroutines.CoroutineScope,
    onStateChange: (VoiceState) -> Unit, onMessage: (ChatMessage) -> Unit, stopListening: () -> Unit
) {
    onStateChange(VoiceState.THINKING)
    stopListening()
    onMessage(ChatMessage("status", "🔍 Думаю..."))
    scope.launch {
        try {
            if (!vm.ensureLlmLoaded()) {
                val err = "Модель ШІ не завантажена."
                onMessage(ChatMessage("assistant", err))
                onStateChange(VoiceState.SPEAKING)
                vm.tts.speakWithCallback(err) { onStateChange(VoiceState.LISTENING) }
                return@launch
            }
            val answer = vm.chatEngine.answerWithAgent(text, history)
            // Видаляємо статус "Думаю"
            // (він зникне, бо ми замінимо список повідомлень)
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
