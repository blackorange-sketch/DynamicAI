package com.dynamicai.launcher

import android.content.Context
import com.dynamicai.launcher.ui.theme.AppTheme
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dynamicai.launcher.ui.GlassCloseButton
import com.dynamicai.launcher.ui.theme.AppDims
import androidx.compose.runtime.*
import com.dynamicai.launcher.overlay.ChatMessage
import com.dynamicai.launcher.overlay.ChatHistoryManager
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
        // Вмикаємо апаратне придушення відлуння (AEC)
        try {
            val am = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            am.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
        } catch (_: Throwable) {}

        val askText = intent?.getStringExtra("ask")
        setContent {
            val vm: AppListViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            com.dynamicai.launcher.ui.theme.StoredAppTheme {
                VoiceChatScreen(vm = vm, onClose = { finish() }, initialQuery = askText)
            }
        }
    }
}

@Composable
fun VoiceChatScreen(vm: AppListViewModel, onClose: () -> Unit, initialQuery: String? = null) {
    val ctx = LocalContext.current
    val messages = ChatHistoryManager.history
    var voiceState by remember { mutableStateOf(VoiceState.LISTENING) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    val shouldListen = remember { mutableStateOf(true) }
    var lastSentText by remember { mutableStateOf("") }
    var lastSentAt by remember { mutableStateOf(0L) }
    var voskSession by remember { mutableStateOf<com.dynamicai.launcher.voice.VoskEngine.Session?>(null) }

    fun stopListening() {
        try { voskSession?.stop() } catch (_: Exception) {}
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
        } catch (_: Exception) {}
    }

    var activeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showArchive by remember { mutableStateOf(false) }

    /** Відправляє запит моделі. addUserMessage=false — для повтору останнього запиту. */
    fun sendQuery(text: String, addUserMessage: Boolean = true) {
        if (text.isBlank()) return
        // Подвійний виклик (напр. розпізнавач віддав результат двічі) — ігноруємо
        val now = System.currentTimeMillis()
        if (addUserMessage && text == lastSentText && now - lastSentAt < 2000) return
        if (addUserMessage) { lastSentText = text; lastSentAt = now }
        if (addUserMessage) ChatHistoryManager.addMessage("user", text)
        val historyStr = messages
            .filter { it.role == "user" || it.role == "assistant" }
            .dropLast(1).takeLast(10)
            .joinToString("\n") { msg ->
                if (msg.role == "user") "Користувач: ${msg.content}" else "Асистент: ${msg.content}"
            }
        activeJob = processQuery(
            text, historyStr, vm, scope,
            { voiceState = it },
            { m ->
                if (m.role != "status") ChatHistoryManager.removeStatus()
                ChatHistoryManager.addMessage(m.role, m.content, m.action)
            },
            { stopListening() }
        )
    }

    fun startListening() {
        if (!shouldListen.value) return
        voskSession?.stop()
        voskSession = null
        val voiceMode = com.dynamicai.launcher.data.SettingsStorage(ctx).load().voiceEngine
        if (com.dynamicai.launcher.voice.VoskEngine.shouldUse(ctx, voiceMode)) {
            recognizer?.destroy()
            recognizer = null
            voskSession = com.dynamicai.launcher.voice.VoskEngine.Session(
                ctx,
                onReady = { voiceState = VoiceState.LISTENING },
                onPartial = { if (voiceState == VoiceState.SPEAKING) { vm.tts.stop(); voiceState = VoiceState.LISTENING } },
                onResult = { text -> sendQuery(text) },
                onError = {
                    voiceState = VoiceState.IDLE
                    if (shouldListen.value) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (shouldListen.value && voiceState == VoiceState.IDLE) {
                                try { startListening() } catch (_: Exception) {}
                            }
                        }, 1500)
                    }
                }
            ).also { it.start() }
            return
        }
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
                    if (com.dynamicai.launcher.voice.OfflineSpeech.isMissingLanguage(error)) {
                        android.widget.Toast.makeText(ctx, com.dynamicai.launcher.voice.OfflineSpeech.HINT, android.widget.Toast.LENGTH_LONG).show()
                        return
                    }
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
                        sendQuery(text)
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
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, com.dynamicai.launcher.voice.OfflineSpeech.preferOffline(ctx))
        }
        recognizer?.startListening(intent)
    }

    /** Зупиняє очікування відповіді та озвучення. Нативна генерація дописується у фоні, але результат відкидається. */
    fun stopGeneration() {
        activeJob?.cancel()
        activeJob = null
        vm.tts.stop()
        ChatHistoryManager.removeStatus()
        voiceState = VoiceState.LISTENING
        startListening()
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
                    try { voskSession?.stop() } catch (_: Exception) {}
                    try {
                        recognizer?.stopListening()
                        recognizer?.cancel()
                    } catch (_: Exception) {}
                }
                Lifecycle.Event.ON_DESTROY -> {
                    shouldListen.value = false
                    try { recognizer?.destroy() } catch (_: Exception) {}
                    try { voskSession?.stop() } catch (_: Exception) {}
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
        ChatHistoryManager.init(ctx)  // історія переживає закриття екрана
        vm.tts // прогрів
        if (!initialQuery.isNullOrBlank()) {
            sendQuery(initialQuery)
        } else if (messages.isEmpty()) {
            val greeting = when ((0..2).random()) {
                0 -> "Системи онлайн. Чим можу допомогти?"
                1 -> "Слухаю вас."
                else -> "Я готовий."
            }
            ChatHistoryManager.addMessage("assistant", greeting)
            
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
            .background(AppTheme.colors.backdropEnd)
    ) {
        // Жива аврора + частинки на фоні; колір залежить від стану (слухає / думає / говорить)
        AuroraBackground(voiceState = voiceState, modifier = Modifier.fillMaxSize())

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
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(AppDims.spaceL)
        ) {
            val visible = messages.filter { it.role != "status" }
            val busy = voiceState == VoiceState.THINKING || voiceState == VoiceState.SPEAKING
            val maxBubble = (LocalConfiguration.current.screenWidthDp * 0.82f).dp

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Голосовий асистент", color = AppTheme.colors.textPrimary, fontSize = AppDims.textTitle, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showArchive = true }) {
                        Text("🕘", fontSize = 20.sp)
                    }
                    if (visible.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Text("＋", color = AppTheme.colors.textSecondary, fontSize = 26.sp)
                        }
                    }
                    GlassCloseButton(onClick = onClose)
                }
            }

            // Швидкий вибір моделі + статус рушія
            ModelChip(vm)
            Spacer(modifier = Modifier.height(AppDims.spaceM))

            if (showClearDialog) {
                AlertDialog(
                    onDismissRequest = { showClearDialog = false },
                    containerColor = AppTheme.colors.surface,
                    title = { Text("Нова розмова?", color = AppTheme.colors.textPrimary) },
                    text = { Text("Поточну розмову буде збережено в історії (🕘).", color = AppTheme.colors.textSecondary) },
                    confirmButton = {
                        TextButton(onClick = { stopGeneration(); ChatHistoryManager.clear(); showClearDialog = false }) {
                            Text("Почати нову", color = AppTheme.colors.accent)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearDialog = false }) {
                            Text("Скасувати", color = AppTheme.colors.textSecondary)
                        }
                    }
                )
            }

            // Статус "Думаю…"
            val currentStatus = messages.lastOrNull { it.role == "status" }
            if (currentStatus != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = AppDims.spaceXS),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppDims.radiusS))
                            .background(AppTheme.colors.accentDark.copy(alpha = 0.15f))
                            .padding(horizontal = AppDims.spaceM, vertical = 6.dp)
                    ) {
                        Text(currentStatus.content, color = AppTheme.colors.accent, fontSize = 13.sp)
                    }
                }
            }

            // Автопрокрутка до нового повідомлення
            LaunchedEffect(visible.size, currentStatus != null) {
                if (visible.isNotEmpty()) listState.animateScrollToItem(visible.lastIndex)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppDims.spaceS)
            ) {
                items(visible) { msg ->
                    MessageBubble(msg, maxBubble) { action ->
                        when (action) {
                            "restart" -> vm.restartLlmEngine()
                            "retry" -> messages.lastOrNull { it.role == "user" }?.let { last ->
                                ChatHistoryManager.history.removeAll { it.role == "error" }
                                sendQuery(last.content, addUserMessage = false)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppDims.spaceL))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(AppTheme.colors.glassBackground)
                    .border(1.dp, AppTheme.colors.glassBorder, RoundedCornerShape(24.dp))
                    .padding(AppDims.spaceS),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f).padding(horizontal = AppDims.spaceM),
                    textStyle = TextStyle(color = AppTheme.colors.textPrimary, fontSize = AppDims.textBodyLarge),
                    cursorBrush = SolidColor(AppTheme.colors.accent),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Запитай або скажи…", color = AppTheme.colors.textMuted, fontSize = AppDims.textBodyLarge)
                        inner()
                    }
                )
                if (busy) {
                    // Стоп: зупиняє очікування відповіді та озвучення
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(AppTheme.colors.danger.copy(alpha = 0.18f)).clickable { stopGeneration() },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(AppTheme.colors.danger))
                    }
                } else {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(AppTheme.colors.accent.copy(alpha = 0.18f)).clickable {
                            if (query.isNotBlank()) {
                                sendQuery(query)
                                query = ""
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Send, contentDescription = "Надіслати", tint = AppTheme.colors.accent) }
                }

                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(if (voiceState == VoiceState.LISTENING) AppTheme.colors.success.copy(alpha = 0.2f) else Color.Transparent).clickable { startListening() },
                    contentAlignment = Alignment.Center
                ) { Icon(MicIcon, contentDescription = "Мікрофон", tint = if (voiceState == VoiceState.LISTENING) AppTheme.colors.success else AppTheme.colors.textSecondary) }
            }

            Spacer(modifier = Modifier.height(AppDims.spaceM))

            // Чіпи прокручуються, щоб не вилазити на вузьких екранах
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AppDims.spaceS)
            ) {
                LiquidChip("Пошук", AppTheme.colors.accent) { ctx.startActivity(Intent(Intent.ACTION_WEB_SEARCH)) }
                LiquidChip("Переклад", AppTheme.colors.warning) { query = "Переклади на англійську: " }
                LiquidChip("AI", AppTheme.colors.accentDark) { ctx.startActivity(Intent(ctx, AiHubActivity::class.java)) }
                LiquidChip("Wild Rift", AppTheme.colors.purple) { ctx.startActivity(Intent(ctx, WildRiftAdvisorActivity::class.java)) }
            }
            Spacer(modifier = Modifier.height(AppDims.spaceS))
        }

        if (showArchive) {
            com.dynamicai.launcher.ui.ChatArchiveScreen(
                onOpen = { chat -> stopGeneration(); ChatHistoryManager.restore(chat.id); showArchive = false },
                onClose = { showArchive = false }
            )
        }
    }
}

