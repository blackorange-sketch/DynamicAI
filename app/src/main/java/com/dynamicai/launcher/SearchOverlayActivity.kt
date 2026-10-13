package com.dynamicai.launcher

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
import com.dynamicai.launcher.suggestion.Suggestion
import com.dynamicai.launcher.suggestion.stableKey
import kotlinx.coroutines.delay

class SearchOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0.7f)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                OverlaySearchScreen(onClose = { finish() })
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isFinishing) finish()
    }
}

@Composable
private fun OverlaySearchScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current

    val parser = remember { CommandParser() }
    val executor = remember { CommandExecutor(ctx) }
    val repo = remember { AppRepository(ctx) }

    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var response by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    // Підказки — ті самі, що на головному
    val suggestions: List<Suggestion> = remember(query, apps) {
        if (query.isBlank() || apps.isEmpty()) emptyList()
        else {
            val q = query.lowercase().trim()
            val result = mutableListOf<Suggestion>()

            // 1. Дії
            ActionCatalog.all
                .map { a ->
                    val best = a.keywords.maxOf { kw -> FuzzyMatcher.score(kw, q) }
                    a to best
                }
                .filter { it.second >= 55 }
                .sortedByDescending { it.second }
                .take(4)
                .forEach { (a, score) ->
                    result.add(
                        Suggestion.Action(
                            id = a.id,
                            label = a.label,
                            subtitle = a.subtitle,
                            command = a.build(),
                            score = score
                        )
                    )
                }

            // 2. Налаштування
            SettingsRoutes.all().keys
                .map { it to FuzzyMatcher.score(it, q) }
                .filter { it.second >= 60 }
                .sortedByDescending { it.second }
                .take(2)
                .forEach { (route, score) ->
                    result.add(
                        Suggestion.Setting(
                            label = "Налаштування: ${route.replaceFirstChar { it.uppercase() }}",
                            route = route,
                            score = score - 5
                        )
                    )
                }

            // 3. Застосунки
            apps
                .map { it to FuzzyMatcher.score(it.label, q) }
                .filter { it.second >= 35 }
                .sortedByDescending { it.second }
                .take(8)
                .forEach { (app, score) ->
                    result.add(Suggestion.App(app, score))
                }

            result.sortedByDescending { it.score }
        }
    }

    LaunchedEffect(Unit) {
        apps = try { repo.loadInstalledApps() } catch (_: Throwable) { emptyList() }
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
        when (s) {
            is Suggestion.App -> {
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
                // Контакти у overlay поки не підтримуються
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .pointerInput(Unit) {
                detectTapGestures { onClose() }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            // Використовуємо той самий LiquidSearchBar
            LiquidSearchBar(
                query = query,
                onQueryChange = {
                    query = it
                    response = null
                },
                onSearch = { submit(query) },
                onVoice = { startVoice() },
                onClose = onClose,
                focusRequester = focusRequester,
                modifier = Modifier.fillMaxWidth()
            )

            if (response != null) {
                Text(
                    response!!,
                    color = Color(0xFF64B5F6),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }

            // Список підказок — glass-картка
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                        .background(Color(0xF01A1A1A))
                        .padding(vertical = 6.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        items(suggestions, key = { it.stableKey() }) { s ->
                            when (s) {
                                is Suggestion.Action -> SuggestionActionRow(s) { handleSuggestion(s) }
                                is Suggestion.Setting -> SuggestionSettingRow(s) { handleSuggestion(s) }
                                is Suggestion.App -> AppRow(
                                    app = s.app,
                                    inDock = false,
                                    iconSizeDp = 44,
                                    fontSizeSp = 16,
                                    showLabel = true,
                                    onClick = { handleSuggestion(s) },
                                    onLongClick = { handleSuggestion(s) }
                                )
                                is Suggestion.Contact -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}
