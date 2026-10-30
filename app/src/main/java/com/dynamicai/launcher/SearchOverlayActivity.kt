package com.dynamicai.launcher

import com.dynamicai.launcher.ui.theme.AppTheme
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.command.Command
import com.dynamicai.launcher.command.CommandExecutor
import com.dynamicai.launcher.command.CommandParser
import com.dynamicai.launcher.command.CommandResult
import com.dynamicai.launcher.command.FuzzyMatcher
import com.dynamicai.launcher.command.SettingsRoutes
import com.dynamicai.launcher.data.AppRepository
import com.dynamicai.launcher.model.AppInfo
import com.dynamicai.launcher.suggestion.ActionCatalog
import com.dynamicai.launcher.ui.LiquidSearchBar
import com.dynamicai.launcher.ui.SuggestionActionRow
import com.dynamicai.launcher.ui.SuggestionSettingRow
import com.dynamicai.launcher.ui.AppRow
import com.dynamicai.launcher.ui.iconShape
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.asImageBitmap
import com.dynamicai.launcher.suggestion.Suggestion
import com.dynamicai.launcher.suggestion.stableKey
import kotlinx.coroutines.delay

class SearchOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Прозоре вікно поверх інших
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )
        window.setDimAmount(0.7f)

        setShowWhenLocked(true)
        setTurnScreenOn(true)

        setContent {
            com.dynamicai.launcher.ui.theme.StoredAppTheme {
                OverlaySearchScreen(onClose = { finish() })
            }
        }
    }

    // onPause — не закриваємо, щоб overlay залишався поверх іншої програми

}

