package com.dynamicai.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dynamicai.launcher.command.FuzzyMatcher
import com.dynamicai.launcher.model.AppInfo
import com.dynamicai.launcher.ui.AlphabetSidebar
import com.dynamicai.launcher.ui.Dock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val ctx = LocalContext.current
            val colorScheme = if (android.os.Build.VERSION.SDK_INT >= 31) {
                androidx.compose.material3.dynamicDarkColorScheme(ctx)
            } else {
                androidx.compose.material3.darkColorScheme()
            }
            MaterialTheme(colorScheme = colorScheme) {
                HomeScreen()
            }
        }
    }
}

@Composable
fun HomeScreen(vm: AppListViewModel = viewModel()) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val dockPkgs by vm.dockPkgs.collectAsStateWithLifecycle()
    val response by vm.response.collectAsStateWithLifecycle()
    val pendingChoice by vm.pendingChoice.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var filtered by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var showOverlay by remember { mutableStateOf(false) }
    var releaseJob by remember { mutableStateOf<Job?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()
    val llmReady = vm.llm.isReady

    fun submit(text: String) {
        if (text.isBlank()) return
        vm.executeCommand(text)
        query = ""
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) submit(text)
        }
    }

    fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажи команду…")
        }
        try {
            voiceLauncher.launch(intent)
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(Unit) { vm.initLlmIfReady() }

    LaunchedEffect(apps, query) {
        filtered = if (query.isBlank()) apps
        else apps
            .map { it to FuzzyMatcher.score(it.label, query) }
            .filter { it.second >= 35 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    val letters = remember(filtered) {
        filtered.map { it.label.firstOrNull()?.uppercaseChar() ?: '#' }
            .distinct()
            .sorted()
    }

    val dockApps = remember(dockPkgs, apps) {
        apps.filter { it.packageName in dockPkgs }
    }

    LaunchedEffect(activeLetter) {
        if (activeLetter != null) showOverlay = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 64.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 20.sp),
                        cursorBrush = SolidColor(Color.White),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (query.isNotBlank()) {
                                when {
                                    vm.isCommand(query) -> submit(query)
                                    filtered.isNotEmpty() -> {
                                        vm.launch(filtered.first().packageName)
                                        query = ""
                                    }
                                    else -> submit(query)
                                }
                            }
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text("Пошук або команда…", color = Color.Gray, fontSize = 20.sp)
                            }
                            inner()
                        }
                    )
                }
                IconButton(onClick = { startVoice() }) {
                    Icon(Icons.Default.Mic, contentDescription = "Голос", tint = Color.White)
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Налаштування", tint = Color.White)
                }
                if (query.isNotBlank()) {
                    IconButton(onClick = { submit(query) }) {
                        Icon(Icons.Default.Send, contentDescription = "Виконати", tint = Color.White)
                    }
                }
            }

            if (query.isBlank() && history.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    history.take(5).forEach { cmd ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF1A1A1A))
                                .clickable { submit(cmd) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (cmd.length > 28) cmd.take(28) + "…" else cmd,
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            if (response.isNotBlank()) {
                Text(
                    text = response,
                    color = Color(0xFF64B5F6),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }

            HorizontalDivider(color = Color.DarkGray)

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = if (dockApps.isNotEmpty() && query.isBlank()) 96.dp else 8.dp
                )
            ) {
                if (filtered.isEmpty() && query.isNotBlank()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Нічого не знайдено за запитом \"$query\"",
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Спробуй: відкрий telegram · пошук kotlin · ліхтарик",
                                color = Color.DarkGray,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                items(filtered, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        inDock = app.packageName in dockPkgs,
                        onClick = { vm.launch(app.packageName) },
                        onLongClick = { vm.toggleDock(app.packageName) }
                    )
                }
            }
        }

        if (letters.isNotEmpty() && query.isBlank()) {
            AlphabetSidebar(
                letters = letters,
                activeLetter = activeLetter,
                onLetterSelected = { letter ->
                    releaseJob?.cancel()
                    activeLetter = letter
                    showOverlay = true
                    scrollJob?.cancel()
                    scrollJob = scope.launch {
                        val idx = filtered.indexOfFirst {
                            (it.label.firstOrNull()?.uppercaseChar() ?: '#') == letter
                        }
                        if (idx >= 0) {
                            val current = listState.firstVisibleItemIndex
                            val distance = kotlin.math.abs(idx - current)
                            when {
                                distance > 60 -> {
                                    listState.scrollToItem(idx - 5)
                                    listState.animateScrollToItem(idx)
                                }
                                distance > 20 -> {
                                    listState.scrollToItem(idx - 3)
                                    listState.animateScrollToItem(idx)
                                }
                                else -> listState.animateScrollToItem(idx)
                            }
                        }
                    }
                },
                onLetterReleased = { isTap ->
                    releaseJob?.cancel()
                    releaseJob = scope.launch {
                        delay(if (isTap) 350 else 100)
                        showOverlay = false
                        delay(180)
                        activeLetter = null
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )
        }

        AnimatedVisibility(
            visible = showOverlay && activeLetter != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Text(
                    text = activeLetter?.toString() ?: "",
                    color = Color.White,
                    fontSize = 64.sp
                )
            }
        }

        if (dockApps.isNotEmpty() && query.isBlank()) {
            Dock(
                apps = dockApps,
                onLaunch = { vm.launch(it) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        downloadProgress?.let { p ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Завантаження моделі", color = Color.White) },
                text = {
                    Column {
                        when (p) {
                            is com.dynamicai.launcher.llm.ModelDownloader.Progress.Downloading -> {
                                Text(
                                    "${p.downloadedMb} / ${p.totalMb} MB",
                                    color = Color.White
                                )
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { p.percent / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(4.dp))
                                Text("${p.percent}%", color = Color.Gray, fontSize = 12.sp)
                            }
                            is com.dynamicai.launcher.llm.ModelDownloader.Progress.Error -> {
                                Text("Помилка: ${p.message}", color = Color(0xFFFF6B6B))
                            }
                            else -> {}
                        }
                    }
                },
                confirmButton = {},
                containerColor = Color(0xFF1A1A1A)
            )
        }

        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text("Налаштування", color = Color.White) },
                text = {
                    Column {
                        TextButton(onClick = {
                            vm.clearHistory()
                            showSettings = false
                        }) {
                            Text("Очистити історію команд", color = Color.White)
                        }
                        if (!vm.downloader.isDownloaded) {
                            TextButton(onClick = {
                                showSettings = false
                                vm.downloadModel()
                            }) {
                                Text("Завантажити LLM (2.6 ГБ)", color = Color(0xFF64B5F6))
                            }
                        } else if (!vm.llm.isReady) {
                            TextButton(onClick = {
                                vm.initLlmIfReady()
                                showSettings = false
                            }) {
                                Text("Ініціалізувати LLM", color = Color(0xFF64B5F6))
                            }
                        } else {
                            Text("LLM активна ✓", color = Color(0xFF4CAF50), fontSize = 13.sp)
                        }
                        Text(
                            "DynamicAI · ${com.dynamicai.launcher.BuildConfig.VERSION_NAME}",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettings = false }) {
                        Text("OK", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1A1A1A)
            )
        }

        pendingChoice?.let { candidates ->
            AlertDialog(
                onDismissRequest = { vm.dismissChoice() },
                title = { Text("Обери застосунок", color = Color.White) },
                text = {
                    Column {
                        candidates.forEach { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.onAppChosen(app) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
                                    contentDescription = app.label,
                                    modifier = Modifier.size(36.dp),
                                    tint = Color.Unspecified
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(app.label, color = Color.White, fontSize = 16.sp)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { vm.dismissChoice() }) {
                        Text("Скасувати", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1A1A1A)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    app: AppInfo,
    inDock: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
            contentDescription = app.label,
            modifier = Modifier.size(44.dp),
            tint = Color.Unspecified
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = app.label,
            color = if (inDock) Color(0xFF64B5F6) else Color.White,
            fontSize = 18.sp
        )
    }
}
