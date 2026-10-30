package com.dynamicai.launcher.ui

import com.dynamicai.launcher.ui.theme.AppTheme
import com.dynamicai.launcher.ui.theme.DynamicAITheme
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
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
fun HomeScreen(vm: AppListViewModel = viewModel(), hubEvent: com.dynamicai.launcher.HubEvent? = null) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val dockPkgs by vm.dockPkgs.collectAsStateWithLifecycle()
    val response by vm.response.collectAsStateWithLifecycle()
    val pendingChoice by vm.pendingChoice.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    // background береться з зовнішньої теми (MainActivity): прозорий або чорний залежно від кастомного фону
    DynamicAITheme(themeId = settings.appTheme, backgroundOverride = androidx.compose.material3.MaterialTheme.colorScheme.background) {
    val hidden by vm.hidden.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val recentTasks by vm.recentTasks.collectAsStateWithLifecycle()
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()
    val llmReady by vm.llmReady.collectAsStateWithLifecycle()
    val dockAppeared by vm.dockAppeared.collectAsStateWithLifecycle()

    // Плавна поява іконок — тільки один раз, потім завжди 1
    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.tween(450),
        label = "contentAlpha"
    )

    val ctx = LocalContext.current
    val statusBarCtrl = remember { com.dynamicai.launcher.system.StatusBarController(ctx.applicationContext) }
    val keyboard = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current

    var query by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showWallpaper by remember { mutableStateOf(false) }
    var showDockEditor by remember { mutableStateOf(false) }
    var showFolderManager by remember { mutableStateOf(false) }
    var showMacroManager by remember { mutableStateOf(false) }
    var showNotesScreen by remember { mutableStateOf(false) }
    val placedWidgetsStorage = remember { com.dynamicai.launcher.widget.WidgetsStorage(ctx) }
    var placedWidgets by remember { mutableStateOf<List<com.dynamicai.launcher.widget.PlacedWidget>>(emptyList()) }
    var showAddWidgetDialog by remember { mutableStateOf(false) }
    var currentSettingsCategory by remember { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableStateOf(0) }
    val pageCount = remember(placedWidgets) {
        (placedWidgets.maxOfOrNull { it.page } ?: 0) + 1
    }
    LaunchedEffect(Unit) { placedWidgets = placedWidgetsStorage.ensureDefaults() }
    var responseOverlayVisible by remember { mutableStateOf(false) }
    var responseOverlayText by remember { mutableStateOf("") }
    var showRecent by remember { mutableStateOf(false) }
    var showWeatherDetail by remember { mutableStateOf(false) }
    var searchMode by remember { mutableStateOf(false) }
    var widgetSettingsFor by remember { mutableStateOf<com.dynamicai.launcher.widget.PlacedWidget?>(null) }
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
    var drawerCategory by remember { mutableStateOf("all") }
    // Анімація зникнення HomeScreen при відкритті drawer
    val homeAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (drawerOpen) 0f else 1f,
        animationSpec = androidx.compose.animation.core.tween(if (drawerOpen) 200 else 260),
        label = "homeAlpha"
    )
    val homeScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (drawerOpen) 0.90f else 1f,
        animationSpec = androidx.compose.animation.core.tween(if (drawerOpen) 240 else 280),
        label = "homeScale"
    )
    var launchingApp by remember { mutableStateOf<AppInfo?>(null) }
    var overlayPermissionGranted by remember {
        mutableStateOf(Settings.canDrawOverlays(ctx))
    }

    val focusRequester = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val listState = rememberLazyListState()
    val drawerGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var showOverlay by remember { mutableStateOf(false) }
    var listVisible by remember { mutableStateOf(false) }
    var listTimerJob by remember { mutableStateOf<Job?>(null) }
    var releaseJob by remember { mutableStateOf<Job?>(null) }



    // Скидаємо таймер при прокручуванні списку
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (scrolling && listVisible) {
                    listTimerJob?.cancel()
                } else if (!scrolling && listVisible) {
                    listTimerJob?.cancel()
                    listTimerJob = scope.launch {
                        delay(3000)
                        listVisible = false
                        activeLetter = null
                    }
                }
            }
    }

    // Системний жест «назад»: один обробник із пріоритетом як у порядку відображення екранів,
    // щоб верхній екран закривався першим (раніше «назад» з макросів/нотаток закривав усі налаштування).
    val anyOverlay = showWeatherDetail || showNotesScreen || showMacroManager || showFolderManager ||
        showRecent || showDockEditor || showWallpaper || showSettings
    BackHandler(enabled = anyOverlay) {
        when {
            showWeatherDetail -> showWeatherDetail = false
            showNotesScreen -> showNotesScreen = false
            showMacroManager -> showMacroManager = false
            showFolderManager -> showFolderManager = false
            showRecent -> showRecent = false
            showDockEditor -> showDockEditor = false
            showWallpaper -> showWallpaper = false
            showSettings -> {
                // Із розділу — назад до меню налаштувань, із меню — закрити налаштування
                if (currentSettingsCategory != null) currentSettingsCategory = null else showSettings = false
            }
        }
    }
    LaunchedEffect(showSettings) { if (!showSettings) currentSettingsCategory = null }
    // Поглинач back — на головному екрані back нічого не робить
    // Найнижчий пріоритет (зареєстровано першим): back ніколи не «провалюється» до системи
    // (на деяких прошивках це відкривало системні «Нещодавні»).
    BackHandler(enabled = !anyOverlay) {
        // нічого не робимо
    }
    // Відкрита вкладка-папка на головному екрані — back закриває її
    BackHandler(enabled = !anyOverlay && selectedTabFolderId != null && openFolder == null && !drawerOpen && !searchMode) {
        selectedTabFolderId = null
    }
    BackHandler(enabled = !anyOverlay && openFolder != null) {
        openFolder = null
    }
    BackHandler(enabled = !anyOverlay && openFolder == null && drawerOpen) {
        drawerOpen = false
    }
    BackHandler(enabled = !anyOverlay && openFolder == null && !drawerOpen && (searchMode || query.isNotBlank())) {
        try { focusManager.clearFocus() } catch (_: Throwable) {}
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

    LaunchedEffect(hubEvent) {
        val ev = hubEvent ?: return@LaunchedEffect
        kotlinx.coroutines.delay(300) // Затримка, щоб лаунчер встиг з'явитися на екрані
        when (ev.action) {
            "ai" -> {
                vm.executeCommand(ev.query)
            }
            "search" -> {
                val i = android.content.Intent(android.content.Intent.ACTION_WEB_SEARCH).apply {
                    putExtra("query", ev.query)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try { ctx.startActivity(i) } catch (_: Throwable) {}
            }
            "mic" -> {
                val voiceIntent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
                    putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Слухаю...")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    ctx.startActivity(voiceIntent)
                } catch (t: Throwable) {
                    android.util.Log.e("HomeScreen", "mic launch failed", t)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        overlayPermissionGranted = Settings.canDrawOverlays(ctx)
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        android.util.Log.e("HS_LIFE", "HomeScreen composed")
        onDispose {
            android.util.Log.e("HS_LIFE", "HomeScreen DISPOSED — composition destroyed!")
        }
    }

    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty()) vm.markDockAppeared()
    }

    // Нічний режим «лише годинник» (00:00–06:00)
    var isNightNow by remember { mutableStateOf(false) }
    LaunchedWhileStarted {
        while (true) {
            val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            isNightNow = h in 0..5
            kotlinx.coroutines.delay(60_000)
        }
    }

    // Точка останнього дотику — звідси «розкривається» запуск додатка
    var lastTouch by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Unspecified) }

    // Бейджі сповіщень на іконках дока (потрібен доступ до сповіщень)
    var dockBadges by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedWhileStarted {
        while (true) {
            val m = try { com.dynamicai.launcher.macro.NotificationDigest.counts(ctx) } catch (_: Throwable) { emptyMap() }
            if (m != dockBadges) dockBadges = m
            kotlinx.coroutines.delay(4000)
        }
    }

    LaunchedEffect(Unit) {
        android.util.Log.e("HS_LIFE", "LaunchedEffect(Unit) fired — composition restarted or first launch")
        vm.startWeatherAutoRefresh { isScreenOn(ctx) }

        // Автозапуск плаваючого дока, якщо був увімкнений
        val overlayPrefs = ctx.getSharedPreferences("dynamicai_settings", android.content.Context.MODE_PRIVATE)
        val wasEnabled = overlayPrefs.getBoolean("overlayEnabled", false)
        if (wasEnabled && Settings.canDrawOverlays(ctx)) {
            try {
                com.dynamicai.launcher.overlay.FloatingDockService.start(ctx)
                overlayActive = true
            } catch (_: Throwable) {}
        }

        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.loadContacts() else permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    fun closeSearch() {
        try { focusManager.clearFocus() } catch (_: Throwable) {}
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

    fun launchAppWithAnimation(app: AppInfo, bounds: Rect? = null) {
        hideListImmediately()
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        launchingApp = app
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

    // Оверлей з відповіддю
    LaunchedEffect(response) {
        if (response.isNotBlank()) {
            responseOverlayText = response
            responseOverlayVisible = true
            kotlinx.coroutines.delay(8000)
            responseOverlayVisible = false
        }
    }

    LaunchedEffect(query) { vm.updateQuery(query) }

    // Режим фокусу ховає відволікаючі додатки (ігри, соцмережі) поверх ручного списку прихованих
    LaunchedEffect(Unit) { com.dynamicai.launcher.macro.FocusModes.init(ctx) }
    val activeFocus by com.dynamicai.launcher.macro.FocusModes.active.collectAsStateWithLifecycle()
    val hiddenEff = remember(hidden, apps, activeFocus) { hidden + com.dynamicai.launcher.macro.FocusModes.hiddenFor(activeFocus, apps) }

    val settingsNow by rememberUpdatedState(settings)
    fun runGesture(spec: String) {
        when {
            spec.isBlank() || spec == "none" -> {}
            spec == "notifications" -> statusBarCtrl.expandNotifications()
            spec == "voice" -> ctx.startActivity(android.content.Intent(ctx, com.dynamicai.launcher.VoiceChatActivity::class.java))
            spec == "lock" -> com.dynamicai.launcher.macro.MacroLauncher.runTool(ctx, "systemAction(action=\"lock\")")
            spec.startsWith("macro:") -> com.dynamicai.launcher.macro.MacroStorage(ctx).get()
                .firstOrNull { it.id == spec.removePrefix("macro:") }?.let { com.dynamicai.launcher.macro.MacroLauncher.run(ctx, it) }
            spec.startsWith("tool:") -> com.dynamicai.launcher.macro.MacroLauncher.runTool(ctx, spec.removePrefix("tool:"))
        }
    }

    // Алфавітна стрічка: активний екран визначає джерело
    val drawerAppsAll = remember(apps, hiddenEff, folders) {
        val folderPkgs = folders.flatMap { it.packageNames }.toSet()
        apps.filter { it.packageName !in hiddenEff && it.packageName !in folderPkgs }
    }
    val drawerFiltered = remember(drawerAppsAll, drawerCategory) {
        if (drawerCategory == "all") drawerAppsAll
        else drawerAppsAll.filter { it.category == drawerCategory }
    }
    val drawerLetters = remember(drawerFiltered) {
        drawerFiltered.mapNotNull { it.label.firstOrNull()?.uppercaseChar() }
            .filter { it.isLetter() }
            .distinct().sorted()
    }
    val homeLetters = remember(suggestions, query) {
        if (query.isNotBlank()) emptyList()
        else suggestions.mapNotNull {
            (it as? Suggestion.App)?.app?.label?.firstOrNull()?.uppercaseChar()
        }.distinct().sorted()
    }
    val letters = if (drawerOpen) drawerLetters else homeLetters

    val dockApps = remember(dockPkgs, apps, hiddenEff) {
        apps.filter { it.packageName in dockPkgs && it.packageName !in hiddenEff }
    }

    LaunchedEffect(activeLetter) { if (activeLetter != null) showOverlay = true }

    // Пошук активний — користувач взаємодіє
    val searchActive = searchMode || query.isNotBlank()
    // Поле пошуку видно (постійно або при активації)
    val showSearchField = settings.showSearchBar || searchActive

    if (showWeatherDetail) {
        ScreenEnter {
            WeatherDetailScreen(
                state = weather,
                cityName = settings.cityName,
                backgroundAlphaPercent = settings.backgroundAlphaPercent,
                onClose = { showWeatherDetail = false }
            )
        }
        return@DynamicAITheme
    }

    if (showAddWidgetDialog) {
        com.dynamicai.launcher.widget.AddWidgetDialog(
            currentPageCount = pageCount,
            onDismiss = { showAddWidgetDialog = false },
            onAdd = { type, page ->
                placedWidgetsStorage.add(type, page)
                placedWidgets = placedWidgetsStorage.get()
                currentPage = page
            }
        )
    }

    if (showNotesScreen) {
        ScreenEnter { NotesScreen(onClose = { showNotesScreen = false }) }
        return@DynamicAITheme
    }

    if (showMacroManager) {
        ScreenEnter {
            com.dynamicai.launcher.macro.MacroScreen(
                onClose = { showMacroManager = false },
                carBtName = settings.carBtName,
                onCarChange = { vm.updateSettings(settings.copy(carBtName = it)) }
            )
        }
        return@DynamicAITheme
    }

    if (showFolderManager) {
        FolderManagerScreen(
            folders = folders,
            apps = apps,
            onAddApp = { folderId, pkg -> vm.addToFolder(folderId, pkg) },
            onRemoveApp = { folderId, pkg -> vm.removeFromFolder(folderId, pkg) },
            onDeleteFolder = { vm.deleteFolder(it) },
            onCreateFolder = { vm.createFolder(it, emptyList()) },
            onClose = { showFolderManager = false }
        )
        return@DynamicAITheme
    }

    if (showRecent) {
        LaunchedEffect(Unit) { vm.loadRecentTasks() }
        RecentScreen(
            tasks = recentTasks,
            onLaunch = { pkg ->
                apps.firstOrNull { it.packageName == pkg }?.let { launchAppWithAnimation(it) }
                    ?: run {
                        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
                        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        try { ctx.startActivity(intent) } catch (_: Exception) {}
                    }
            },
            onForceStop = { vm.forceStopApp(it) },
            onClearAll = { vm.forceStopAll() },
            onDismiss = { showRecent = false },
            visible = true
        )
        return@DynamicAITheme
    }

    if (showDockEditor) {
        DockEditorScreen(
            apps = apps,
            dockPkgs = dockPkgs,
            hidden = hidden,
            onToggle = { vm.toggleDock(it) },
            onClose = { showDockEditor = false }
        )
        return@DynamicAITheme
    }

    if (showWallpaper) {
        WallpaperScreen(
            settings = settings,
            onSettingsChange = { vm.updateSettings(it) },
            onClose = { showWallpaper = false }
        )
        return@DynamicAITheme
    }

    if (showSettings) {
        androidx.compose.animation.Crossfade(targetState = currentSettingsCategory, label = "settingsNav") { category ->
        if (category == null) {
            SettingsMenuScreen(
                onCategorySelected = { currentSettingsCategory = it },
                onClose = { showSettings = false },
                backgroundAlpha = settings.backgroundAlphaPercent / 100f
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                SettingsScreen(
                    vm = vm,
                    settings = settings,
                    onSettingsChange = { vm.updateSettings(it) },
                    hiddenCount = hidden.size,
                    foldersCount = folders.size,
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
                            ctx.getSharedPreferences("dynamicai_settings", android.content.Context.MODE_PRIVATE)
                                .edit().putBoolean("overlayEnabled", false).commit()
                        } else {
                            com.dynamicai.launcher.overlay.FloatingDockService.start(ctx)
                            overlayActive = true
                            ctx.getSharedPreferences("dynamicai_settings", android.content.Context.MODE_PRIVATE)
                                .edit().putBoolean("overlayEnabled", true).commit()
                        }
                    },
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onOpenWallpaper = { showWallpaper = true },
                    onOpenDockEditor = { showDockEditor = true },
                    onOpenFolderManager = { showFolderManager = true },
                    onOpenMacroManager = { showMacroManager = true },
                    onOpenNotes = { showNotesScreen = true },
                    onTestLlm = { prompt -> if (vm.ensureLlmLoaded()) vm.chatEngine.simpleAnswer(prompt) else null },
                    llmDownloaded = vm.downloader.isDownloaded,
                    llmReady = llmReady,
                    versionName = BuildConfig.VERSION_NAME,
                    initialCategory = category,
                    onBackToMenu = { currentSettingsCategory = null },
                    onClose = { showSettings = false }
                )
            }
        }
        }  // Crossfade
        return@DynamicAITheme
    }

    val openNotesReq by vm.openNotesRequest.collectAsStateWithLifecycle()
    LaunchedEffect(openNotesReq) {
        if (openNotesReq) {
            showNotesScreen = true
            vm.clearOpenNotes()
        }
    }

    LaunchedEffect(launchingApp) {
        val app = launchingApp ?: return@LaunchedEffect
        kotlinx.coroutines.delay(160)
        vm.launch(app.packageName)
        kotlinx.coroutines.delay(200)
        launchingApp = null
    }

    val launchFade by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (launchingApp != null) 0f else 1f,
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "launchFade"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = launchFade
                // Зум від місця дотику: екран «розкривається» назовні
                val s = 1f + 0.18f * (1f - launchFade)
                scaleX = s
                scaleY = s
                if (lastTouch != androidx.compose.ui.geometry.Offset.Unspecified && size.width > 0f) {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                        (lastTouch.x / size.width).coerceIn(0f, 1f), (lastTouch.y / size.height).coerceIn(0f, 1f)
                    )
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        ev.changes.firstOrNull { it.pressed && !it.previousPressed }?.let { lastTouch = it.position }
                    }
                }
            }
            .background(AppTheme.colors.background.copy(alpha = settings.backgroundAlphaPercent / 100f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (!showSettings && !drawerOpen && !searchActive && openFolder == null && !showNotesScreen && !showMacroManager) {
                            runGesture(settingsNow.gestureDoubleTap)
                        }
                    },
                    onLongPress = {
                        // Тільки якщо ми на головному екрані (не в drawer/search/settings)
                        if (!showSettings && !drawerOpen && !searchActive) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showMainMenu = true
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                var swipeFired = false
                detectVerticalDragGestures(
                    onDragStart = { swipeFired = false },
                    onDragEnd = { swipeFired = false },
                    onDragCancel = { swipeFired = false }
                ) { _, dragAmount ->
                    if (dragAmount > 10f && !swipeFired
                        && !drawerOpen && !searchActive && !showSettings
                        && openFolder == null && !showNotesScreen && !showMacroManager
                    ) {
                        swipeFired = true
                        runGesture(settingsNow.gestureSwipeDown)
                    }
                }
            }
    ) {

        // Затемнення фону коли список/пошук/drawer активний
        androidx.compose.animation.AnimatedVisibility(
            visible = listVisible || searchMode || query.isNotBlank(),
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(com.dynamicai.launcher.ui.theme.AppTheme.colors.background.copy(alpha = 0.85f))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = homeAlpha
                    scaleX = homeScale
                    scaleY = homeScale
                }
                .statusBarsPadding()
                .padding(start = 16.dp, end = 24.dp)
        ) {
            // Режим однієї руки: весь вміст зсунуто вниз, ближче до великого пальця
            Spacer(Modifier.height(if (settings.oneHandMode) (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.2f).dp else 8.dp))

            if (!searchActive) {
                if (!listVisible) {
                    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                        initialPage = currentPage,
                        pageCount = { pageCount }
                    )
                    
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(3f)
                    ) { page ->
                        val notesStorage = remember { com.dynamicai.launcher.note.NotesStorage(ctx) }
                        var notesList by remember { mutableStateOf<List<com.dynamicai.launcher.note.Note>>(emptyList()) }
                        LaunchedEffect(showNotesScreen, page) {
                            if (!showNotesScreen) notesList = notesStorage.get()
                        }
                        
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    if (settings.pageParallax) {
                                        val off = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                        val a = kotlin.math.abs(off).coerceIn(0f, 1f)
                                        translationX = off * size.width * 0.25f
                                        alpha = 1f - 0.6f * a
                                        val sc = 1f - 0.06f * a
                                        scaleX = sc; scaleY = sc
                                    }
                                },
                            verticalArrangement = Arrangement.Top
                        ) {
                            val pageWidgets = placedWidgets.filter { it.page == page }
                            val hasFull = pageWidgets.any { it.size == "full" }
                            val baseWidgets = if (hasFull) pageWidgets.filter { it.size == "full" } else pageWidgets
                            val clockOnly = if (isNightNow && settings.nightClockMode) baseWidgets.filter { it.type == "weather_clock" } else emptyList()
                            val visibleWidgets = if (clockOnly.isNotEmpty()) clockOnly else baseWidgets
                            
                            val renderOne: @Composable (com.dynamicai.launcher.widget.PlacedWidget) -> Unit = { widget ->
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier.pointerInput(widget.id) {
                                            detectTapGestures(
                                                onLongPress = { widgetSettingsFor = widget }
                                            )
                                        }
                                    ) {
                                        com.dynamicai.launcher.widget.RenderPlacedWidget(
                                            widget = widget,
                                            weather = weather,
                                            showWeather = settings.showWeather,
                                            notes = notesList,
                                            notesStorage = notesStorage,
                                            onWeatherTap = { showWeatherDetail = true },
                                            onSettingsLongPress = { showSettings = true },
                                            onNotesToggle = { id -> notesStorage.toggle(id); notesList = notesStorage.get() },
                                            onNotesDelete = { id -> notesStorage.remove(id); notesList = notesStorage.get() },
                                            onNotesAdd = { text -> notesStorage.add(text); notesList = notesStorage.get() },
                                            onOpenAllNotes = { showNotesScreen = true },
                                            onWidgetLongPress = { widgetSettingsFor = widget },
                                            onOpenMacros = { showMacroManager = true },
                                            onOpenVoice = {
                                                ctx.startActivity(android.content.Intent(ctx, com.dynamicai.launcher.VoiceChatActivity::class.java))
                                            }
                                        )
                                    }
                                    if (widgetSettingsFor?.id == widget.id) {
                                        com.dynamicai.launcher.widget.WidgetSettingsDialog(
                                            widget = widget,
                                            pageCount = pageCount,
                                            onDismiss = { widgetSettingsFor = null },
                                            onDelete = {
                                                placedWidgetsStorage.remove(widget.id)
                                                placedWidgets = placedWidgetsStorage.get()
                                                widgetSettingsFor = null
                                            },
                                            onSizeChange = { newSize ->
                                                placedWidgetsStorage.updateSize(widget.id, newSize)
                                                placedWidgets = placedWidgetsStorage.get()
                                            },
                                            onMove = { newPage ->
                                                placedWidgetsStorage.moveToPage(widget.id, newPage)
                                                placedWidgets = placedWidgetsStorage.get()
                                            },
                                            onStackWithPrevious = {
                                                placedWidgetsStorage.stackWithPrevious(widget.id)
                                                placedWidgets = placedWidgetsStorage.get()
                                                widgetSettingsFor = null
                                            },
                                            onUnstack = {
                                                placedWidgetsStorage.unstack(widget.id)
                                                placedWidgets = placedWidgetsStorage.get()
                                                widgetSettingsFor = null
                                            }
                                        )
                                    }
                            }
                            // Віджети з однаковим group малюємо як стек на місці першого
                            val slots = mutableListOf<MutableList<com.dynamicai.launcher.widget.PlacedWidget>>()
                            for (w in visibleWidgets) {
                                val s = if (w.group.isNotBlank() && w.size != "full") slots.firstOrNull { it.first().group == w.group } else null
                                if (s != null) s.add(w) else slots.add(mutableListOf(w))
                            }
                            slots.forEach { s ->
                                if (s.size == 1) renderOne(s[0])
                                else com.dynamicai.launcher.widget.WidgetStack(s) { renderOne(it) }
                            }
                        }
                    }
                    
                    LaunchedEffect(pagerState.currentPage) {
                        currentPage = pagerState.currentPage
                    }

                    // Dots під віджетами (перед search bar)
                    if (pageCount > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (p in 0 until pageCount) {
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .size(if (p == currentPage) 8.dp else 6.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(
                                            if (p == currentPage) AppTheme.colors.textPrimary
                                            else AppTheme.colors.textPrimary.copy(alpha = 0.35f)
                                        )
                                )
                            }
                        }
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showSearchField,
                enter = androidx.compose.animation.expandHorizontally(
                    animationSpec = androidx.compose.animation.core.tween(260),
                    expandFrom = Alignment.Start
                ) + androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(220, delayMillis = 60)
                ),
                exit = androidx.compose.animation.shrinkHorizontally(
                    animationSpec = androidx.compose.animation.core.tween(200)
                ) + androidx.compose.animation.fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(120)
                )
            ) {
                LiquidSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    onFocusChange = { focused -> if (focused) searchMode = true },
                    onSearch = {
                        if (query.isNotBlank()) {
                            if (vm.isCommand(query)) {
                                submit(query)
                            } else {
                                openGoogleSearch(ctx, query)
                                closeSearch()
                            }
                        }
                    },
                    onVoice = { startVoice() },
                    onClose = { closeSearch() },
                    focusRequester = focusRequester,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            if (response.isNotBlank()) {
                Text(
                    text = response,
                    color = AppTheme.colors.accent,
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
                            Text("Нічого не знайдено за \"$query\"", color = com.dynamicai.launcher.ui.theme.AppTheme.colors.textMuted, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Спробуй: ліхтарик · wifi · Аня",
                                color = com.dynamicai.launcher.ui.theme.AppTheme.colors.textMuted.copy(alpha = 0.7f), fontSize = 13.sp
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
                        is Suggestion.Extra -> SuggestionExtraRow(s) {
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

        if (letters.isNotEmpty() && (drawerOpen || (query.isBlank() && !searchMode))) {
            AlphabetSidebar(
                letters = letters,
                activeLetter = activeLetter,
                onLetterSelected = { letter ->
                    releaseJob?.cancel()
                    activeLetter = letter
                    showOverlay = true
                    if (!drawerOpen) showListTemporarily()
                    scrollJob?.cancel()
                    scrollJob = scope.launch {
                        if (drawerOpen) {
                            val idx = drawerFiltered.indexOfFirst {
                                (it.label.firstOrNull()?.uppercaseChar() ?: '#') == letter
                            }
                            if (idx >= 0) {
                                try {
                                    val current = drawerGridState.firstVisibleItemIndex
                                    val distance = kotlin.math.abs(idx - current)
                                    // Для дуже далеких букв — швидкий пре-скрол, потім плавна анімація
                                    if (distance > 80) {
                                        drawerGridState.scrollToItem(idx - 10)
                                    }
                                    drawerGridState.animateScrollToItem(
                                        index = idx,
                                        scrollOffset = 0
                                    )
                                } catch (_: Throwable) {}
                            }
                        } else {
                            val idx = suggestions.indexOfFirst {
                                it is Suggestion.App && (it.app.label.firstOrNull()?.uppercaseChar() ?: '#') == letter
                            }
                            if (idx >= 0) {
                                val current = listState.firstVisibleItemIndex
                                val d = kotlin.math.abs(idx - current)
                                if (d > 60) {
                                    listState.scrollToItem((idx - 5).coerceAtLeast(0))
                                }
                                listState.animateScrollToItem(idx)
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
                    .zIndex(10f)
            )
        }

        AnimatedVisibility(
            visible = showOverlay && activeLetter != null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center).zIndex(20f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp).clip(CircleShape).background(AppTheme.colors.textPrimary.copy(alpha = 0.15f))
            ) {
                Text(activeLetter?.toString() ?: "", color = AppTheme.colors.textPrimary, fontSize = 64.sp)
            }
        }

        // Папки над доком
        if (folders.isNotEmpty() && !listVisible && query.isBlank()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        alpha = homeAlpha
                        scaleX = homeScale
                        scaleY = homeScale
                    }
                    .padding(bottom = if (dockPkgs.isNotEmpty()) 100.dp else 20.dp)
                    .padding(horizontal = 16.dp)
            ) {
                FolderTabs(
                    folders = folders,
                    apps = apps,
                    selectedFolderId = selectedTabFolderId,
                    iconSizeDp = settings.iconSizeDp,
                    fontSizeSp = settings.fontSizeSp,
                    onSelectFolder = { folder ->
                        selectedTabFolderId =
                            if (selectedTabFolderId == folder.id) null
                            else folder.id
                    },
                    onLongPressFolder = { folder ->
                        folderHeaderMenu = folder
                    },
                    onLaunchApp = { app -> launchAppWithAnimation(app) }
                )
            }
        }

        // Оверлей з відповіддю
        Box(modifier = Modifier
            .align(Alignment.BottomCenter)
            .graphicsLayer { alpha = homeAlpha }
            .padding(bottom = 120.dp)) {
            ResponseOverlay(
                text = responseOverlayText,
                visible = responseOverlayVisible,
                onDismiss = { responseOverlayVisible = false }
            )
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
                            isFolder = true,
                            bitmap = firstApp?.bitmap
                        )
                    } else {
                        val a = apps.firstOrNull { it.packageName == id } ?: return@mapNotNull null
                        DockItem(
                            id = id,
                            label = a.label,
                            icon = a.icon,
                            isFolder = false,
                            bitmap = a.bitmap
                        )
                    }
                }
            }
            Dock(
                items = dockItems,
                iconSizeDp = settings.dockIconSizeDp,
                onLongClick = { item ->
                    if (!item.isFolder) {
                        val app = apps.firstOrNull { it.packageName == item.id }
                        if (app != null) menuApp = app
                    }
                },
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
                onRecent = {
                    showRecent = true
                    vm.loadRecentTasks()
                },
                showRecentButton = settings.showRecentButton,
                appeared = dockAppeared,
                badges = dockBadges,
                onSwipeUp = {
                    if (settings.showRecentButton) {
                        // Recent увімкнено — показуємо його
                        showRecent = true
                        vm.loadRecentTasks()
                    } else {
                        // Recent вимкнено — показуємо AppDrawer (всі застосунки)
                        drawerOpen = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .alpha(contentAlpha)
                    .graphicsLayer {
                        alpha = homeAlpha
                        scaleX = homeScale
                        scaleY = homeScale
                    }
            )
        }

        

        val drawerApps = remember(apps, hiddenEff) {
            // Показуємо всі apps крім прихованих (ті що в папках теж видно)
            apps.filter { it.packageName !in hiddenEff }
        }
        AppDrawer(
            apps = drawerApps,
            selectedCategory = drawerCategory,
            onCategoryChange = { newCat ->
                drawerCategory = newCat
                scope.launch {
                    try { drawerGridState.scrollToItem(0) } catch (_: Throwable) {}
                }
            },
            backgroundAlphaPercent = settings.backgroundAlphaPercent,
            iconSizeDp = settings.menuIconSizeDp,
            fontSizeSp = settings.fontSizeSp,
            onLaunch = { app -> launchAppWithAnimation(app) },
            onLongClick = { app -> menuApp = app },
            onDismiss = { drawerOpen = false },
            visible = drawerOpen,
            gridState = drawerGridState
        )

        // Діалог створення папки
        showCreateFolder?.let { app ->
            AlertDialog(
                onDismissRequest = { showCreateFolder = null; createFolderName = "" },
                title = { Text("Нова папка", color = AppTheme.colors.textPrimary) },
                text = {
                    Column {
                        Text("Застосунок: ${app.label}", color = com.dynamicai.launcher.ui.theme.AppTheme.colors.textMuted, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = createFolderName,
                            onValueChange = { createFolderName = it },
                            textStyle = androidx.compose.ui.text.TextStyle(color = AppTheme.colors.textPrimary, fontSize = 16.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.textPrimary),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            decorationBox = { inner ->
                                if (createFolderName.isEmpty()) {
                                    Text("Назва папки", color = com.dynamicai.launcher.ui.theme.AppTheme.colors.textMuted, fontSize = 16.sp)
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
                    }) { Text("Створити", color = AppTheme.colors.accent) }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateFolder = null; createFolderName = "" }) {
                        Text("Скасувати", color = AppTheme.colors.textPrimary)
                    }
                },
                containerColor = AppTheme.colors.surface
            )
        }

        // Діалог вибору папки (додати застосунок до існуючої)
        addToFolderPicker?.let { app ->
            AlertDialog(
                onDismissRequest = { addToFolderPicker = null },
                title = { Text("Додати ${app.label} до…", color = AppTheme.colors.textPrimary) },
                text = {
                    Column {
                        if (folders.isEmpty()) {
                            Text("Немає папок", color = com.dynamicai.launcher.ui.theme.AppTheme.colors.textMuted, fontSize = 14.sp)
                        } else {
                            folders.forEach { f ->
                                Text(
                                    f.name,
                                    color = AppTheme.colors.textPrimary,
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
                        Text("Скасувати", color = AppTheme.colors.textPrimary)
                    }
                },
                containerColor = AppTheme.colors.surface
            )
        }

        // Діалог long-press на застосунок у папці
        folderMenuApp?.let { app ->
            AlertDialog(
                onDismissRequest = { folderMenuApp = null; folderMenuFolder = null },
                title = { Text(app.label, color = AppTheme.colors.textPrimary, fontSize = 18.sp) },
                text = {
                    Column {
                        Text(
                            "Прибрати з папки",
                            color = AppTheme.colors.textPrimary,
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
                            color = AppTheme.colors.textPrimary,
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
                        Text("Скасувати", color = AppTheme.colors.textPrimary)
                    }
                },
                containerColor = AppTheme.colors.surface
            )
        }

        // Діалог long-press на заголовок папки
        folderHeaderMenu?.let { folder ->
            AlertDialog(
                onDismissRequest = { folderHeaderMenu = null },
                title = { Text(folder.name, color = AppTheme.colors.textPrimary, fontSize = 18.sp) },
                text = {
                    Column {
                        Text(
                            "Видалити папку (застосунки залишаться)",
                            color = AppTheme.colors.danger,
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
                        Text("Скасувати", color = AppTheme.colors.textPrimary)
                    }
                },
                containerColor = AppTheme.colors.surface
            )
        }

        // ==== Головне меню (long press на порожньому місці) ====
        if (showMainMenu) {
            AlertDialog(
                onDismissRequest = { showMainMenu = false },
                title = { Text("Меню", color = AppTheme.colors.textPrimary, fontSize = 18.sp) },
                text = {
                    Column {
                        MenuItem("Створити папку") {
                            newFolderName = ""
                            newFolderSelection = emptySet()
                            showMainMenu = false
                            showNewFolderDialog = true
                        }
                        MenuItem("Додати віджет") {
                            showMainMenu = false
                            showAddWidgetDialog = true
                        }
                        if (pageCount > 1) {
                            MenuItem("Сторінки (${currentPage + 1}/${pageCount})") {
                                showMainMenu = false
                                currentPage = (currentPage + 1) % pageCount
                            }
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
                    TextButton(onClick = { showMainMenu = false }) { Text("Скасувати", color = AppTheme.colors.textPrimary) }
                },
                containerColor = AppTheme.colors.surface
            )
        }

        // ==== Діалог нової папки ====
        if (showNewFolderDialog) {
            AlertDialog(
                onDismissRequest = { showNewFolderDialog = false },
                title = { Text("Нова папка", color = AppTheme.colors.textPrimary) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            textStyle = androidx.compose.ui.text.TextStyle(color = AppTheme.colors.textPrimary, fontSize = 16.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.textPrimary),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            decorationBox = { inner ->
                                if (newFolderName.isEmpty()) {
                                    Text("Назва папки", color = com.dynamicai.launcher.ui.theme.AppTheme.colors.textMuted, fontSize = 16.sp)
                                }
                                inner()
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Обери застосунки:", color = com.dynamicai.launcher.ui.theme.AppTheme.colors.textMuted, fontSize = 13.sp)
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
                                        bitmap = (app.bitmap ?: app.icon.toBitmap(72, 72)).asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = Color.Unspecified
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(app.label, color = AppTheme.colors.textPrimary, fontSize = 15.sp)
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
                    ) { Text("Створити", color = AppTheme.colors.accent) }
                },
                dismissButton = {
                    TextButton(onClick = { showNewFolderDialog = false }) {
                        Text("Скасувати", color = AppTheme.colors.textPrimary)
                    }
                },
                containerColor = AppTheme.colors.surface
            )
        }

        // ==== Діалог відкритої папки ====
        openFolder?.let { folder ->
            val folderApps = apps.filter { it.packageName in folder.packageNames }
            AlertDialog(
                onDismissRequest = { openFolder = null },
                title = { Text(folder.name, color = AppTheme.colors.textPrimary, fontSize = 18.sp) },
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
                                    bitmap = (app.bitmap ?: app.icon.toBitmap(96, 96)).asImageBitmap(),
                                    contentDescription = app.label,
                                    modifier = Modifier.size(40.dp),
                                    tint = Color.Unspecified
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(app.label, color = AppTheme.colors.textPrimary, fontSize = 16.sp)
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
                            color = AppTheme.colors.accent
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { openFolder = null }) {
                        Text("Закрити", color = AppTheme.colors.textPrimary)
                    }
                },
                containerColor = AppTheme.colors.surface
            )
        }

        pendingChoice?.let { candidates ->
            AlertDialog(
                onDismissRequest = { vm.dismissChoice() },
                title = { Text("Обери застосунок", color = AppTheme.colors.textPrimary) },
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
                                    bitmap = (app.bitmap ?: app.icon.toBitmap(96, 96)).asImageBitmap(),
                                    contentDescription = app.label,
                                    modifier = Modifier.size(36.dp),
                                    tint = Color.Unspecified
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(app.label, color = AppTheme.colors.textPrimary, fontSize = 16.sp)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { vm.dismissChoice() }) { Text("Скасувати", color = AppTheme.colors.textPrimary) } },
                containerColor = AppTheme.colors.surface
            )
        }

        menuApp?.let { app ->
            val inFolder = vm.folderOf(app.packageName)
            AppMenuPopup(app = app, anchor = lastTouch, onDismiss = { menuApp = null }) {
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
                        MenuItem("Видалити з телефону") {
                            try {
                                // Спроба 1: ACTION_UNINSTALL_PACKAGE (як у Lawnchair)
                                val uninstallIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                                    data = Uri.fromParts("package", app.packageName, null)
                                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    val activity = ctx as? android.app.Activity ?: ctx
                                    activity.startActivity(uninstallIntent)
                                    android.util.Log.e("Uninstall", "ACTION_UNINSTALL_PACKAGE OK")
                                } catch (e1: Exception) {
                                    android.util.Log.e("Uninstall", "UNINSTALL failed: ${e1.message}", e1)
                                    val deleteIntent = Intent(Intent.ACTION_DELETE).apply {
                                        data = Uri.parse("package:${app.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try {
                                        ctx.startActivity(deleteIntent)
                                        android.util.Log.e("Uninstall", "ACTION_DELETE OK")
                                    } catch (e2: Exception) {
                                        android.util.Log.e("Uninstall", "DELETE failed: ${e2.message}", e2)
                                        throw e2
                                    }
                                }
                            } catch (t: Throwable) {
                                // Спроба 3: Налаштування застосунку з кнопкою видалення
                                try {
                                    val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${app.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    ctx.startActivity(settingsIntent)
                                } catch (_: Exception) {}
                            }
                            menuApp = null
                        }
                    }
            }
        }
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
        title = { Text(app.label, color = AppTheme.colors.textPrimary, fontSize = 18.sp) },
        text = {
            Column {
                MenuItem(if (inDock) "Прибрати з дока" else "Додати в док", onToggleDock)
                MenuItem("Приховати зі списку", onHide)
                MenuItem("Інформація про застосунок", onInfo)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати", color = AppTheme.colors.textPrimary) } },
        containerColor = AppTheme.colors.surface
    )
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = AppTheme.colors.textPrimary,
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        AppTheme.colors.textPrimary.copy(alpha = 0.08f),
                        AppTheme.colors.textPrimary.copy(alpha = 0.02f)
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassCircleIcon(Icons.Default.Star, AppTheme.colors.accent)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(s.label, color = AppTheme.colors.textPrimary, fontSize = 16.sp)
            s.subtitle?.let { Text(it, color = com.dynamicai.launcher.ui.theme.AppTheme.colors.textMuted, fontSize = 12.sp) }
        }
    }
}

@Composable
fun SuggestionExtraRow(s: Suggestion.Extra, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        AppTheme.colors.textPrimary.copy(alpha = if (s.kind == "ask") 0.04f else 0.08f),
                        AppTheme.colors.textPrimary.copy(alpha = 0.02f)
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(AppTheme.colors.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) { Text(s.icon, fontSize = 20.sp) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(s.label, color = AppTheme.colors.textPrimary, fontSize = 16.sp, maxLines = 1)
            Text(s.subtitle, color = AppTheme.colors.textMuted, fontSize = 12.sp)
        }
    }
}

@Composable
fun SuggestionSettingRow(s: Suggestion.Setting, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        AppTheme.colors.textPrimary.copy(alpha = 0.08f),
                        AppTheme.colors.textPrimary.copy(alpha = 0.02f)
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassCircleIcon(Icons.Default.Settings, AppTheme.colors.textMuted)
        Spacer(Modifier.width(14.dp))
        Text(s.label, color = AppTheme.colors.textPrimary, fontSize = 16.sp)
    }
}

@Composable
fun SuggestionContactRow(s: Suggestion.Contact, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        AppTheme.colors.textPrimary.copy(alpha = 0.08f),
                        AppTheme.colors.textPrimary.copy(alpha = 0.02f)
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassCircleIcon(Icons.Default.Person, AppTheme.colors.successLight)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(s.name, color = AppTheme.colors.textPrimary, fontSize = 16.sp)
            Text(s.subtitle, color = com.dynamicai.launcher.ui.theme.AppTheme.colors.textMuted, fontSize = 12.sp)
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
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        AppTheme.colors.textPrimary.copy(alpha = 0.06f),
                        AppTheme.colors.textPrimary.copy(alpha = 0.015f)
                    )
                )
            )
            .pointerInput(app.packageName) {
                detectTapGestures(onTap = { onClick() })
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Glass-рамка навколо іконки
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size((iconSizeDp + 10).dp)
                .clip(RoundedCornerShape((iconSizeDp / 2 + 5).dp))
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            AppTheme.colors.textPrimary.copy(alpha = 0.08f),
                            AppTheme.colors.textPrimary.copy(alpha = 0.02f)
                        )
                    )
                )
                .border(
                    width = 0.5.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            AppTheme.colors.textPrimary.copy(alpha = 0.2f),
                            AppTheme.colors.textPrimary.copy(alpha = 0.04f)
                        )
                    ),
                    shape = RoundedCornerShape((iconSizeDp / 2 + 5).dp)
                )
        ) {
            Icon(
                bitmap = (app.bitmap ?: app.icon.toBitmap(96, 96)).asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier.size(iconSizeDp.dp).iconShape(),
                tint = Color.Unspecified
            )
        }
        if (showLabel) {
            Spacer(Modifier.width(14.dp))
            Text(
                app.label,
                color = if (inDock) AppTheme.colors.accent else AppTheme.colors.textPrimary,
                fontSize = fontSizeSp.sp
            )
        }
    }
}


private fun openGoogleSearch(ctx: android.content.Context, query: String) {
    val url = "https://www.google.com/search?q=" + android.net.Uri.encode(query)
    val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    
    try {
        val st = com.dynamicai.launcher.data.SettingsStorage(ctx).load()
        if (st.defaultBrowser.isNotBlank()) {
            val i = android.content.Intent(viewIntent).setPackage(st.defaultBrowser)
            ctx.startActivity(i)
            return
        }
    } catch (_: Throwable) {}
    
    try {
        val i = android.content.Intent(viewIntent).setPackage("com.google.android.googlequicksearchbox")
        ctx.startActivity(i)
        return
    } catch (_: Throwable) {}
    
    try {
        val i = android.content.Intent(viewIntent).setPackage("com.android.chrome")
        ctx.startActivity(i)
        return
    } catch (_: Throwable) {}
    
    try {
        ctx.startActivity(viewIntent)
    } catch (_: Throwable) {}
}

private fun isScreenOn(ctx: android.content.Context): Boolean {
    return try {
        val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        pm.isInteractive
    } catch (_: Throwable) { true }



}