@Composable
private fun OverlaySearchScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current

    val parser = remember { CommandParser() }
    val executor = remember { com.dynamicai.launcher.command.CommandExecutorProvider.get(ctx) }
    val repo = remember { AppRepository(ctx) }

    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var response by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    // Підказки — той самий SuggestionProvider, що й на головному екрані (контакти, калькулятор, файли, макроси…)
    val provider = remember { com.dynamicai.launcher.suggestion.SuggestionProvider(ctx) }
    var contacts by remember { mutableStateOf<List<com.dynamicai.launcher.suggestion.ContactInfo>>(emptyList()) }
    var suggestions by remember { mutableStateOf<List<Suggestion>>(emptyList()) }

    LaunchedEffect(query, apps, contacts) {
        if (query.isBlank() || apps.isEmpty()) { suggestions = emptyList(); return@LaunchedEffect }
        delay(35)
        val hidden = try { com.dynamicai.launcher.data.HiddenAppsStorage(ctx).get() } catch (_: Throwable) { emptySet() }
        val focusHidden = try {
            com.dynamicai.launcher.macro.FocusModes.hiddenFor(com.dynamicai.launcher.macro.FocusModes.active.value, apps)
        } catch (_: Throwable) { emptySet<String>() }
        val visible = apps.filter { it.packageName !in hidden && it.packageName !in focusHidden }
        suggestions = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            try { provider.build(query, visible, contacts, executor.flashlightOn.value) } catch (_: Throwable) { emptyList() }
        }
    }

    LaunchedEffect(Unit) {
        apps = try { repo.loadInstalledApps() } catch (_: Throwable) { emptyList() }
        if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_CONTACTS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            contacts = try { com.dynamicai.launcher.suggestion.ContactRepository(ctx).loadAll() } catch (_: Throwable) { emptyList() }
        }
        delay(120)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    fun submit(text: String) {
        if (text.isBlank()) return
        val cmd = parser.parse(text)
        val result = executor.execute(cmd, apps)
        when (result) {
            is CommandResult.Success -> onClose()
            is CommandResult.Error -> response = result.message
            is CommandResult.ChooseApp -> {
                val app = result.candidates.firstOrNull()
                if (app != null) {
                    executor.launchApp(app)
                    onClose()
                }
            }
        }
    }

    fun handleSuggestion(s: Suggestion) {
        if (query.isNotBlank()) com.dynamicai.launcher.suggestion.SearchHistory.add(ctx, query)
        when (s) {
            is Suggestion.App -> {
                try { com.dynamicai.launcher.suggestion.AppUsage.bump(ctx, s.app.packageName) } catch (_: Throwable) {}
                executor.launchApp(s.app)
                onClose()
            }
            is Suggestion.Action -> {
                val result = executor.execute(s.command, apps)
                if (result is CommandResult.Success) onClose()
                else if (result is CommandResult.Error) response = result.message
            }
            is Suggestion.Setting -> {
                val result = executor.execute(Command.OpenSettings(s.route), apps)
                if (result is CommandResult.Success) onClose()
                else if (result is CommandResult.Error) response = result.message
            }
            is Suggestion.Contact -> {
                val r = com.dynamicai.launcher.suggestion.ContactLauncher.run(ctx, s, contacts, apps, executor)
                if (r is CommandResult.Error) response = r.message else onClose()
            }
            is Suggestion.Extra -> {
                com.dynamicai.launcher.suggestion.SuggestionRunner.run(ctx, s)
                onClose()
            }
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) {
                query = text
            }
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

    val settings = remember { runCatching { com.dynamicai.launcher.data.SettingsStorage(ctx).load() }.getOrNull() }
    val iconSize = settings?.iconSizeDp ?: 44
    val fontSize = settings?.fontSizeSp ?: 16
    val showLabels = settings?.showLabels ?: true
    val hiddenNow = remember { try { com.dynamicai.launcher.data.HiddenAppsStorage(ctx).get() } catch (_: Throwable) { emptySet() } }
    // Порожній запит — той самий алфавітний список усіх додатків, що й у пошуку на головному екрані
    val allApps = remember(apps) {
        apps.filter { it.packageName !in hiddenNow }.sortedBy { it.label.lowercase() }
    }
    // Підказки пошуку з інтернету (лише якщо ввімкнено в налаштуваннях)
    var webSugg by remember { mutableStateOf<List<Suggestion>>(emptyList()) }
    LaunchedEffect(query) {
        webSugg = emptyList()
        val q = query.trim()
        val on = try { com.dynamicai.launcher.data.SettingsStorage(ctx).load().webSuggest } catch (_: Throwable) { false }
        if (!on || q.length < 3) return@LaunchedEffect
        delay(250)
        val web = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.dynamicai.launcher.suggestion.BrowserBridge.webSuggestions(q)
        }
        webSugg = web.map {
            Suggestion.Extra("web", "🔎", it, "Підказка пошуку",
                com.dynamicai.launcher.suggestion.BrowserBridge.searchUrl(it), 11)
        }
    }
    val rows: List<Suggestion> = if (query.isBlank()) allApps.map { Suggestion.App(it, 0) }
        else (suggestions + webSugg).distinctBy { it.stableKey() }.sortedByDescending { it.score }

    com.dynamicai.launcher.ui.IconLayoutProvider(settings ?: com.dynamicai.launcher.data.LauncherSettings()) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .pointerInput(Unit) { detectTapGestures { onClose() } }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            LiquidSearchBar(
                query = query,
                onQueryChange = { query = it; response = null },
                onSearch = {
                    val top = suggestions.firstOrNull()
                    val strong = top != null && top.score >= 75 && top !is Suggestion.Contact &&
                        !(top is Suggestion.Extra && (top.kind == "ask" || top.kind == "web"))
                    if (query.isNotBlank() && strong) handleSuggestion(top!!) else submit(query)
                },
                onVoice = { startVoice() },
                onClose = onClose,
                focusRequester = focusRequester,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            )

            LaunchedEffect(response) {
                response?.let {
                    android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_SHORT).show()
                    response = null
                }
            }

            // Часті додатки та історія — як на головному екрані
            if (query.isBlank()) {
                var historyTick by remember { mutableStateOf(0) }
                val history = remember(historyTick) { com.dynamicai.launcher.suggestion.SearchHistory.get(ctx) }
                val frequent = remember(apps) {
                    com.dynamicai.launcher.suggestion.AppUsage.top(ctx, 6).mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
                }
                if (frequent.isNotEmpty()) {
                    Text("Часті", color = AppTheme.colors.textSecondary, fontSize = 11.sp,
                        modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 4.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        frequent.forEach { a ->
                            androidx.compose.foundation.Image(
                                bitmap = (a.bitmap ?: a.icon.toBitmap(96, 96)).asImageBitmap(),
                                contentDescription = a.label,
                                modifier = Modifier
                                    .size(46.dp)
                                    .iconShape()
                                    .pointerInput(a.packageName) {
                                        detectTapGestures(onTap = { handleSuggestion(Suggestion.App(a, 0)) })
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
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                    .background(AppTheme.colors.textPrimary.copy(alpha = 0.08f))
                                    .pointerInput(h) { detectTapGestures { query = h } }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                        Text(
                            "✕", color = AppTheme.colors.textSecondary, fontSize = 13.sp,
                            modifier = Modifier
                                .pointerInput(Unit) {
                                    detectTapGestures { com.dynamicai.launcher.suggestion.SearchHistory.clear(ctx); historyTick++ }
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            if (query.isNotBlank()) {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = com.dynamicai.launcher.ui.gridCellsForIcons(settings?.menuIconSizeDp ?: 56),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                horizontalArrangement = com.dynamicai.launcher.ui.gridGapH(),
                verticalArrangement = Arrangement.spacedBy(com.dynamicai.launcher.ui.LocalGridGapV.current.dp)
            ) {
                if (rows.isEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Нічого не знайдено за \"$query\"", color = AppTheme.colors.textMuted, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Спробуй: ліхтарик · wifi · Аня", color = AppTheme.colors.textMuted.copy(alpha = 0.7f), fontSize = 13.sp)
                        }
                    }
                }
                items(
                    rows,
                    key = { it.stableKey() },
                    span = { s ->
                        androidx.compose.foundation.lazy.grid.GridItemSpan(if (s is Suggestion.App) 1 else maxLineSpan)
                    }
                ) { s ->
                    when (s) {
                        is Suggestion.Action -> SuggestionActionRow(s) { handleSuggestion(s) }
                        is Suggestion.Setting -> SuggestionSettingRow(s) { handleSuggestion(s) }
                        is Suggestion.App -> com.dynamicai.launcher.ui.AppGridItem(
                            app = s.app,
                            iconSizeDp = settings?.menuIconSizeDp ?: 56,
                            fontSizeSp = fontSize,
                            onLongClick = { handleSuggestion(s) },
                            onTap = { handleSuggestion(s) }
                        )
                        is Suggestion.Contact -> com.dynamicai.launcher.ui.SuggestionContactRow(s) { handleSuggestion(s) }
                        is Suggestion.Extra -> com.dynamicai.launcher.ui.SuggestionExtraRow(s) { handleSuggestion(s) }
                    }
                }
            }
            } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
            ) {
                if (rows.isEmpty() && query.isNotBlank()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Нічого не знайдено за \"$query\"", color = AppTheme.colors.textMuted, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Спробуй: ліхтарик · wifi · Аня", color = AppTheme.colors.textMuted.copy(alpha = 0.7f), fontSize = 13.sp)
                        }
                    }
                }
                items(rows, key = { it.stableKey() }) { s ->
                    when (s) {
                        is Suggestion.Action -> SuggestionActionRow(s) { handleSuggestion(s) }
                        is Suggestion.Setting -> SuggestionSettingRow(s) { handleSuggestion(s) }
                        is Suggestion.App -> AppRow(
                            app = s.app,
                            inDock = false,
                            iconSizeDp = iconSize,
                            fontSizeSp = fontSize,
                            showLabel = showLabels,
                            onClick = { handleSuggestion(s) },
                            onLongClick = { handleSuggestion(s) },
                            highlight = query
                        )
                        is Suggestion.Contact -> com.dynamicai.launcher.ui.SuggestionContactRow(s) { handleSuggestion(s) }
                        is Suggestion.Extra -> com.dynamicai.launcher.ui.SuggestionExtraRow(s) { handleSuggestion(s) }
                    }
                }
            }
            }
        }
    }
    }
}
