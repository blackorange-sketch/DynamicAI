package com.dynamicai.launcher.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dynamicai.launcher.AppListViewModel
import com.dynamicai.launcher.BuildConfig
import com.dynamicai.launcher.model.AppInfo
import com.dynamicai.launcher.suggestion.Suggestion
import com.dynamicai.launcher.suggestion.stableKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(vm: AppListViewModel = viewModel()) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val dockPkgs by vm.dockPkgs.collectAsStateWithLifecycle()
    val response by vm.response.collectAsStateWithLifecycle()
    val pendingChoice by vm.pendingChoice.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val hidden by vm.hidden.collectAsStateWithLifecycle()
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()

    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current

    var query by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var searchMode by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }

    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var showOverlay by remember { mutableStateOf(false) }
    var releaseJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(searchMode) {
        if (searchMode) {
            delay(50)
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.loadContacts() }

    LaunchedEffect(Unit) {
        vm.initLlmIfReady()
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.loadContacts() else permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    fun closeSearch() {
        query = ""
        searchMode = false
        keyboard?.hide()
    }

    fun submit(text: String) {
        if (text.isBlank()) return
        vm.executeCommand(text)
        closeSearch()
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) submit(text)
        }
    }

    fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажи команду…")
        }
        try { voiceLauncher.launch(intent) } catch (_: Exception) {}
    }

    LaunchedEffect(query) { vm.updateQuery(query) }

    val letters = remember(suggestions, query) {
        if (query.isNotBlank()) emptyList()
        else suggestions.mapNotNull {
            (it as? Suggestion.App)?.app?.label?.firstOrNull()?.uppercaseChar()
        }.distinct().sorted()
    }

    val dockApps = remember(dockPkgs, apps, hidden) {
        apps.filter { it.packageName in dockPkgs && it.packageName !in hidden }
    }

    LaunchedEffect(activeLetter) { if (activeLetter != null) showOverlay = true }

    val showSearchField = searchMode || query.isNotBlank()

    if (showSettings) {
        SettingsScreen(
            settings = settings,
            onSettingsChange = { vm.updateSettings(it) },
            hiddenCount = hidden.size,
            onClearHistory = { vm.clearHistory() },
            onRefreshContacts = { vm.loadContacts() },
            onClearHidden = { vm.clearHidden() },
            onDownloadLlm = { vm.downloadModel() },
            llmDownloaded = vm.downloader.isDownloaded,
            llmReady = vm.llm.isReady,
            versionName = BuildConfig.VERSION_NAME,
            onClose = { showSettings = false }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 64.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            if (showSearchField) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
                                        suggestions.isNotEmpty() -> {
                                            vm.executeSuggestion(suggestions.first())
                                            closeSearch()
                                        }
                                        else -> submit(query)
                                    }
                                }
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .focusRequester(focusRequester),
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
                    if (query.isNotBlank()) {
                        IconButton(onClick = {
                            if (suggestions.isNotEmpty()) {
                                vm.executeSuggestion(suggestions.first())
                                closeSearch()
                            } else submit(query)
                        }) {
                            Icon(Icons.Default.Send, contentDescription = "Виконати", tint = Color.White)
                        }
                    } else {
                        IconButton(onClick = { closeSearch() }) {
                            Icon(Icons.Default.Close, contentDescription = "Закрити", tint = Color.White)
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
                                    if (cmd.length > 28) cmd.take(28) + "…" else cmd,
                                    color = Color.White, fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color.DarkGray)
            }

            if (response.isNotBlank()) {
                Text(
                    text = response,
                    color = Color(0xFF64B5F6),
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = if (dockApps.isNotEmpty()) 96.dp else 8.dp
                )
            ) {
                if (suggestions.isEmpty() && query.isNotBlank()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Нічого не знайдено за \"$query\"", color = Color.Gray, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Спробуй: ліхтарик · wifi · Аня",
                                color = Color.DarkGray, fontSize = 13.sp
                            )
                        }
                    }
                }
                items(suggestions, key = { it.stableKey() }) { s ->
                    when (s) {
                        is Suggestion.Action -> SuggestionActionRow(s) {
                            vm.executeSuggestion(s); closeSearch()
                        }
                        is Suggestion.Setting -> SuggestionSettingRow(s) {
                            vm.executeSuggestion(s); closeSearch()
                        }
                        is Suggestion.Contact -> SuggestionContactRow(s) {
                            vm.executeSuggestion(s); closeSearch()
                        }
                        is Suggestion.App -> AppRow(
                            app = s.app,
                            inDock = s.app.packageName in dockPkgs,
                            iconSizeDp = settings.iconSizeDp,
                            fontSizeSp = settings.fontSizeSp,
                            showLabel = settings.showLabels,
                            onClick = { vm.launch(s.app.packageName) },
                            onLongClick = { menuApp = s.app }
                        )
                    }
                }
            }
        }

        if (letters.isNotEmpty() && query.isBlank() && !searchMode) {
            AlphabetSidebar(
                letters = letters,
                activeLetter = activeLetter,
                onLetterSelected = { letter ->
                    releaseJob?.cancel()
                    activeLetter = letter
                    showOverlay = true
                    scrollJob?.cancel()
                    scrollJob = scope.launch {
                        val idx = suggestions.indexOfFirst {
                            it is Suggestion.App && (it.app.label.firstOrNull()?.uppercaseChar() ?: '#') == letter
                        }
                        if (idx >= 0) {
                            val current = listState.firstVisibleItemIndex
                            val d = kotlin.math.abs(idx - current)
                            when {
                                d > 60 -> { listState.scrollToItem(idx - 5); listState.animateScrollToItem(idx) }
                                d > 20 -> { listState.scrollToItem(idx - 3); listState.animateScrollToItem(idx) }
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
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
        }

        AnimatedVisibility(
            visible = showOverlay && activeLetter != null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))
            ) {
                Text(activeLetter?.toString() ?: "", color = Color.White, fontSize = 64.sp)
            }
        }

        if (dockApps.isNotEmpty()) {
            Dock(
                apps = dockApps,
                iconSizeDp = settings.dockIconSizeDp,
                onLaunch = { vm.launch(it) },
                onSearch = { searchMode = true },
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
                                Text("${p.downloadedMb} / ${p.totalMb} MB", color = Color.White)
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { p.percent / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(4.dp))
                                Text("${p.percent}%", color = Color.Gray, fontSize = 12.sp)
                            }
                            is com.dynamicai.launcher.llm.ModelDownloader.Progress.Error ->
                                Text("Помилка: ${p.message}", color = Color(0xFFFF6B6B))
                            else -> {}
                        }
                    }
                },
                confirmButton = {},
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
                dismissButton = { TextButton(onClick = { vm.dismissChoice() }) { Text("Скасувати", color = Color.White) } },
                containerColor = Color(0xFF1A1A1A)
            )
        }

        menuApp?.let { app ->
            AppContextMenu(
                app = app,
                inDock = app.packageName in dockPkgs,
                onDismiss = { menuApp = null },
                onToggleDock = { vm.toggleDock(app.packageName); menuApp = null },
                onHide = { vm.hideApp(app.packageName); menuApp = null },
                onInfo = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${app.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try { ctx.startActivity(intent) } catch (_: Exception) {}
                    menuApp = null
                }
            )
        }
    }
}