private val MicIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Mic", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.addPathNodes(
            "M12 14c1.66 0 2.99-1.34 2.99-3L15 5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z"
        ),
        fill = SolidColor(Color.Black)
    ).build()
}

/** Швидкий вибір моделі та індикатор стану рушія у шапці чату. */
@Composable
private fun ModelChip(vm: AppListViewModel) {
    val ready by vm.llmReady.collectAsStateWithLifecycle()
    val restarting by vm.llmRestarting.collectAsStateWithLifecycle()
    val downloaded by vm.downloadedModelIds.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    // Список завантажених моделей читаємо з диска щоразу при відкритті — не залежимо від того, чи вже оновився StateFlow
    LaunchedEffect(open) { if (open) vm.refreshDownloadedModels() }
    val models = com.dynamicai.launcher.llm.ModelRepository.AVAILABLE_MODELS
    val current = models.find { it.id == vm.currentModelId }
    val backend = when (settings.llmBackend) { "gpu" -> "GPU"; "cpu" -> "CPU"; else -> "Auto" }
    val dot = when {
        restarting -> AppTheme.colors.warning
        ready -> AppTheme.colors.success
        else -> AppTheme.colors.textMuted
    }
    val onDisk = vm.currentModelId in downloaded
    // Модель вантажиться при першому запиті, тому «не в пам'яті» ≠ «не завантажено»
    val state = when {
        restarting -> "завантаження…"
        ready -> backend
        onDisk -> "готова"
        downloaded.isEmpty() -> "…"
        else -> "немає на пристрої"
    }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(AppDims.radiusS))
                .background(AppTheme.colors.glassBackground)
                .border(1.dp, AppTheme.colors.glassBorder, RoundedCornerShape(AppDims.radiusS))
                .clickable { open = true }
                .padding(horizontal = AppDims.spaceM, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(AppDims.spaceS))
            Text(
                (current?.name ?: vm.currentModelId) + " · " + state,
                color = AppTheme.colors.textPrimary, fontSize = AppDims.textSmall, maxLines = 1
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AppTheme.colors.textMuted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val available = models.filter { it.id in downloaded }
            if (available.isEmpty()) {
                DropdownMenuItem(text = { Text("Немає завантажених моделей") }, onClick = { open = false }, enabled = false)
            }
            available.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.name + if (m.id == vm.currentModelId) "  ✓" else "") },
                    onClick = {
                        open = false
                        if (m.id != vm.currentModelId) vm.setCurrentModel(m.id)
                    }
                )
            }
        }
    }
}

