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

            // Помилки команд — коротким тостом замість постійного синього тексту
            LaunchedEffect(response) {
                response?.let {
                    android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_SHORT).show()
                    response = null
                }
            }

            // Список підказок — glass-картка
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                        .background(AppTheme.colors.surface.copy(alpha = 0.94f))
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