@Composable
fun AppContextMenu(
    app: AppInfo,
    inDock: Boolean,
    onDismiss: () -> Unit,
    onToggleDock: () -> Unit,
    onHide: () -> Unit,
    onInfo: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label, color = Color.White, fontSize = 18.sp) },
        text = {
            Column {
                MenuItem(if (inDock) "Прибрати з дока" else "Додати в док", onToggleDock)
                MenuItem("Приховати зі списку", onHide)
                MenuItem("Інформація про застосунок", onInfo)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати", color = Color.White) } },
        containerColor = Color(0xFF1A1A1A)
    )
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    )
}

@Composable
fun SuggestionActionRow(s: Suggestion.Action, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Bolt, null, tint = Color(0xFF64B5F6)) }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(s.label, color = Color.White, fontSize = 17.sp)
            s.subtitle?.let { Text(it, color = Color.Gray, fontSize = 12.sp) }
        }
    }
}

@Composable
fun SuggestionSettingRow(s: Suggestion.Setting, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Settings, null, tint = Color(0xFFB0BEC5)) }
        Spacer(Modifier.width(16.dp))
        Text(s.label, color = Color.White, fontSize = 17.sp)
    }
}

@Composable
fun SuggestionContactRow(s: Suggestion.Contact, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Person, null, tint = Color(0xFF81C784)) }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(s.name, color = Color.White, fontSize = 17.sp)
            Text(s.subtitle, color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    app: AppInfo,
    inDock: Boolean,
    iconSizeDp: Int,
    fontSizeSp: Int,
    showLabel: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
            contentDescription = app.label,
            modifier = Modifier.size(iconSizeDp.dp),
            tint = Color.Unspecified
        )
        if (showLabel) {
            Spacer(Modifier.width(16.dp))
            Text(
                app.label,
                color = if (inDock) Color(0xFF64B5F6) else Color.White,
                fontSize = fontSizeSp.sp
            )
        }
    }
}