/** Бульбашка повідомлення: користувач (акцент), асистент (скло), помилка (з дією). Довге натискання — копіювати/поділитись. */
@Composable
private fun MessageBubble(msg: ChatMessage, maxWidth: androidx.compose.ui.unit.Dp, onAction: (String) -> Unit) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val c = AppTheme.colors
    val isUser = msg.role == "user"
    val isError = msg.role == "error"
    val shape = if (isUser) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    val bg = when { isError -> c.danger.copy(alpha = 0.12f); isUser -> c.accent.copy(alpha = 0.20f); else -> c.glassBackground }
    val border = when { isError -> c.danger.copy(alpha = 0.5f); isUser -> c.accent.copy(alpha = 0.4f); else -> c.glassBorder }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box {
            Column(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .clip(shape)
                    .background(bg)
                    .border(1.dp, border, shape)
                    .pointerInput(msg) { detectTapGestures(onLongPress = { menu = true }) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    msg.content,
                    color = if (isError) c.danger else c.textPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontWeight = if (isUser) FontWeight.Medium else FontWeight.Normal
                )
                if (isError && msg.action != null) {
                    TextButton(onClick = { onAction(msg.action) }) {
                        Text(
                            if (msg.action == "restart") "Перезапустити рушій" else "Спробувати ще раз",
                            color = c.accent, fontSize = 13.sp
                        )
                    }
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Копіювати") }, onClick = {
                    menu = false
                    try {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("message", msg.content))
                        android.widget.Toast.makeText(ctx, "Скопійовано", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (_: Throwable) {}
                })
                DropdownMenuItem(text = { Text("Поділитись") }, onClick = {
                    menu = false
                    try {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, msg.content)
                        }
                        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Throwable) {}
                })
            }
        }
    }
}

