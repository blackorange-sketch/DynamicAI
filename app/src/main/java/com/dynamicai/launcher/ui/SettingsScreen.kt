package com.dynamicai.launcher.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.RowScope
import com.dynamicai.launcher.ui.theme.AppTheme
import android.content.Intent
import android.net.Uri
import android.provider.Settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.dynamicai.launcher.data.LauncherSettings
import com.dynamicai.launcher.system.ShizukuManager
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import com.dynamicai.launcher.music.GoogleAuthManager
import androidx.compose.material.icons.filled.AccountCircle
import com.dynamicai.launcher.morning.WakeUpDetector
import com.dynamicai.launcher.morning.MorningBriefingService
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import com.dynamicai.launcher.data.AppRepository
import com.dynamicai.launcher.model.AppInfo

@Composable
fun SettingsScreen(
    vm: com.dynamicai.launcher.AppListViewModel,
    settings: LauncherSettings,
    onSettingsChange: (LauncherSettings) -> Unit,
    hiddenCount: Int,
    foldersCount: Int,
    onClearHistory: () -> Unit,
    onRefreshContacts: () -> Unit,
    onClearHidden: () -> Unit,
    onDownloadLlm: () -> Unit,
    overlayActive: Boolean,
    overlayPermissionGranted: Boolean,
    onToggleOverlay: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onOpenWallpaper: () -> Unit,
    onOpenDockEditor: () -> Unit,
    onOpenFolderManager: () -> Unit,
    onOpenMacroManager: () -> Unit = {},
    onOpenNotes: () -> Unit = {},
    llmDownloaded: Boolean,
    llmReady: Boolean,
    versionName: String,
    onClose: () -> Unit,
    onTestLlm: suspend (String) -> String? = { null },
    initialCategory: String? = null,
    onBackToMenu: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background.copy(alpha = settings.backgroundAlphaPercent / 100f))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        // Header — glass
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (initialCategory != null) onBackToMenu() else onClose()
            }) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = AppTheme.colors.textPrimary)
            }
            Text("Налаштування", color = AppTheme.colors.textPrimary, fontSize = 22.sp)
        }

        val ctx = LocalContext.current
        // initialCategory: "ai" | "ai:assistant" | "ai:models" | "ai:voice" | "tools" | "appearance" | "system" | null (усе)
        val cat = initialCategory?.substringBefore(':')
        var aiTab by remember { mutableStateOf(initialCategory?.substringAfter(':', "")?.ifBlank { null } ?: "models") }
        val showAssistant = initialCategory == null || (cat == "ai" && aiTab == "assistant")
        val showModels = initialCategory == null || (cat == "ai" && aiTab == "models")
        val showVoice = initialCategory == null || (cat == "ai" && aiTab == "voice")
        if (cat == "ai") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LlmBackendChip("Асистент", "assistant", aiTab) { aiTab = it }
                LlmBackendChip("Моделі", "models", aiTab) { aiTab = it }
                LlmBackendChip("Голос", "voice", aiTab) { aiTab = it }
            }
        }
            var showOverlayPermDialog by remember { mutableStateOf(false) }

        if (showOverlayPermDialog) {
            AlertDialog(
                onDismissRequest = { showOverlayPermDialog = false },
                title = { Text("Дозвіл для голосової активації", color = AppTheme.colors.textPrimary) },
                text = {
                    Text(
                        "Щоб показувати вікно підтвердження команд, дозвольте DynamicAI показ поверх інших застосунків.",
                        color = AppTheme.colors.textSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showOverlayPermDialog = false
                        try {
                            val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(i)
                        } catch (_: Throwable) {}
                    }) { Text("Дозволити", color = AppTheme.colors.accent) }
                },
                dismissButton = {
                    TextButton(onClick = { showOverlayPermDialog = false }) {
                        Text("Пізніше", color = AppTheme.colors.textSecondary)
                    }
                },
                containerColor = AppTheme.colors.surface
            )
        }


        if (initialCategory == null || cat == "tools") {
            SettingsToolsSection(vm, settings, onSettingsChange, onOpenNotes, onOpenMacroManager)
        }
        if (initialCategory == null || cat == "appearance") {
            SettingsAppearanceSection(
                settings, onSettingsChange, overlayActive, overlayPermissionGranted,
                onToggleOverlay, onRequestOverlayPermission, onOpenWallpaper, onOpenDockEditor, onOpenFolderManager
            )
        }
        if (initialCategory == null || cat == "system") {
            SettingsSystemSection(settings, onSettingsChange, onClearHistory, onRefreshContacts)
        }
        SettingsAiSection(
            vm = vm, settings = settings, onSettingsChange = onSettingsChange,
            aiVisible = initialCategory == null || cat == "ai",
            showAssistant = showAssistant, showModels = showModels, showVoice = showVoice,
            llmReady = llmReady, onTestLlm = onTestLlm,
            onNeedOverlayPermission = { showOverlayPermDialog = true }
        )

        Spacer(Modifier.height(16.dp))
        Text(
            "DynamicAI · $versionName · папок: $foldersCount",
            color = AppTheme.colors.glassBorder,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(48.dp))
    }
}

