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
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.flow.first
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dynamicai.launcher.folder.Folder
import com.dynamicai.launcher.macro.FocusModes
import com.dynamicai.launcher.macro.MacroLauncher
import com.dynamicai.launcher.macro.MacroScreen
import com.dynamicai.launcher.macro.MacroStorage
import com.dynamicai.launcher.macro.NotificationDigest
import com.dynamicai.launcher.overlay.FloatingDockService
import com.dynamicai.launcher.suggestion.AppUsage
import com.dynamicai.launcher.suggestion.SearchHistory
import com.dynamicai.launcher.system.StatusBarController
import com.dynamicai.launcher.widget.AddWidgetDialog
import com.dynamicai.launcher.widget.PlacedWidget
import com.dynamicai.launcher.widget.WidgetsStorage
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    DynamicAITheme(themeId = settings.appTheme, accentOverride = settings.accentColor, backgroundOverride = MaterialTheme.colorScheme.background) {
    val hidden by vm.hidden.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val recentTasks by vm.recentTasks.collectAsStateWithLifecycle()
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()
    val llmReady by vm.llmReady.collectAsStateWithLifecycle()
    val dockAppeared by vm.dockAppeared.collectAsStateWithLifecycle()

    // Плавна поява іконок — тільки один раз, потім завжди 1
    val contentAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(450),
        label = "contentAlpha"
    )

    val ctx = LocalContext.current
    val statusBarCtrl = remember { StatusBarController(ctx.applicationContext) }
    val keyboard = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current

    var query by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showWallpaper by remember { mutableStateOf(false) }
    var showDockEditor by remember { mutableStateOf(false) }
    var showFolderManager by remember { mutableStateOf(false) }
    var showMacroManager by remember { mutableStateOf(false) }
    var showNotesScreen by remember { mutableStateOf(false) }
    val placedWidgetsStorage = remember { WidgetsStorage(ctx) }
    var placedWidgets by remember { mutableStateOf<List<PlacedWidget>>(placedWidgetsStorage.get()) }
    var showAddWidgetDialog by remember { mutableStateOf(false) }
    var currentSettingsCategory by remember { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableStateOf(if (placedWidgetsStorage.hasDashboard()) 1 else 0) }
    val pageCount = remember(placedWidgets) {
        (placedWidgets.maxOfOrNull { it.page } ?: 0) + 1
    }
    LaunchedEffect(Unit) { placedWidgets = placedWidgetsStorage.ensureDefaults() }
    var responseOverlayVisible by remember { mutableStateOf(false) }
    var responseOverlayText by remember { mutableStateOf("") }
    var showRecent by remember { mutableStateOf(false) }
    var showWeatherDetail by remember { mutableStateOf(false) }
    var searchMode by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    var folderMenuApp by remember { mutableStateOf<AppInfo?>(null) }
    var folderMenuFolder by remember { mutableStateOf<Folder?>(null) }
    var folderHeaderMenu by remember { mutableStateOf<Folder?>(null) }
    var openFolder by remember { mutableStateOf<Folder?>(null) }
    var selectedTabFolderId by remember { mutableStateOf<String?>(null) }
    var showMainMenu by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showCreateFolder by remember { mutableStateOf<AppInfo?>(null) }
    var addToFolderPicker by remember { mutableStateOf<AppInfo?>(null) }
    var overlayActive by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }
    var drawerCategory by remember { mutableStateOf("all") }
    // Анімація зникнення HomeScreen при відкритті drawer
    val homeAlpha by animateFloatAsState(
        targetValue = if (drawerOpen) 0f else 1f,
        animationSpec = tween(animMs(if (drawerOpen) 180 else 300), easing = Motion.Standard),
        label = "homeAlpha"
    )
    val homeScale by animateFloatAsState(
        targetValue = if (drawerOpen) 0.93f else 1f,
        animationSpec = tween(animMs(if (drawerOpen) 260 else 340), easing = Motion.Decel),
        label = "homeScale"
    )
    var launchingApp by remember { mutableStateOf<AppInfo?>(null) }
    var overlayPermissionGranted by remember {
        mutableStateOf(Settings.canDrawOverlays(ctx))
    }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val searchGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
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
        snapshotFlow { listState.isScrollInProgress }
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
        delay(300) // Затримка, щоб лаунчер встиг з'явитися на екрані
        when (ev.action) {
            "ai" -> {
                vm.executeCommand(ev.query)
            }
            "search" -> {
                val i = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra("query", ev.query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try { ctx.startActivity(i) } catch (_: Throwable) {}
            }
            "mic" -> {
                val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Слухаю...")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    ctx.startActivity(voiceIntent)
                } catch (t: Throwable) {
                    Log.e("HomeScreen", "mic launch failed", t)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        overlayPermissionGranted = Settings.canDrawOverlays(ctx)
    }

    DisposableEffect(Unit) {
        Log.e("HS_LIFE", "HomeScreen composed")
        onDispose {
            Log.e("HS_LIFE", "HomeScreen DISPOSED — composition destroyed!")
        }
    }

    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty()) vm.markDockAppeared()
    }

    // Після першого показу вхідна анімація більше не програється
    LaunchedEffect(Unit) { delay(1200); HomeAnim.played = true }

    // Нічний режим «лише годинник» (00:00–06:00)
    var isNightNow by remember { mutableStateOf(false) }
    LaunchedWhileStarted {
        while (true) {
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            isNightNow = h in 0..5
            delay(60_000)
        }
    }

    // Точка останнього дотику — звідси «розкривається» запуск додатка
    var lastTouch by remember { mutableStateOf(Offset.Unspecified) }

    // Бейджі сповіщень на іконках дока (потрібен доступ до сповіщень)
    var dockBadges by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedWhileStarted {
        while (true) {
            // Запит до системи сповіщень — поза головним потоком, щоб не збивати кадри під час жестів
            val m = withContext(Dispatchers.Default) {
                try { NotificationDigest.counts(ctx) } catch (_: Throwable) { emptyMap() }
            }
            if (m != dockBadges) dockBadges = m
            delay(4000)
        }
    }

    LaunchedEffect(Unit) {
        Log.e("HS_LIFE", "LaunchedEffect(Unit) fired — composition restarted or first launch")
        vm.startWeatherAutoRefresh { isScreenOn(ctx) }

        // Автозапуск плаваючого дока, якщо був увімкнений
        val overlayPrefs = ctx.getSharedPreferences("dynamicai_settings", Context.MODE_PRIVATE)
        val wasEnabled = overlayPrefs.getBoolean("overlayEnabled", false)
        if (wasEnabled && Settings.canDrawOverlays(ctx)) {
            try {
                FloatingDockService.start(ctx)
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
            delay(8000)
            responseOverlayVisible = false
        }
    }

    LaunchedEffect(query) { vm.updateQuery(query) }

    // Режим фокусу ховає відволікаючі додатки (ігри, соцмережі) поверх ручного списку прихованих
    LaunchedEffect(Unit) { FocusModes.init(ctx) }
    val activeFocus by FocusModes.active.collectAsStateWithLifecycle()
    val hiddenEff = remember(hidden, apps, activeFocus) { hidden + FocusModes.hiddenFor(activeFocus, apps) }

    val settingsNow by rememberUpdatedState(settings)
    fun runGesture(spec: String) {
        when {
            spec.isBlank() || spec == "none" -> {}
            spec == "notifications" -> statusBarCtrl.expandNotifications()
            spec == "drawer" -> drawerOpen = true
            spec == "search" -> searchMode = true
            spec == "voice" -> ctx.startActivity(Intent(ctx, com.dynamicai.launcher.VoiceChatActivity::class.java))
            spec == "lock" -> MacroLauncher.runTool(ctx, "systemAction(action=\"lock\")")
            spec.startsWith("macro:") -> MacroStorage(ctx).get()
                .firstOrNull { it.id == spec.removePrefix("macro:") }?.let { MacroLauncher.run(ctx, it) }
            spec.startsWith("tool:") -> MacroLauncher.runTool(ctx, spec.removePrefix("tool:"))
        }
    }

    // Алфавітна стрічка: активний екран визначає джерело
    val drawerAppsAll = remember(apps, hiddenEff, folders) {
        apps.filter { it.packageName !in hiddenEff }
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
    // Ландшафт: ліворуч віджети/дошка, праворуч постійна панель додатків
    val isLandscape = LocalConfiguration.current.orientation ==
        Configuration.ORIENTATION_LANDSCAPE
    val landscapePane = isLandscape && !searchActive && !listVisible

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
        AddWidgetDialog(
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
            MacroScreen(
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
                        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
            onMove = { id, delta -> vm.moveDock(id, delta) },
            folderNames = folders.associate { "folder:" + it.id to it.name },
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
        Crossfade(targetState = currentSettingsCategory, animationSpec = tween(220, easing = Motion.Standard), label = "settingsNav") { category ->
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
                            FloatingDockService.stop(ctx)
                            overlayActive = false
                            ctx.getSharedPreferences("dynamicai_settings", Context.MODE_PRIVATE)
                                .edit().putBoolean("overlayEnabled", false).commit()
                        } else {
                            FloatingDockService.start(ctx)
                            overlayActive = true
                            ctx.getSharedPreferences("dynamicai_settings", Context.MODE_PRIVATE)
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

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(launchingApp) {
        val app = launchingApp ?: return@LaunchedEffect
        // Запускаємо одразу: поки грає анімація, додаток уже стартує і до її кінця готовий
        // Спершу даємо намалювати перші кадри анімації, а важкий виклик запуску робимо поза головним потоком —
        // інакше binder-виклик startActivity на мить заморожує UI («ривок»)
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        val smoothLaunch = settings.animSpeedPercent > 0
        withContext(Dispatchers.Default) {
            vm.launch(app.packageName, smooth = smoothLaunch)
        }
        // Тримаємо оверлей, доки лаунчер не стане повністю невидимим (додаток уже намальований), максимум 1,3 с.
        // PAUSED настає ще до появи вікна додатка, тому чекаємо саме STOPPED — інакше головний екран блимає.
        withTimeoutOrNull(1300) {
            lifecycleOwner.lifecycle.currentStateFlow.first { !it.isAtLeast(Lifecycle.State.STARTED) }
        }
        launchingApp = null
    }

    val launchFade by animateFloatAsState(
        targetValue = if (launchingApp != null) 0f else 1f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "launchFade"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Як в iOS: головний екран трохи зменшується, тьмяніє й розмивається, поки відкривається додаток
                val t = 1f - launchFade
                alpha = 1f - 0.5f * t
                val s = 1f - 0.07f * t
                scaleX = s
                scaleY = s
                if (t > 0.02f && Build.VERSION.SDK_INT >= 31) {
                    val r = 18.dp.toPx() * t
                    renderEffect = BlurEffect(r, r, TileMode.Decal)
                } else renderEffect = null
                if (lastTouch != Offset.Unspecified && size.width > 0f) {
                    transformOrigin = TransformOrigin(
                        (lastTouch.x / size.width).coerceIn(0f, 1f), (lastTouch.y / size.height).coerceIn(0f, 1f)
                    )
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
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
                    if (Math.abs(dragAmount) > 10f && !swipeFired
                        && !drawerOpen && !searchActive && !showSettings
                        && openFolder == null && !showNotesScreen && !showMacroManager
                    ) {
                        swipeFired = true
                        runGesture(if (dragAmount > 0f) settingsNow.gestureSwipeDown else settingsNow.gestureSwipeUp)
                    }
                }
            }
    ) {

        // Затемнення фону коли список/пошук/drawer активний
        androidx.compose.animation.AnimatedVisibility(
            visible = listVisible || searchMode || query.isNotBlank(),
            enter = fadeIn(tween(animMs(240), easing = Motion.Standard)),
            exit = fadeOut(tween(animMs(200), easing = Motion.Standard))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppTheme.colors.background.copy(alpha = 0.85f))
            )
        }

        var paneReady by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { paneReady = true }
        val leftFraction by animateFloatAsState(
            targetValue = if (isLandscape) 0.46f else 1f,
            animationSpec = tween(animMs(420), easing = FastOutSlowInEasing),
            label = "leftFraction"
        )
        val resultsBlock: @Composable (Modifier) -> Unit = { mod ->
            Box(modifier = mod) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = listVisible || searchMode || query.isNotBlank(),
                    enter = fadeIn(tween(110)),
                    exit = fadeOut(tween(90)),
                    modifier = Modifier.fillMaxSize()
                ) {
            if (query.isNotBlank()) {
            // Набраний запит: додатки — квадратні плитки, решта результатів — рядки на всю ширину
            LazyVerticalGrid(
                state = searchGridState,
                columns = gridCells(),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = if (dockPkgs.isNotEmpty()) 110.dp else 8.dp),
                horizontalArrangement = gridGapH(),
                verticalArrangement = gridGapV()
            ) {
                if (suggestions.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Нічого не знайдено за \"$query\"", color = AppTheme.colors.textMuted, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Спробуй: ліхтарик · wifi · Аня",
                                color = AppTheme.colors.textMuted.copy(alpha = 0.7f), fontSize = 13.sp
                            )
                        }
                    }
                }
                // Додатки — плитками зверху, решта результатів (дії, налаштування, контакти) — рядками знизу
                val orderedSuggestions = suggestions.filterIsInstance<Suggestion.App>() +
                    suggestions.filter { it !is Suggestion.App }
                items(
                    orderedSuggestions,
                    key = { it.stableKey() },
                    span = { s ->
                        GridItemSpan(if (s is Suggestion.App) 1 else maxLineSpan)
                    }
                ) { s ->
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
                        is Suggestion.App -> AppGridItem(
                            app = s.app,
                            onLongClick = { menuApp = s.app },
                            onTap = { launchAppWithAnimation(s.app) }
                        )
                    }
                }
            }
            } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = if (dockPkgs.isNotEmpty()) 110.dp else 8.dp
                )
            ) {
                if (suggestions.isEmpty() && query.isNotBlank()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Нічого не знайдено за \"$query\"", color = AppTheme.colors.textMuted, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Спробуй: ліхтарик · wifi · Аня",
                                color = AppTheme.colors.textMuted.copy(alpha = 0.7f), fontSize = 13.sp
                            )
                        }
                    }
                }
                items(suggestions, key = { it.stableKey() }) { s ->
                  // Без animateItem: при швидкому наборі рядки мають змінюватись різко, а не «пливти»
                  androidx.compose.foundation.layout.Box {
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
                            showLabel = settings.showLabels,
                            onClick = { launchAppWithAnimation(s.app) },
                            onLongClick = { menuApp = s.app },
                            highlight = query
                        )
                    }
                  }
                }
            }
            }
                }
            }
        }
        val folderBlock: @Composable (Modifier, Dp) -> Unit = { mod, bottomPad ->
            Column(
                modifier = mod
                    .graphicsLayer {
                        alpha = homeAlpha
                        scaleX = homeScale
                        scaleY = homeScale
                    }
                    .padding(bottom = bottomPad)
                    .padding(horizontal = 16.dp)
            ) {
                FolderTabs(
                    folders = folders,
                    apps = apps,
                    selectedFolderId = selectedTabFolderId,
                    onSelectFolder = { folder ->
                        selectedTabFolderId =
                            if (selectedTabFolderId == folder.id) null
                            else folder.id
                    },
                    onLongPressFolder = { folder ->
                        folderHeaderMenu = folder
                    },
                    onLaunchApp = { app -> launchAppWithAnimation(app) },
                    onLongPressApp = { app ->
                        folderMenuApp = app
                        folderMenuFolder = folders.firstOrNull { it.id == selectedTabFolderId }
                    }
                )
            }
        }
        val dockBlock: @Composable (Modifier) -> Unit = { mod ->
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
                iconSizeDp = LocalGridStyle.current.dockIconDp,
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
                modifier = mod
                    .alpha(contentAlpha)
                    .graphicsLayer {
                        alpha = homeAlpha
                        scaleX = homeScale
                        scaleY = homeScale
                    }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(leftFraction)
                // Поки відкрита шторка — головний екран не реагує на дотики (раніше свайпи/тапи проходили крізь неї)
                .pointerInput(drawerOpen) {
                    if (drawerOpen) awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            e.changes.forEach { it.consume() }
                        }
                    }
                }
                .graphicsLayer {
                    alpha = homeAlpha
                    scaleX = homeScale
                    scaleY = homeScale
                }
                .statusBarsPadding()
                .padding(start = 16.dp, end = 24.dp)
        ) {
            // Режим однієї руки: весь вміст зсунуто вниз, ближче до великого пальця
            Spacer(Modifier.height(if (settings.oneHandMode) (LocalConfiguration.current.screenHeightDp * 0.2f).dp else 8.dp))

            if (!searchActive) {
                if (!listVisible) {
                    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                        initialPage = currentPage,
                        pageCount = { pageCount }
                    )
                    
                    HorizontalPager(
                        state = pagerState,
                        // Перемикання сторінок лише на головному екрані: не у шторці, папці чи пошуку
                        userScrollEnabled = !drawerOpen && openFolder == null && !searchMode,
                        // Сусідні сторінки зібрані наперед — початок свайпу без ривка
                        beyondViewportPageCount = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(if (isLandscape) 1f else 3f)
                    ) { page ->
                        HomeWidgetPage(
                            page = page,
                            pageCount = pageCount,
                            placedWidgets = placedWidgets,
                            storage = placedWidgetsStorage,
                            weather = weather,
                            showWeather = settings.showWeather,
                            clockOnlyMode = isNightNow && settings.nightClockMode,
                            scrollable = landscapePane,
                            notesScreenOpen = showNotesScreen,
                            onWidgetsChanged = { placedWidgets = placedWidgetsStorage.get() },
                            onWeatherTap = { showWeatherDetail = true },
                            onOpenSettings = { showSettings = true },
                            onOpenNotes = { showNotesScreen = true },
                            onOpenMacros = { showMacroManager = true },
                            modifier = Modifier
                                .graphicsLayer {
                                    if (settings.pageParallax) {
                                        val off = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                        val a = kotlin.math.abs(off).coerceIn(0f, 1f)
                                        translationX = off * size.width * 0.25f
                                        // Сусідня сторінка зсунута паралаксом у бік поточної, тому гасимо її повністю:
                                        // інакше її віджети видно з краю екрана, коли сторінка вже стоїть на місці
                                        alpha = (1f - a * 1.3f).coerceIn(0f, 1f)
                                        val sc = 1f - 0.08f * a
                                        scaleX = sc; scaleY = sc
                                    }
                                }
                        )
                    }
                    
                    // settledPage, а не currentPage: інакше весь екран перекомпоновується посеред свайпу (ривок)
                    LaunchedEffect(pagerState.settledPage) {
                        currentPage = pagerState.settledPage
                    }
                    LaunchedEffect(currentPage, pageCount) {
                        val target = currentPage.coerceIn(0, pageCount - 1)
                        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
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
                                        .size(8.dp)
                                        // Крапка плавно «перетікає» слідом за пальцем; читання в graphicsLayer — без перекомпонування
                                        .graphicsLayer {
                                            val pos = pagerState.currentPage + pagerState.currentPageOffsetFraction
                                            val sel = (1f - kotlin.math.abs(pos - p)).coerceIn(0f, 1f)
                                            val sc = 0.75f + 0.25f * sel
                                            scaleX = sc; scaleY = sc
                                            alpha = 0.35f + 0.65f * sel
                                        }
                                        .clip(CircleShape)
                                        .background(AppTheme.colors.textPrimary)
                                )
                            }
                        }
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showSearchField,
                enter = expandHorizontally(
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Start
                ) + fadeIn(
                    animationSpec = tween(240, delayMillis = 40)
                ) + scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(320, easing = FastOutSlowInEasing)
                ),
                exit = shrinkHorizontally(
                    animationSpec = tween(200)
                ) + fadeOut(
                    animationSpec = tween(120)
                )
            ) {
                LiquidSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    onFocusChange = { focused -> if (focused) searchMode = true },
                    onSearch = {
                        val top = suggestions.firstOrNull()
                        // Enter запускає найкращий збіг (крім контактів — щоб випадково не телефонувати, та «спитати/веб»)
                        val strong = top != null && top.score >= 75 && top !is Suggestion.Contact &&
                            !(top is Suggestion.Extra && (top.kind == "ask" || top.kind == "web"))
                        if (query.isNotBlank() && strong) {
                            vm.executeSuggestion(top!!)
                            closeSearch()
                        } else if (query.isNotBlank()) {
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

            // Відповідь показує лише ResponseOverlay (картка, зникає сама); постійний синій текст прибрано

            // Порожній пошук: часті додатки та історія запитів
            if (searchMode && query.isBlank()) {
                var historyTick by remember { mutableStateOf(0) }
                val history = remember(historyTick, searchMode) { SearchHistory.get(ctx) }
                val frequent = remember(apps, searchMode) {
                    AppUsage.top(ctx, 6).mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
                }
                if (frequent.isNotEmpty()) {
                    Text("Часті", color = AppTheme.colors.textSecondary, fontSize = 11.sp,
                        modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 4.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        frequent.forEach { a ->
                            Image(
                                bitmap = (a.bitmap ?: a.icon.toBitmap(96, 96)).asImageBitmap(),
                                contentDescription = a.label,
                                modifier = Modifier
                                    .size(46.dp)
                                    .iconShape()
                                    .pointerInput(a.packageName) {
                                        detectTapGestures(
                                            onTap = { launchAppWithAnimation(a) },
                                            onLongPress = { menuApp = a }
                                        )
                                    }
                            )
                        }
                    }
                }
                if (history.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        history.forEach { h ->
                            Text(
                                "🕘 $h", color = AppTheme.colors.textPrimary, fontSize = 13.sp, maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(AppTheme.colors.textPrimary.copy(alpha = 0.08f))
                                    .pointerInput(h) { detectTapGestures { query = h } }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                        Text(
                            "✕", color = AppTheme.colors.textSecondary, fontSize = 13.sp,
                            modifier = Modifier
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        SearchHistory.clear(ctx); historyTick++
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            if (!isLandscape) resultsBlock(Modifier.weight(1f))

            // Ландшафт: папки та док стоять у лівій панелі одне під одним (без накладання)
            if (isLandscape) {
                if (folders.isNotEmpty() && !listVisible && query.isBlank() && !searchMode && !drawerOpen) {
                    folderBlock(Modifier.padding(top = 4.dp), 4.dp)
                }
                if (dockPkgs.isNotEmpty() && !searchMode) {
                    dockBlock(Modifier.padding(bottom = 6.dp).navigationBarsPadding())
                }
            }
        }

        // Права панель ландшафту: або постійна сітка додатків, або результати пошуку/списку
        val paneFrame = Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.54f).fillMaxHeight()
        if (isLandscape) {
            val paneApps = remember(apps, hiddenEff) { apps.filter { it.packageName !in hiddenEff } }
            androidx.compose.animation.AnimatedVisibility(
                visible = paneReady && landscapePane,
                enter = fadeIn(tween(animMs(360), delayMillis = animMs(120))) +
                    slideInHorizontally(
                        tween(animMs(420), easing = FastOutSlowInEasing)
                    ) { it / 3 } +
                    scaleIn(tween(animMs(420)), initialScale = 0.94f),
                exit = fadeOut(tween(animMs(140))) +
                    slideOutHorizontally(tween(animMs(200))) { it / 6 },
                modifier = paneFrame
            ) {
                LandscapeAppPane(
                    apps = paneApps,
                    onLaunch = { app -> launchAppWithAnimation(app) },
                    onLongClick = { app -> menuApp = app },
                    modifier = Modifier.fillMaxSize()
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = !landscapePane,
                enter = fadeIn(tween(animMs(240))) +
                    slideInHorizontally(
                        tween(animMs(320), easing = FastOutSlowInEasing)
                    ) { it / 4 },
                exit = fadeOut(tween(animMs(120))),
                modifier = paneFrame
            ) {
                resultsBlock(
                    Modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp, end = 12.dp, bottom = 8.dp)
                )
            }
        }

        if (letters.isNotEmpty() && (drawerOpen || (query.isBlank() && !searchMode && !landscapePane))) {
            AlphabetSidebar(
                letters = letters,
                activeLetter = activeLetter,
                onLetterSelected = { letter ->
                    val listWasHidden = !listVisible
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
                            // Список щойно відкрився: чекаємо, доки з'являться дані й розмітка, інакше перший дотик нікуди не прокручує
                            val idx = withTimeoutOrNull(800) {
                                snapshotFlow {
                                    suggestions.indexOfFirst {
                                        it is Suggestion.App && (it.app.label.firstOrNull()?.uppercaseChar() ?: '#') == letter
                                    }
                                }.first { it >= 0 }
                            } ?: -1
                            if (idx >= 0) {
                                withTimeoutOrNull(800) {
                                    snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > idx }
                                }
                                if (listWasHidden) {
                                    listState.scrollToItem(idx)   // список ще проявляється — одразу стаємо на букву
                                } else {
                                    val current = listState.firstVisibleItemIndex
                                    val d = kotlin.math.abs(idx - current)
                                    if (d > 60) {
                                        listState.scrollToItem((idx - 5).coerceAtLeast(0))
                                    }
                                    listState.animateScrollToItem(idx)
                                }
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
            enter = fadeIn(tween(120)) +
                scaleIn(spring(dampingRatio = 0.7f, stiffness = 500f), initialScale = 0.8f),
            exit = fadeOut(tween(180)) +
                scaleOut(tween(180), targetScale = 0.9f),
            modifier = Modifier.align(Alignment.Center).zIndex(20f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp).clip(CircleShape).background(AppTheme.colors.textPrimary.copy(alpha = 0.15f))
            ) {
                Text(activeLetter?.toString() ?: "", color = AppTheme.colors.textPrimary, fontSize = 64.sp)
            }
        }

        // Папки над доком (у ландшафті вони вбудовані в ліву панель)
        if (folders.isNotEmpty() && !listVisible && query.isBlank() && !searchMode && !drawerOpen && !isLandscape) {
            folderBlock(
                Modifier.align(Alignment.BottomCenter),
                if (dockPkgs.isNotEmpty()) 100.dp else 20.dp
            )
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

        // Під час пошуку док ховаємо — не заважає результатам
        if (dockPkgs.isNotEmpty() && !searchMode && !isLandscape) {
            dockBlock(Modifier.align(Alignment.BottomCenter))
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
            onLaunch = { app -> launchAppWithAnimation(app) },
            onLongClick = { app -> menuApp = app },
            onDismiss = { drawerOpen = false },
            visible = drawerOpen,
            gridState = drawerGridState
        )

        // Діалоги й меню винесено в HomeDialogs.kt
        showCreateFolder?.let { app ->
            CreateFolderDialog(
                app = app,
                onCreate = { name -> vm.createFolder(name, listOf(app.packageName)) },
                onDismiss = { showCreateFolder = null }
            )
        }

        addToFolderPicker?.let { app ->
            AddToFolderDialog(
                app = app,
                folders = folders,
                onPick = { f -> vm.addToFolder(f.id, app.packageName); addToFolderPicker = null },
                onDismiss = { addToFolderPicker = null }
            )
        }

        folderMenuApp?.let { app ->
            val closeFolderMenu = { folderMenuApp = null; folderMenuFolder = null }
            FolderAppMenuDialog(
                app = app,
                onRemove = { folderMenuFolder?.let { f -> vm.removeFromFolder(f.id, app.packageName) }; closeFolderMenu() },
                onLaunch = { launchAppWithAnimation(app); closeFolderMenu() },
                onMore = { menuApp = app; closeFolderMenu() },
                onDismiss = closeFolderMenu
            )
        }

        folderHeaderMenu?.let { folder ->
            FolderHeaderDialog(
                folder = folder,
                onDelete = { vm.deleteFolder(folder.id); folderHeaderMenu = null },
                onDismiss = { folderHeaderMenu = null }
            )
        }

        // ==== Головне меню (long press на порожньому місці) ====
        if (showMainMenu) {
            LauncherMenuPopup(
                anchor = lastTouch,
                onDismiss = { showMainMenu = false },
                tiles = listOf(
                    MenuTile("🧩", "Віджети") { showAddWidgetDialog = true },
                    MenuTile("🖼", "Шпалери") { showWallpaper = true },
                    MenuTile("⚡", "Макроси") { showMacroManager = true },
                    MenuTile("📝", "Нотатки") { showNotesScreen = true },
                    MenuTile("⚓", "Док") { showDockEditor = true },
                    MenuTile("⚙", "Налаштування") { showSettings = true }
                ),
                items = buildList {
                    add(MenuItemSpec("Створити папку") { showNewFolderDialog = true })
                    add(MenuItemSpec("Автопапки за категоріями") { vm.createAutoFolders() })
                    if (placedWidgetsStorage.hasDashboard())
                        add(MenuItemSpec("Прибрати дошку зліва") {
                            placedWidgetsStorage.removeDashboard()
                            placedWidgets = placedWidgetsStorage.get()
                            currentPage = 0
                        })
                    else
                        add(MenuItemSpec("Створити дошку зліва") {
                            placedWidgetsStorage.createDashboard()
                            placedWidgets = placedWidgetsStorage.get()
                            currentPage = 1
                        })
                    add(MenuItemSpec("Керування папками") { showFolderManager = true })
                    if (pageCount > 1) add(MenuItemSpec("Наступна сторінка (${currentPage + 1}/$pageCount)") {
                        currentPage = (currentPage + 1) % pageCount
                    })
                    add(MenuItemSpec("Оновити контакти") { vm.loadContacts() })
                    add(MenuItemSpec("Очистити історію команд") { vm.clearHistory() })
                }
            )
        }

        if (showNewFolderDialog) {
            NewFolderDialog(
                apps = apps,
                onCreate = { name, pkgs -> vm.createFolder(name, pkgs) },
                onDismiss = { showNewFolderDialog = false }
            )
        }

        openFolder?.let { folder ->
            OpenFolderDialog(
                folder = folder,
                apps = apps,
                inDock = "folder:${folder.id}" in dockPkgs,
                onLaunch = { app -> launchAppWithAnimation(app); openFolder = null },
                onToggleDock = { vm.toggleDock("folder:${folder.id}"); openFolder = null },
                onDismiss = { openFolder = null }
            )
        }

        pendingChoice?.let { candidates ->
            AppChoiceDialog(candidates, onChosen = { vm.onAppChosen(it) }, onDismiss = { vm.dismissChoice() })
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
                        openAppInfo(ctx, app.packageName); menuApp = null
                    }
                    MenuItem("Видалити з телефону") {
                        uninstallApp(ctx, app.packageName); menuApp = null
                    }
                }
            }
        }
    }

    // Анімація запуску поверх усього (поза зумом/затемненням основного екрана)
    LaunchOverlay(launchingApp, lastTouch)

    }
}