private fun stateColor(voiceState: VoiceState, c: com.dynamicai.launcher.ui.theme.AppColors): Color = when (voiceState) {
    VoiceState.LISTENING -> c.successLight
    VoiceState.SPEAKING -> c.pink
    VoiceState.THINKING -> c.accentLight
    else -> c.accent
}

@Composable
fun AuroraBackground(voiceState: VoiceState, modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "aurora")
    val phase by tr.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart), label = "phase"
    )
    val stateTint by animateColorAsState(stateColor(voiceState, AppTheme.colors), tween(1200), label = "auroraTint")
    val second = AppTheme.colors.accent
    val third = AppTheme.colors.pink
    val dust = AppTheme.colors.textPrimary
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val a = (2.0 * Math.PI * phase).toFloat()
        fun blob(color: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
            val c = androidx.compose.ui.geometry.Offset(cx, cy)
            drawCircle(brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = c, radius = r), radius = r, center = c)
        }
        blob(stateTint, w * (0.5f + 0.35f * cos(a)), h * (0.35f + 0.2f * sin(2f * a)), w * 0.95f, 0.17f)
        blob(second, w * (0.5f + 0.4f * sin(a)), h * (0.72f + 0.12f * cos(a)), w * 0.8f, 0.12f)
        blob(third, w * (0.5f + 0.3f * cos(2f * a + 1f)), h * (0.5f + 0.3f * sin(a + 2f)), w * 0.7f, 0.08f)

        // Повільні частинки, що пливуть угору й мерехтять
        for (i in 0 until 32) {
            val k = 1 + i % 3
            val sx = ((i * 7919) % 1000) / 1000f
            val sy = ((i * 104729) % 1000) / 1000f
            val y = (((sy - phase * k) % 1f) + 1f) % 1f
            val x = sx + 0.02f * sin(a * k + i)
            val tw = 0.5f + 0.5f * sin(a * (2 + i % 3) + i)
            val tint = if (i % 4 == 0) stateTint else dust
            drawCircle(
                color = tint.copy(alpha = 0.06f + 0.2f * tw),
                radius = (0.8f + (i % 3) * 0.7f).dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(x * w, y * h)
            )
        }
    }
}

