package com.dynamicai.launcher

import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

enum class VoiceState { LISTENING, THINKING, SPEAKING, IDLE }

class VoiceChatActivity : ComponentActivity() {
    private val vm: AppListViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VoiceChatScreen(vm, onClose = { finish() }) }
    }
}

@Composable
fun VoiceChatScreen(vm: AppListViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val storage = remember { VoiceChatStorage(ctx) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messages by remember { mutableStateOf<List<ChatMessage>>(storage.load()) }
    var voiceState by remember { mutableStateOf(VoiceState.LISTENING) }
    var webSearchEnabled by remember { mutableStateOf(true) }
    var partialText by remember { mutableStateOf("") }
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

    fun startListening() {
        if (listeningRef.value) return
        val recognizer = recognizerRef.value ?: SpeechRecognizer.createSpeechRecognizer(ctx).also {
            recognizerRef.value = it
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {
                // Barge-in: if TTS speaking, stop it
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
                if (voiceState == VoiceState.LISTENING) {
                    scope.launch { delay(300); if (voiceState == VoiceState.LISTENING) startListening() }
                }
            }
            override fun onResults(results: Bundle?) {
                listeningRef.value = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                partialText = ""
                if (text.isNotBlank()) {
                    handleUserQuery(text)
                } else if (voiceState == VoiceState.LISTENING) {
                    scope.launch { delay(300); startListening() }
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
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
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

    fun handleUserQuery(text: String) {
        if (text.isBlank()) return
        resetAutoStop()
        messages = messages + ChatMessage("user", text)
        voiceState = VoiceState.THINKING
        scope.launch {
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
            }
        }
    }

    // Start listening when activity opens
    LaunchedEffect(Unit) { startListening() }

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
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎙️ Голосова розмова", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
                PulseCircle(voiceState)
            }

            // Status + partial
            Text(
                text = when (voiceState) {
                    VoiceState.LISTENING -> if (partialText.isNotBlank()) "🎤 $partialText" else "🎤 Слухаю…"
                    VoiceState.THINKING -> "🧠 Думаю…"
                    VoiceState.SPEAKING -> "🔊 Відповідаю…"
                    VoiceState.IDLE -> ""
                },
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth(),
                fontWeight = FontWeight.Medium
            )
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
}