// ===== Компоненти =====

@Composable
internal fun RowScope.ThemeChip(label: String, themeId: String, currentTheme: String, onSelect: (String) -> Unit) {
    val selected = currentTheme == themeId
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AppTheme.colors.accent.copy(alpha = 0.3f) else AppTheme.colors.surface)
            .border(1.dp, if (selected) AppTheme.colors.accent else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable { onSelect(themeId) }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = AppTheme.colors.textPrimary, fontSize = 14.sp)
    }
}

@Composable
internal fun RowScope.LlmBackendChip(label: String, backendId: String, current: String, onSelect: (String) -> Unit) {
    val selected = current == backendId
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AppTheme.colors.accent.copy(alpha = 0.3f) else AppTheme.colors.surface)
            .border(1.dp, if (selected) AppTheme.colors.accent else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable { onSelect(backendId) }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = AppTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

/** 1_436_000_000 -> "1.4 ГБ", 614_000_000 -> "614 МБ" */
internal fun formatModelSize(bytes: Long): String =
    if (bytes >= 1_000_000_000L) "%.1f ГБ".format(bytes / 1_000_000_000.0) else "${bytes / 1_000_000} МБ"


/** Розпізнавання голосу: Google (онлайн) або Vosk (офлайн, українська). Модель завантажується тут же. */
@Composable
internal fun VoskSettingsBlock(settings: LauncherSettings, onSettingsChange: (LauncherSettings) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(com.dynamicai.launcher.voice.VoskEngine.isInstalled(ctx)) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Text("Розпізнавання голосу", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LlmBackendChip("Google", "google", settings.voiceEngine) { onSettingsChange(settings.copy(voiceEngine = it)) }
        LlmBackendChip("Авто", "auto", settings.voiceEngine) { onSettingsChange(settings.copy(voiceEngine = it)) }
        LlmBackendChip("Vosk офлайн", "vosk", settings.voiceEngine) { onSettingsChange(settings.copy(voiceEngine = it)) }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        "Авто: Google з інтернетом, Vosk без нього. Vosk працює повністю на телефоні (українська модель ~140 МБ).",
        color = AppTheme.colors.textSecondary, fontSize = 11.sp
    )
    Spacer(Modifier.height(6.dp))
    if (installed) {
        Text("Модель Vosk встановлено", color = AppTheme.colors.success, fontSize = 12.sp)
        TextButton(onClick = {
            com.dynamicai.launcher.voice.VoskEngine.delete(ctx)
            installed = false
            status = ""
            if (settings.voiceEngine != "google") onSettingsChange(settings.copy(voiceEngine = "google"))
        }) { Text("Видалити модель") }
    } else {
        Button(enabled = !busy, onClick = {
            busy = true
            status = "Початок завантаження…"
            scope.launch {
                val err = com.dynamicai.launcher.voice.VoskEngine.download(ctx) { msg -> status = msg }
                busy = false
                if (err == null) {
                    installed = true
                    status = ""
                    if (settings.voiceEngine == "google") onSettingsChange(settings.copy(voiceEngine = "auto"))
                } else status = "Помилка: $err"
            }
        }) { Text(if (busy) "Завантаження…" else "Завантажити модель Vosk (~140 МБ)") }
        if (status.isNotBlank()) Text(status, color = AppTheme.colors.textSecondary, fontSize = 12.sp)
    }
}


/** Сценарій «включи музику в машині»: вибір Bluetooth-пристрою авто. */
@Composable
internal fun CarModeSettingsBlock(settings: LauncherSettings, onSettingsChange: (LauncherSettings) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Text("Bluetooth-пристрій авто", color = AppTheme.colors.textPrimary, fontSize = 14.sp)
    Spacer(Modifier.height(2.dp))
    Text(
        "Використовується в команді «включи музику в машині» та у макросах («Моє авто»): інтернет → Bluetooth → підключення → YouTube Music → Play. Режим польоту й мобільні дані вимикаються через Shizuku.",
        color = AppTheme.colors.textSecondary, fontSize = 11.sp
    )
    Spacer(Modifier.height(6.dp))
    TextButton(onClick = { showPicker = true }) {
        Text(if (settings.carBtName.isBlank()) "Обрати пристрій…" else "✓ ${settings.carBtName} — змінити",
            color = if (settings.carBtName.isBlank()) AppTheme.colors.warning else AppTheme.colors.successLight)
    }
    if (showPicker) {
        com.dynamicai.launcher.macro.BtDevicePickerDialog(
            title = "🚗 Bluetooth-пристрій авто", current = settings.carBtName, anyLabel = "Не вибрано",
            onPick = { onSettingsChange(settings.copy(carBtName = it)); showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
}


/** Локальне керування Xiaomi-пилососом (miIO): IP, токен, протокол і перевірка зв'язку. */
@Composable
internal fun VacuumSettingsBlock(settings: LauncherSettings, onSettingsChange: (LauncherSettings) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var testResult by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    val tfColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = AppTheme.colors.textPrimary, unfocusedTextColor = AppTheme.colors.textPrimary,
        focusedBorderColor = AppTheme.colors.successLight, unfocusedBorderColor = AppTheme.colors.glassBorder,
        cursorColor = AppTheme.colors.successLight
    )
    Text(
        "Керування пилососом по Wi-Fi без додатка: «запусти пилосос», «пилосос додому», «де пилосос». Потрібні IP пристрою (з роутера або Xiaomi Home → пристрій → Налаштування → Інформація про пристрій) і токен із 32 символів (його дістає утиліта Xiaomi-cloud-tokens-extractor). Працює вдома, в одній мережі з пилососом.",
        color = AppTheme.colors.textSecondary, fontSize = 11.sp
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = settings.vacuumIp, onValueChange = { onSettingsChange(settings.copy(vacuumIp = it.trim())) },
        label = { Text("IP пилососа", fontSize = 12.sp) }, singleLine = true, colors = tfColors, modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = settings.vacuumToken, onValueChange = { onSettingsChange(settings.copy(vacuumToken = it.trim())) },
        label = { Text("Токен (32 символи)", fontSize = 12.sp) }, singleLine = true, colors = tfColors, modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Text("Протокол", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        listOf("auto" to "Авто", "miot" to "Dreame / Mop 2 Pro", "roborock" to "Roborock").forEach { (id, label) ->
            val sel = settings.vacuumProto == id
            Box(
                Modifier.clip(RoundedCornerShape(16.dp))
                    .background(if (sel) AppTheme.colors.successLight.copy(alpha = 0.3f) else AppTheme.colors.textPrimary.copy(alpha = 0.06f))
                    .clickable { onSettingsChange(settings.copy(vacuumProto = id)) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) { Text(label, color = AppTheme.colors.textPrimary, fontSize = 12.sp) }
        }
    }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = {
        testing = true; testResult = "Перевіряю…"
        scope.launch {
            testResult = try { com.dynamicai.launcher.smarthome.VacuumControl(ctx).run("status") } catch (t: Throwable) { "Помилка: ${t.message}" }
            testing = false
        }
    }, enabled = !testing) { Text("Перевірити зв'язок", color = AppTheme.colors.successLight) }
    if (testResult.isNotBlank()) Text(testResult, color = AppTheme.colors.textPrimary, fontSize = 12.sp)
}


/** Вибір дії для жесту: стандартні варіанти або будь-який макрос. */
@Composable
internal fun HiddenAppsSection(vm: com.dynamicai.launcher.AppListViewModel) {
    val hidden by vm.hidden.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val list = remember(hidden, apps) { apps.filter { it.packageName in hidden }.sortedBy { it.label.lowercase() } }
    GlassSectionHeader("Приховані застосунки")
    GlassCard {
        ActionRow("Керувати прихованими (${hidden.size})", Icons.Default.Info, AppTheme.colors.textMuted) { open = true }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = AppTheme.colors.surface,
            title = { Text("Приховані застосунки", color = AppTheme.colors.textPrimary, fontSize = 16.sp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 400.dp)) {
                    if (list.isEmpty()) {
                        Text("Прихованих застосунків немає. Сховати застосунок можна довгим натисканням на ньому.",
                            color = AppTheme.colors.textSecondary, fontSize = 13.sp)
                    }
                    list.forEach { app ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            app.bitmap?.let {
                                androidx.compose.foundation.Image(
                                    bitmap = it.asImageBitmap(), contentDescription = null,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(app.label, color = AppTheme.colors.textPrimary, fontSize = 14.sp,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.unhideApp(app.packageName) }) {
                                Text("Показати", color = AppTheme.colors.accent, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (list.isNotEmpty()) TextButton(onClick = { vm.clearHidden() }) {
                    Text("Показати всі", color = AppTheme.colors.accent)
                }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Закрити", color = AppTheme.colors.textSecondary) } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GestureRow(title: String, value: String, onPick: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var showMacros by remember { mutableStateOf(false) }
    val macroName = remember(value) {
        if (value.startsWith("macro:")) com.dynamicai.launcher.macro.MacroStorage(ctx).get().firstOrNull { it.id == value.removePrefix("macro:") }?.name else null
    }
    Text(title, color = AppTheme.colors.textPrimary, fontSize = 14.sp)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val current = if (value.startsWith("macro:")) "macro" else value
        listOf("Нічого" to "none", "Шторка" to "notifications", "Шухляда" to "drawer", "Пошук" to "search", "Голос" to "voice", "Блок." to "lock", "Макрос" to "macro").forEach { (label, id) ->
            LlmBackendChip(label, id, current) { if (it == "macro") showMacros = true else onPick(it) }
        }
    }
    if (macroName != null) Text("⚡ $macroName", color = AppTheme.colors.successLight, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    if (showMacros) {
        val macros = remember { com.dynamicai.launcher.macro.MacroStorage(ctx).get() }
        AlertDialog(
            onDismissRequest = { showMacros = false },
            containerColor = AppTheme.colors.surface,
            title = { Text("Який макрос запускати?", color = AppTheme.colors.textPrimary, fontSize = 16.sp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 360.dp)) {
                    if (macros.isEmpty()) Text("Макросів ще немає — створіть їх у «Керування макросами».", color = AppTheme.colors.textSecondary, fontSize = 13.sp)
                    macros.forEach { m ->
                        Text("${m.icon}  ${m.name}", color = AppTheme.colors.textPrimary, fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth().clickable { onPick("macro:" + m.id); showMacros = false }.padding(vertical = 10.dp))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMacros = false }) { Text("Закрити", color = AppTheme.colors.textSecondary) } }
        )
    }
}


/** Експорт/імпорт налаштувань, макросів, нотаток, віджетів і розмов одним файлом. */
@Composable
internal fun BackupSettingsBlock() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var status by remember { mutableStateOf("") }
    var askRestart by remember { mutableStateOf(false) }
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) status = try {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(com.dynamicai.launcher.util.BackupManager.export(ctx).toByteArray(Charsets.UTF_8)) }
            "✓ Копію збережено"
        } catch (t: Throwable) { "Помилка збереження: ${t.message}" }
    }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                val n = com.dynamicai.launcher.util.BackupManager.import(ctx, text)
                status = "✓ Відновлено розділів: $n"
                askRestart = true
            } catch (t: Throwable) { status = "Помилка імпорту: ${t.message}" }
        }
    }
    Text(
        "Зберігає налаштування, макроси, нотатки, віджети, папки, док і історію розмов в один файл. Файл містить і токен пилососа — не діліться ним.",
        color = AppTheme.colors.textSecondary, fontSize = 11.sp
    )
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = {
            exportLauncher.launch("dynamicai-backup-" + java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date()) + ".json")
        }) { Text("Експортувати", color = AppTheme.colors.successLight) }
        TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
            Text("Імпортувати", color = AppTheme.colors.accent)
        }
    }
    if (status.isNotBlank()) Text(status, color = AppTheme.colors.textPrimary, fontSize = 12.sp)
    if (askRestart) {
        AlertDialog(
            onDismissRequest = { askRestart = false },
            containerColor = AppTheme.colors.surface,
            title = { Text("Копію відновлено", color = AppTheme.colors.textPrimary) },
            text = { Text("Щоб застосувати зміни, лаунчер потрібно перезапустити. Він запуститься знову автоматично.", color = AppTheme.colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = { android.os.Process.killProcess(android.os.Process.myPid()) }) {
                    Text("Перезапустити", color = AppTheme.colors.accent)
                }
            },
            dismissButton = { TextButton(onClick = { askRestart = false }) { Text("Пізніше", color = AppTheme.colors.textSecondary) } }
        )
    }
}