@Composable
fun JarvisOrb(voiceState: VoiceState, modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "jarvis")
    val primaryColor by animateColorAsState(stateColor(voiceState, AppTheme.colors), tween(1000), label = "color")

    val phase by tr.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart), label = "phase"
    )
    val pulse by tr.animateFloat(
        initialValue = 0.92f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )
    // Амплітуда «дихання» контуру: спокійно в очікуванні, жвавіше коли говорить
    val wobble by animateFloatAsState(
        targetValue = when (voiceState) {
            VoiceState.SPEAKING -> 0.09f
            VoiceState.THINKING -> 0.06f
            VoiceState.LISTENING -> 0.045f
            else -> 0.025f
        },
        animationSpec = tween(900), label = "wobble"
    )
    val rippleAlpha by animateFloatAsState(
        targetValue = if (voiceState == VoiceState.IDLE) 0.08f else 0.28f,
        animationSpec = tween(900), label = "ripple"
    )
    // Зовнішнє кільце й орбітальні точки плавно з'являються/зникають зі зміною стану
    val outerAlpha by animateFloatAsState(
        targetValue = if (voiceState == VoiceState.IDLE) 0.08f else 0.3f,
        animationSpec = tween(1200), label = "outer"
    )
    val orbitAlpha by animateFloatAsState(
        targetValue = if (voiceState == VoiceState.IDLE) 0.25f else 1f,
        animationSpec = tween(1200), label = "orbit"
    )
    val core = AppTheme.colors.textPrimary

    Canvas(modifier = modifier) {
        val center = this.center
        val baseRadius = size.minDimension * 0.22f
        val a = (2.0 * Math.PI * phase).toFloat()

        // 1. Зовнішнє світіння
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryColor.copy(alpha = 0.18f), Color.Transparent),
                center = center, radius = baseRadius * 2.8f
            ),
            radius = baseRadius * 2.8f, center = center
        )

        // 2. Хвилі: назовні, коли говорить/думає; досередини, коли слухає
        for (j in 0 until 3) {
            val p = ((phase * 8f) + j / 3f) % 1f
            // Плавна поява і зникнення: огинаюча sin²(πp) — на початку й наприкінці хвиля непомітна
            val sp = kotlin.math.sin(Math.PI * p).toFloat()
            val env = sp * sp
            // easeInOut для радіуса: хвиля рухається м'яко, без ривків
            val e = p * p * (3f - 2f * p)
            val r = if (voiceState == VoiceState.LISTENING) baseRadius * (2.1f - 1.2f * e) else baseRadius * (0.9f + 1.4f * e)
            drawCircle(
                color = primaryColor.copy(alpha = rippleAlpha * env),
                radius = r, center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        // 3. Живі органічні кільця (хвилястий контур)
        fun blobPath(radius: Float, ph: Float, dir: Float): androidx.compose.ui.graphics.Path {
            val path = androidx.compose.ui.graphics.Path()
            val steps = 96
            for (n in 0..steps) {
                val th = (n.toFloat() / steps) * 2f * Math.PI.toFloat()
                val r = radius * (1f + wobble * sin(3f * th + dir * 2f * a + ph) + wobble * 0.6f * sin(5f * th - dir * 3f * a + ph * 2f))
                val x = center.x + r * cos(th)
                val y = center.y + r * sin(th)
                if (n == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            return path
        }
        drawPath(blobPath(baseRadius * 1.15f, 0f, 1f), primaryColor.copy(alpha = 0.75f), style = Stroke(width = 2.dp.toPx()))
        drawPath(blobPath(baseRadius * 1.35f, 2f, -1f), primaryColor.copy(alpha = 0.45f), style = Stroke(width = 1.5.dp.toPx()))
        drawPath(blobPath(baseRadius * 1.55f, 4f, 1f), primaryColor.copy(alpha = outerAlpha * (0.8f + 0.2f * kotlin.math.sin(a * 2f))), style = Stroke(width = 1.dp.toPx()))

        // 4. Ядро (пульсуюче світло)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(core.copy(alpha = 0.95f), primaryColor.copy(alpha = 0.65f), Color.Transparent),
                center = center, radius = baseRadius * 0.85f * pulse
            ),
            radius = baseRadius * 0.85f * pulse, center = center
        )

        // 5. Три світні точки, що обертаються навколо
        for (d in 0 until 3) {
            val ang = a * (if (voiceState == VoiceState.THINKING) 3f else 1f) + d * 2.0943952f
            val pos = androidx.compose.ui.geometry.Offset(center.x + baseRadius * 1.9f * cos(ang), center.y + baseRadius * 1.9f * sin(ang))
            drawCircle(color = primaryColor.copy(alpha = 0.35f * orbitAlpha), radius = 7.dp.toPx(), center = pos)
            drawCircle(color = core.copy(alpha = 0.9f * orbitAlpha), radius = 2.5.dp.toPx(), center = pos)
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
        Text(label, color = AppTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** Обрізає відповідь приблизно до maxTokens (≈1,7 символа на токен для кирилиці) по межі речення. */
fun limitAnswer(text: String, maxTokens: Int): String {
    val maxChars = (maxTokens * 2).coerceAtLeast(200)
    if (text.length <= maxChars) return text
    val cut = text.take(maxChars)
    val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?', '…', '\n'))
    return if (end > maxChars / 3) cut.substring(0, end + 1).trim() else cut.trim() + "…"
}

/** Накопичує токени й віддає готові речення (для потокового озвучення). Ігнорує службовий блок <think>. */
class SentenceSplitter(private val onSentence: (String) -> Unit) {
    private val buf = StringBuilder()
    var fedAny = false
        private set

    fun feed(token: String) {
        fedAny = true
        buf.append(token)
        // Пропускаємо «роздуми» Qwen3 (<think>…</think>)
        if (buf.startsWith("<think>")) {
            val end = buf.indexOf("</think>")
            if (end < 0) return
            buf.delete(0, end + "</think>".length)
        }
        while (true) {
            val s = buf.toString()
            // Кінець речення: .!?… + пробіл (або новий рядок), речення не коротше 15 символів; «3.14» не ріжемо
            val m = Regex("[.!?…]+\\s|\\n").find(s, minOf(14, s.length)) ?: break
            val cut = m.range.last + 1
            val sentence = s.substring(0, cut).trim()
            buf.delete(0, cut)
            if (sentence.isNotBlank()) onSentence(sentence)
        }
    }

    /** Залишок тексту після завершення генерації. */
    fun flush() {
        if (buf.startsWith("<think>")) { buf.setLength(0); return }
        val rest = buf.toString().trim()
        buf.setLength(0)
        if (rest.isNotBlank()) onSentence(rest)
    }
}

fun processQuery(
    text: String, history: String, vm: AppListViewModel, scope: kotlinx.coroutines.CoroutineScope,
    onStateChange: (VoiceState) -> Unit, onMessage: (ChatMessage) -> Unit, stopListening: () -> Unit
): kotlinx.coroutines.Job {
    onStateChange(VoiceState.THINKING)
    stopListening()
    onMessage(ChatMessage("status", "Думаю…"))
    return scope.launch {
        try {
            // Прості команди (ліхтарик, відкрити додаток, будильник…) — миттєво, без моделі
            if (vm.settings.value.fastCommands) {
                val fast = try { vm.chatEngine.tryFastRoute(text, vm.apps.value) } catch (e: Exception) { null }
                if (fast != null) {
                    onMessage(ChatMessage("assistant", fast))
                    onStateChange(VoiceState.SPEAKING)
                    vm.tts.speakWithCallback(fast) {
                        kotlinx.coroutines.MainScope().launch {
                            kotlinx.coroutines.delay(400)
                            onStateChange(VoiceState.LISTENING)
                        }
                    }
                    return@launch
                }
            }
            if (!vm.ensureLlmLoaded()) {
                onMessage(ChatMessage("error", "Модель не завантажилась. Перезапустіть рушій або оберіть іншу модель зверху.", "restart"))
                onStateChange(VoiceState.SPEAKING)
                vm.tts.speakWithCallback("Модель не завантажилась.") { onStateChange(VoiceState.LISTENING) }
                return@launch
            }
            // Для MNN-моделей використовуємо простий чат (без агента)
            // для LiteRT (Gemma) — з агентними інструментами
            // Налаштування «Режим агента» вимкнено — теж простий чат (короткий промпт, зручно для діагностики GPU)
            val useAgent = vm.settings.value.useAgent
            val maxTokens = vm.settings.value.maxResponseTokens
            val simple = vm.currentModelId.startsWith("mnn-") || !useAgent
            val job = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]

            // Потокове озвучення: перше речення звучить, поки модель дописує решту
            var streamed = false
            var spokenChars = 0
            val maxSpoken = (maxTokens * 2).coerceAtLeast(200)
            val streamDone = kotlinx.coroutines.CompletableDeferred<Unit>()
            val splitter = SentenceSplitter { sentence ->
                if (job?.isActive == false) return@SentenceSplitter   // «Стоп»: нічого нового не озвучуємо
                if (spokenChars >= maxSpoken) return@SentenceSplitter
                if (!streamed) {
                    streamed = true
                    vm.tts.beginStream { streamDone.complete(Unit) }
                    onStateChange(VoiceState.SPEAKING)
                    stopListening()
                }
                spokenChars += sentence.length
                vm.tts.speakChunk(sentence)
            }

            val rawAnswer = if (simple) {
                vm.chatEngine.simpleAnswer(text, { token -> splitter.feed(token) }, history)
            } else {
                vm.chatEngine.answerWithAgent(text, history)
            }
            // Довга відповідь не влазить у повідомлення і довго озвучується — обрізаємо за реченням
            val answer = limitAnswer(rawAnswer, maxTokens)
            stopListening() // Примусово вимикаємо мікрофон, щоб не зчитав TTS

            // Службові відповіді рушія — показуємо як помилку з кнопкою, а не озвучуємо
            val failed = answer.startsWith("Помилка") || answer == "LLM не відповіла" || answer == "LLM не активна"
            if (failed) {
                onMessage(ChatMessage("error", "Не вдалося отримати відповідь від моделі ($answer).", "retry"))
                onStateChange(VoiceState.LISTENING)
                return@launch
            }

            onMessage(ChatMessage("assistant", answer))
            if (simple && splitter.fedAny) {
                // Дозвучуємо залишок і чекаємо кінця черги озвучення
                splitter.flush()
                if (!streamed) {
                    // Токени не стрімились (напр. fallback без стріму) — озвучуємо готову відповідь одним шматком
                    onStateChange(VoiceState.SPEAKING)
                    vm.tts.speakWithCallback(answer) {
                        kotlinx.coroutines.MainScope().launch {
                            kotlinx.coroutines.delay(400)
                            onStateChange(VoiceState.LISTENING)
                        }
                    }
                } else {
                    vm.tts.endStream()
                    streamDone.await()
                    kotlinx.coroutines.delay(400)
                    onStateChange(VoiceState.LISTENING)
                }
            } else {
                onStateChange(VoiceState.SPEAKING)
                vm.tts.speakWithCallback(answer) {
                    // Чекаємо 400мс, щоб динамік повністю замовк і мікрофон не зчитав відлуння
                    kotlinx.coroutines.MainScope().launch {
                        kotlinx.coroutines.delay(400)
                        onStateChange(VoiceState.LISTENING)
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            onMessage(ChatMessage("error", "Щось пішло не так: ${e.message}", "retry"))
            onStateChange(VoiceState.IDLE)
        }
    }
}

enum class VoiceState { IDLE, LISTENING, THINKING, SPEAKING }
