package com.dynamicai.launcher.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlinx.coroutines.withTimeoutOrNull

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
    val folders by vm.folders.collectAsStateWithLifecycle()
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()

    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current

    var query by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var searchMode by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    var folderMenuApp by remember { mutableStateOf<AppInfo?>(null) }
    var folderMenuFolder by remember { mutableStateOf<com.dynamicai.launcher.folder.Folder?>(null) }
    var folderHeaderMenu by remember { mutableStateOf<com.dynamicai.launcher.folder.Folder?>(null) }
    var openFolder by remember { mutableStateOf<com.dynamicai.launcher.folder.Folder?>(null) }
    var selectedTabFolderId by remember { mutableStateOf<String?>(null) }
    var showMainMenu by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var newFolderSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showCreateFolder by remember { mutableStateOf<AppInfo?>(null) }
    var createFolderName by remember { mutableStateOf("") }
    var addToFolderPicker by remember { mutableStateOf<AppInfo?>(null) }
    var overlayActive by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }
    var overlayPermissionGranted by remember {
        mutableStateOf(Settings.canDrawOverlays(ctx))
    }

    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var showOverlay by remember { mutableStateOf(false) }
    var listVisible by remember { mutableStateOf(false) }
    var listTimerJob by remember { mutableStateOf<Job?>(null) }
    var releaseJob by remember { mutableStateOf<Job?>(null) }



    BackHandler(enabled = showSettings) {
        showSettings = false
    }
    BackHandler(enabled = !showSettings && openFolder != null) {
        openFolder = null
    }
    BackHandler(enabled = !showSettings && openFolder == null && drawerOpen) {
        drawerOpen = false
    }
    BackHandler(enabled = !showSettings && openFolder == null && !drawerOpen && (searchMode || query.isNotBlank())) {
        query = ""
        searchMode = false
        keyboard?.hide()
    }

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

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        overlayPermissionGranted = Settings.canDrawOverlays(ctx)
    }

    fun requestOverlayPermission() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${ctx.packageName}")
            )
            overlayPermissionLauncher.launch(intent)
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) {
        overlayPermissionGranted = Settings.canDrawOverlays(ctx)
    }

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

    fun showListTemporarily() {
        listVisible = true
        listTimerJob?.cancel()
        listTimerJob = scope.launch {
            delay(3000)
            listVisible = false
            activeLetter = null
        }
    }

    fun hideListImmediately() {
        listTimerJob?.cancel()
        listVisible = false
        activeLetter = null
    }

    fun launchAppWithAnimation(app: AppInfo) {
        hideListImmediately()
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        vm.launch(app.packageName)
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
            overlayActive = overlayActive,
            overlayPermissionGranted = overlayPermissionGranted,
            onToggleOverlay = {
                if (overlayActive) {
                    com.dynamicai.launcher.overlay.FloatingDockService.stop(ctx)
                    overlayActive = false
                } else {
                    com.dynamicai.launcher.overlay.FloatingDockService.start(ctx)
                    overlayActive = true
                }
            },
            onRequestOverlayPermission = { requestOverlayPermission() },
            llmDownloaded = vm.downloader.isDownloaded,
            llmReady = vm.llm.isReady,
            versionName = BuildConfig.VERSION_NAME,
            onClose = { showSettings = false }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        // Тільки якщо ми на головному екрані (не в drawer/search/settings)
                        if (!showSettings && !drawerOpen && !showSearchField && query.isBlank()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showMainMenu = true
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 64.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            if (!showSearchField) {
                HomeWidget(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))

                        if (folders.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            FolderTabs(
                                folders = folders,
                                apps = apps,
                                selectedFolderId = selectedTabFolderId,
                                iconSizeDp = settings.iconSizeDp,
                                onSelectFolder = { folder ->
                                    selectedTabFolderId = folder?.id
                                },
                                onLaunchApp = { app -> launchAppWithAnimation(app) }
                            )
                        }
            }

            if (!showSearchField) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Налаштування", tint = Color.White)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

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

            Box(modifier = Modifier.weight(1f)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = listVisible || searchMode || query.isNotBlank(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = 8.dp
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
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            vm.executeSuggestion(s); closeSearch()
                        }
                        is Suggestion.Setting -> SuggestionSettingRow(s) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            vm.executeSuggestion(s); closeSearch()
                        }
                        is Suggestion.Contact -> SuggestionContactRow(s) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            vm.executeSuggestion(s); closeSearch()
                        }
                        is Suggestion.App -> AppRow(
                            app = s.app,
                            inDock = s.app.packageName in dockPkgs,
                            iconSizeDp = settings.iconSizeDp,
                            fontSizeSp = settings.fontSizeSp,
                            showLabel = settings.showLabels,
                            onClick = { launchAppWithAnimation(s.app) },
                            onLongClick = { menuApp = s.app }
                        )
                    }
                }
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
                    showListTemporarily()
                    scrollJob?.cancel()
                    scrollJob = scope.launch {
                        val idx = suggestions.indexOfFirst {
                            it is Suggestion.App && (it.app.label.firstOrNull()?.uppercaseChar() ?: '#') == letter
                        }
                        if (idx >= 0) {
                            val current = listState.firstVisibleItemIndex
                            val d = kotlin.math.abs(idx - current)
                            when {
                                d > 60 -> { listState.scrollToItem((idx - 5).coerceAtLeast(0)); listState.animateScrollToItem(idx) }
                                d > 20 -> { listState.scrollToItem((idx - 3).coerceAtLeast(0)); listState.animateScrollToItem(idx) }
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

        if (dockPkgs.isNotEmpty()) {
            val dockItems = remember(dockPkgs, apps, folders) {
                dockPkgs.mapNotNull { id ->
                    if (id.startsWith("folder:")) {
                        val fid = id.removePrefix("folder:")
                        val f = folders.firstOrNull { it.id == fid } ?: return@mapNotNull null
                        val firstApp = apps.firstOrNull { it.packageName in f.packageNames }
                        DockItem(
                            id = id,
                            label = f.name,
                            icon = firstApp?.icon,
                            isFolder = true
                        )
                    } else {
                        val a = apps.firstOrNull { it.packageName == id } ?: return@mapNotNull null
                        DockItem(
                            id = id,
                            label = a.label,
                            icon = a.icon,
                            isFolder = false
                        )
                    }
                }
            }
            Dock(
                items = dockItems,
                iconSizeDp = settings.dockIconSizeDp,
                onLaunch = { item ->
                    if (item.isFolder) {
                        val fid = item.id.removePrefix("folder:")
                        openFolder = folders.firstOrNull { it.id == fid }
                    } else {
                        val app = apps.firstOrNull { it.packageName == item.id }
                        if (app != null) launchAppWithAnimation(app)
                    }
                },
                onSearch = { searchMode = true },
                onSwipeUp = { drawerOpen = true },
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

        val drawerApps = remember(apps, hidden, folders) {
            val folderPkgs = folders.flatMap { it.packageNames }.toSet()
            apps.filter { it.packageName !in hidden && it.packageName !in folderPkgs }
        }
        AppDrawer(
            apps = drawerApps,
            onLaunch = { app -> launchAppWithAnimation(app) },
            onDismiss = { drawerOpen = false },
            visible = drawerOpen
        )

        // Діалог створення папки
        showCreateFolder?.let { app ->
            AlertDialog(
                onDismissRequest = { showCreateFolder = null; createFolderName = "" },
                title = { Text("Нова папка", color = Color.White) },
                text = {
                    Column {
                        Text("Застосунок: ${app.label}", color = Color.Gray, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = createFolderName,
                            onValueChange = { createFolderName = it },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            decorationBox = { inner ->
                                if (createFolderName.isEmpty()) {
                                    Text("Назва папки", color = Color.Gray, fontSize = 16.sp)
                                }
                                inner()
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (createFolderName.isNotBlank()) {
                            vm.createFolder(createFolderName, listOf(app.packageName))
                        }
                        showCreateFolder = null
                        createFolderName = ""
                    }) { Text("Створити", color = Color(0xFF64B5F6)) }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateFolder = null; createFolderName = "" }) {
                        Text("Скасувати", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1A1A1A)
            )
        }

        // Діалог вибору папки (додати застосунок до існуючої)
        addToFolderPicker?.let { app ->
            AlertDialog(
                onDismissRequest = { addToFolderPicker = null },
                title = { Text("Додати ${app.label} до…", color = Color.White) },
                text = {
                    Column {
                        if (folders.isEmpty()) {
                            Text("Немає папок", color = Color.Gray, fontSize = 14.sp)
                        } else {
                            folders.forEach { f ->
                                Text(
                                    f.name,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerInput(f.id) {
                                            detectTapGestures {
                                                vm.addToFolder(f.id, app.packageName)
                                                addToFolderPicker = null
                                            }
                                        }
                                        .padding(vertical = 12.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { addToFolderPicker = null }) {
                        Text("Скасувати", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1A1A1A)
            )
        }

        // Діалог long-press на застосунок у папці
        folderMenuApp?.let { app ->
            AlertDialog(
                onDismissRequest = { folderMenuApp = null; folderMenuFolder = null },
                title = { Text(app.label, color = Color.White, fontSize = 18.sp) },
                text = {
                    Column {
                        Text(
                            "Прибрати з папки",
                            color = Color.White,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(app.packageName) {
                                    detectTapGestures {
                                        folderMenuFolder?.let { f ->
                                            vm.removeFromFolder(f.id, app.packageName)
                                        }
                                        folderMenuApp = null
                                        folderMenuFolder = null
                                    }
                                }
                                .padding(vertical = 12.dp)
                        )
                        Text(
                            "Запустити",
                            color = Color.White,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(app.packageName) {
                                    detectTapGestures {
                                        launchAppWithAnimation(app)
                                        folderMenuApp = null
                                        folderMenuFolder = null
                                    }
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { folderMenuApp = null; folderMenuFolder = null }) {
                        Text("Скасувати", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1A1A1A)
            )
        }

        // Діалог long-press на заголовок папки
        folderHeaderMenu?.let { folder ->
            AlertDialog(
                onDismissRequest = { folderHeaderMenu = null },
                title = { Text(folder.name, color = Color.White, fontSize = 18.sp) },
                text = {
                    Column {
                        Text(
                            "Видалити папку (застосунки залишаться)",
                            color = Color(0xFFFF6B6B),
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(folder.id) {
                                    detectTapGestures {
                                        vm.deleteFolder(folder.id)
                                        folderHeaderMenu = null
                                    }
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { folderHeaderMenu = null }) {
                        Text("Скасувати", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1A1A1A)
            )
        }

        // ==== Головне меню (long press на порожньому місці) ====
        if (showMainMenu) {
            AlertDialog(
                onDismissRequest = { showMainMenu = false },
                title = { Text("Меню", color = Color.White, fontSize = 18.sp) },
                text = {
                    Column {
                        MenuItem("Створити папку") {
                            newFolderName = ""
                            newFolderSelection = emptySet()
                            showMainMenu = false
                            showNewFolderDialog = true
                        }
                        MenuItem("Оновити контакти") {
                            vm.loadContacts()
                            showMainMenu = false
                        }
                        MenuItem("Очистити історію команд") {
                            vm.clearHistory()
                            showMainMenu = false
                        }
                        MenuItem("Налаштування") {
                            showMainMenu = false
                            showSettings = true
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showMainMenu = false }) { Text("Скасувати", color = Color.White) }
                },
                containerColor = Color(0xFF1A1A1A)
            )
        }

        // ==== Діалог нової папки ====
        if (showNewFolderDialog) {
            AlertDialog(
                onDismissRequest = { showNewFolderDialog = false },
                title = { Text("Нова папка", color = Color.White) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            decorationBox = { inner ->
                                if (newFolderName.isEmpty()) {
                                    Text("Назва папки", color = Color.Gray, fontSize = 16.sp)
                                }
                                inner()
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Обери застосунки:", color = Color.Gray, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                        ) {
                            items(apps) { app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerInput(app.packageName) {
                                            detectTapGestures {
                                                newFolderSelection = if (app.packageName in newFolderSelection)
                                                    newFolderSelection - app.packageName
                                                else
                                                    newFolderSelection + app.packageName
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.Checkbox(
                                        checked = app.packageName in newFolderSelection,
                                        onCheckedChange = { checked ->
                                            newFolderSelection = if (checked)
                                                newFolderSelection + app.packageName
                                            else
                                                newFolderSelection - app.packageName
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        bitmap = app.icon.toBitmap(72, 72).asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = Color.Unspecified
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(app.label, color = Color.White, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newFolderName.isNotBlank() && newFolderSelection.isNotEmpty()) {
                                vm.createFolder(newFolderName, newFolderSelection.toList())
                            }
                            showNewFolderDialog = false
                        },
                        enabled = newFolderName.isNotBlank() && newFolderSelection.isNotEmpty()
                    ) { Text("Створити", color = Color(0xFF64B5F6)) }
                },
                dismissButton = {
                    TextButton(onClick = { showNewFolderDialog = false }) {
                        Text("Скасувати", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1A1A1A)
            )
        }

        // ==== Діалог відкритої папки ====
        openFolder?.let { folder ->
            val folderApps = apps.filter { it.packageName in folder.packageNames }
            AlertDialog(
                onDismissRequest = { openFolder = null },
                title = { Text(folder.name, color = Color.White, fontSize = 18.sp) },
                text = {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                    ) {
                        items(folderApps, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(app.packageName) {
                                        detectTapGestures {
                                            launchAppWithAnimation(app)
                                            openFolder = null
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
                                    contentDescription = app.label,
                                    modifier = Modifier.size(40.dp),
                                    tint = Color.Unspecified
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(app.label, color = Color.White, fontSize = 16.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        // Додати папку в док
                        if (folder.id !in dockPkgs) {
                            vm.toggleDock("folder:${folder.id}")
                        } else {
                            vm.toggleDock("folder:${folder.id}")
                        }
                        openFolder = null
                    }) {
                        Text(
                            if ("folder:${folder.id}" in dockPkgs) "Прибрати з дока"
                            else "Додати в док",
                            color = Color(0xFF64B5F6)
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { openFolder = null }) {
                        Text("Закрити", color = Color.White)
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
                dismissButton = { TextButton(onClick = { vm.dismissChoice() }) { Text("Скасувати", color = Color.White) } },
                containerColor = Color(0xFF1A1A1A)
            )
        }

        menuApp?.let { app ->
            val inFolder = vm.folderOf(app.packageName)
            AlertDialog(
                onDismissRequest = { menuApp = null },
                title = { Text(app.label, color = Color.White, fontSize = 18.sp) },
                text = {
                    Column {
                        if (inFolder == null) {
                            MenuItem(if (app.packageName in dockPkgs) "Прибрати з дока" else "Додати в док") {
                                vm.toggleDock(app.packageName); menuApp = null
                            }
                            MenuItem("Створити папку з цим застосунком") {
                                showCreateFolder = app; menuApp = null
                            }
                            MenuItem("Додати до існуючої папки") {
                                addToFolderPicker = app; menuApp = null
                            }
                        } else {
                            MenuItem("Прибрати з папки «${inFolder.name}»") {
                                vm.removeFromFolder(inFolder.id, app.packageName)
                                menuApp = null
                            }
                        }
                        MenuItem("Приховати зі списку") {
                            vm.hideApp(app.packageName); menuApp = null
                        }
                        MenuItem("Інформація про застосунок") {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${app.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try { ctx.startActivity(intent) } catch (_: Exception) {}
                            menuApp = null
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { menuApp = null }) { Text("Скасувати", color = Color.White) }
                },
                containerColor = Color(0xFF1A1A1A)
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
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(app.packageName) {
                detectTapGestures(onTap = { onClick() })
            }
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
