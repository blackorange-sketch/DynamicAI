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
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.SwitchDefaults
import com.dynamicai.launcher.llm.LlmModel
import com.dynamicai.launcher.llm.ModelDownloader
import com.dynamicai.launcher.llm.ModelRepository
import com.dynamicai.launcher.llm.PromptManager
import com.dynamicai.launcher.overlay.FloatingOrbService
import com.dynamicai.launcher.voice.WakeWordService
import kotlinx.coroutines.delay

/** Налаштування → ШІ: асистент, моделі (локальні й хмарна), голос. */
@Composable
internal fun ColumnScope.SettingsAiSection(
    vm: com.dynamicai.launcher.AppListViewModel,
    settings: LauncherSettings,
    onSettingsChange: (LauncherSettings) -> Unit,
    aiVisible: Boolean,
    showAssistant: Boolean,
    showModels: Boolean,
    showVoice: Boolean,
    llmReady: Boolean,
    onTestLlm: suspend (String) -> String?,
    onNeedOverlayPermission: () -> Unit
) {
    val ctx = LocalContext.current
        var modelFilter by remember { mutableStateOf("all") }
        if (showAssistant) {
        GlassSectionHeader("🤖 ШІ та Асистент")
        GlassSectionHeader("🫧 Плаваючий асистент")
        GlassCard {
            val prefs = remember { ctx.getSharedPreferences("floating_orb", Context.MODE_PRIVATE) }
            var orbEnabled by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Плаваюча куля JARVIS", color = AppTheme.colors.textPrimary, fontSize = 14.sp)
                    Text(
                        if (orbEnabled) "Активна — куля на екрані" else "Вимкнена",
                        color = AppTheme.colors.textSecondary, fontSize = 11.sp
                    )
                }
                Switch(
                    checked = orbEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (!Settings.canDrawOverlays(ctx)) {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                                ctx.startActivity(intent)
                                return@Switch
                            }
                            val intent = Intent(ctx, FloatingOrbService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                ctx.startForegroundService(intent)
                            } else {
                                ctx.startService(intent)
                            }
                            orbEnabled = true
                            prefs.edit().putBoolean("enabled", true).apply()
                        } else {
                            val intent = Intent(ctx, FloatingOrbService::class.java)
                            ctx.stopService(intent)
                            orbEnabled = false
                            prefs.edit().putBoolean("enabled", false).apply()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AppTheme.colors.success
                    )
                )
            }
        }
        }

                if (showAssistant) {
                GlassSectionHeader("🌅 Ранковий брифінг")
        GlassCard {
            val wakeDetector = remember { WakeUpDetector(ctx) }
            var morningEnabled by remember { mutableStateOf(wakeDetector.isEnabled()) }
            val avgHour = remember { wakeDetector.getAverageWakeHour() }
            val avgMinute = remember { wakeDetector.getAverageWakeMinute() }
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Розумний ранковий брифінг", color = AppTheme.colors.textPrimary, fontSize = 14.sp)
                    Text(
                        if (avgHour >= 0) "Середній час пробудження: ${String.format("%02d:%02d", avgHour, avgMinute)}" 
                        else "Ще недостатньо даних (потрібно 3+ дні)",
                        color = AppTheme.colors.textSecondary, fontSize = 11.sp
                    )
                }
                Switch(
                    checked = morningEnabled,
                    onCheckedChange = {
                        morningEnabled = it
                        wakeDetector.setEnabled(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AppTheme.colors.success
                    )
                )
            }
            
            ActionRow(
                "Тестувати брифінг зараз",
                Icons.Default.PlayArrow,
                AppTheme.colors.warning
            ) {
                MorningBriefingService.start(ctx)
            }
        }
                }

        if (aiVisible) {
        if (showModels) {
        // Перемикач рушія
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("all" to "Усі", "litert" to "LiteRT", "mnn" to "MNN").forEach { (key, label) ->
                val selected = modelFilter == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) AppTheme.colors.accent.copy(alpha = 0.3f) else AppTheme.colors.surface)
                        .clickable { modelFilter = key }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = AppTheme.colors.textPrimary, fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        GlassSectionHeader("Локальна LLM")

        // Реактивний стан: оновлюється автоматично після завантаження / видалення
        val downloadedIds by vm.downloadedModelIds.collectAsStateWithLifecycle()
        var modelToDelete by remember { mutableStateOf<LlmModel?>(null) }
        modelToDelete?.let { m ->
            AlertDialog(
                onDismissRequest = { modelToDelete = null },
                containerColor = AppTheme.colors.surface,
                title = { Text("Видалити модель?", color = AppTheme.colors.textPrimary) },
                text = {
                    Text(
                        "${m.name} (${formatModelSize(m.sizeBytes)}) буде видалено з пристрою." +
                            if (vm.currentModelId == m.id) " Це активна модель — буде обрано модель за замовчуванням." else "",
                        color = AppTheme.colors.textSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = { vm.deleteModel(m); modelToDelete = null }) {
                        Text("Видалити", color = AppTheme.colors.danger)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { modelToDelete = null }) {
                        Text("Скасувати", color = AppTheme.colors.textSecondary)
                    }
                }
            )
        }

        // === Backend + Temperature selector ===
        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Прискорювач (Backend)", color = AppTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LlmBackendChip("Auto", "auto", settings.llmBackend) { onSettingsChange(settings.copy(llmBackend = it)) }

                    LlmBackendChip("GPU", "gpu", settings.llmBackend) { onSettingsChange(settings.copy(llmBackend = it)) }
                    LlmBackendChip("CPU", "cpu", settings.llmBackend) { onSettingsChange(settings.copy(llmBackend = it)) }
                }
                Spacer(Modifier.height(12.dp))
                TtsSlider(
                    label = "Температура: ${"%.2f".format(settings.llmTemperature)}",
                    value = settings.llmTemperature,
                    values = listOf(0.0f, 0.2f, 0.4f, 0.6f, 0.8f, 1.0f),
                    onChange = { onSettingsChange(settings.copy(llmTemperature = it)) }
                )
                Text("Зміни застосуються після повторного вибору моделі.",
                     color = AppTheme.colors.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }


        // === LiteRT GPU (експериментальні) + керування рушієм ===
        val llmRestarting by vm.llmRestarting.collectAsStateWithLifecycle()
        val llmReadyNow by vm.llmReady.collectAsStateWithLifecycle()
        val llmStatus by vm.llmStatusText.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) {
            while (true) { vm.checkLlmReady(); delay(3000) }
        }
        // --- Відповіді ---
        GlassSectionHeader("Відповіді")
        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Мова відповіді", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LlmBackendChip("Українська", "uk", settings.answerLanguage) { onSettingsChange(settings.copy(answerLanguage = it)) }
                    LlmBackendChip("Як у питанні", "auto", settings.answerLanguage) { onSettingsChange(settings.copy(answerLanguage = it)) }
                    LlmBackendChip("English", "en", settings.answerLanguage) { onSettingsChange(settings.copy(answerLanguage = it)) }
                }
                Spacer(Modifier.height(10.dp))
                Text("Стиль відповіді", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LlmBackendChip("Коротко (1–3 речення)", "short", settings.answerStyle) { onSettingsChange(settings.copy(answerStyle = it)) }
                    LlmBackendChip("Звичайно", "normal", settings.answerStyle) { onSettingsChange(settings.copy(answerStyle = it)) }
                }
                Text("Макс. довжина відповіді (токени, застосується після перезапуску рушія)", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(128, 256, 512, 1024).forEach { n ->
                        LlmBackendChip(n.toString(), n.toString(), settings.maxResponseTokens.toString()) {
                            onSettingsChange(settings.copy(maxResponseTokens = it.toInt()))
                        }
                    }
                }
                SwitchRow(
                    label = "Автопошук в інтернеті (Вікіпедія + веб) для питань про світ",
                    checked = settings.autoSearch,
                    icon = Icons.Default.Settings,
                    tint = AppTheme.colors.accent
                ) { onSettingsChange(settings.copy(autoSearch = it)) }
                SwitchRow(
                    label = "Швидкі команди без моделі (ліхтарик, додатки, будильник…)",
                    checked = settings.fastCommands,
                    icon = Icons.Default.Settings,
                    tint = AppTheme.colors.success
                ) { onSettingsChange(settings.copy(fastCommands = it)) }
                SwitchRow(
                    label = "Режим агента в голосовому чаті (довгий промпт)",
                    checked = settings.useAgent,
                    icon = Icons.Default.Settings,
                    tint = AppTheme.colors.purple
                ) { onSettingsChange(settings.copy(useAgent = it)) }
            }
        }

        // --- Хмарна модель ---
        GlassSectionHeader("Хмарна модель (Gemini / Groq)")
        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                SwitchRow(
                    label = "Складні питання й пошук — через хмарну модель",
                    checked = settings.cloudEnabled,
                    icon = Icons.Default.Settings,
                    tint = AppTheme.colors.accent
                ) { onSettingsChange(settings.copy(cloudEnabled = it)) }
                Text(
                    "Лаунчер сам шукає в інтернеті, читає кращі сторінки й дає моделі джерела. Прості команди (ліхтарик, додатки…) " +
                        "і балачки лишаються локальними. Без мережі чи ключа працює локальна модель.",
                    color = AppTheme.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("Коли використовувати", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LlmBackendChip("Авто (факти й складні)", "auto", settings.cloudMode) { onSettingsChange(settings.copy(cloudMode = it)) }
                    LlmBackendChip("Усі питання", "always", settings.cloudMode) { onSettingsChange(settings.copy(cloudMode = it)) }
                }
                TextFieldRow("Gemini API-ключ (aistudio.google.com/apikey)", settings.geminiKey, "AIza…", secret = true) {
                    onSettingsChange(settings.copy(geminiKey = it))
                }
                TextFieldRow("Модель Gemini", settings.geminiModel, "gemini-3.5-flash-lite") {
                    onSettingsChange(settings.copy(geminiModel = it))
                }
                TextFieldRow("Groq API-ключ — запасний (console.groq.com/keys)", settings.groqKey, "gsk_…", secret = true) {
                    onSettingsChange(settings.copy(groqKey = it))
                }
                TextFieldRow("Модель Groq", settings.groqModel, "openai/gpt-oss-120b") {
                    onSettingsChange(settings.copy(groqModel = it))
                }
                CloudTestRow(settings)
                Text(
                    "Приватність: назовні йде лише питання, кілька останніх реплік і публічні уривки знайдених сторінок. " +
                        "Контакти, нотатки, повідомлення й текст екрана не надсилаються. На безкоштовному рівні Google може " +
                        "використовувати запити для покращення продуктів — не питайте там особистого.",
                    color = AppTheme.colors.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp)
                )
            }
        }

        // --- Пам'ять і рушій ---
        GlassSectionHeader("Пам'ять і рушій")
        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Вивантажувати модель з пам'яті після простою", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("3 хв" to 3, "15 хв" to 15, "60 хв" to 60, "Ніколи" to 0).forEach { (label, min) ->
                        LlmBackendChip(label, min.toString(), settings.llmUnloadMinutes.toString()) {
                            onSettingsChange(settings.copy(llmUnloadMinutes = it.toInt()))
                        }
                    }
                }
                SwitchRow(
                    label = "Завантажувати модель у фоні при старті",
                    checked = settings.llmPreload,
                    icon = Icons.Default.Settings,
                    tint = AppTheme.colors.accent
                ) { onSettingsChange(settings.copy(llmPreload = it)) }
                Spacer(Modifier.height(8.dp))

                val stateText = when {
                    llmRestarting -> "… " + llmStatus.ifBlank { "Перезапуск…" }
                    llmReadyNow -> "● Рушій працює"
                    else -> "○ Рушій вимкнений (завантажиться при першому запиті)"
                }
                Text(
                    stateText,
                    color = if (llmReadyNow && !llmRestarting) AppTheme.colors.success else AppTheme.colors.textSecondary,
                    fontSize = 12.sp
                )
                if (!llmRestarting && llmStatus.isNotBlank()) {
                    Text(llmStatus, color = AppTheme.colors.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !llmRestarting, onClick = { vm.restartLlmEngine(clearGpuCache = false) }) {
                        Text("Перезапустити рушій", color = AppTheme.colors.accent, fontSize = 12.sp)
                    }
                    TextButton(enabled = !llmRestarting, onClick = { vm.restartLlmEngine(clearGpuCache = true) }) {
                        Text("+ очистити кеш GPU", color = AppTheme.colors.danger, fontSize = 12.sp)
                    }
                }
            }
        }

        // --- LiteRT (експериментально), згорнуто за замовчуванням ---
        var litertOpen by remember { mutableStateOf(false) }
        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { litertOpen = !litertOpen },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("LiteRT GPU (експериментально)", color = AppTheme.colors.textPrimary, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(if (litertOpen) "▲" else "▼", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                }
                if (litertOpen) {
                    Text(
                        "Якщо Gemma на GPU видає нісенітниці, спробуйте FLOAT32 і вимкнений MTP. Після змін перезапустіть рушій.",
                        color = AppTheme.colors.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                Text("Точність активацій", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LlmBackendChip("За замовч.", "default", settings.litertActivation) { onSettingsChange(settings.copy(litertActivation = it)) }
                    LlmBackendChip("FLOAT32", "float32", settings.litertActivation) { onSettingsChange(settings.copy(litertActivation = it)) }
                    LlmBackendChip("FLOAT16", "float16", settings.litertActivation) { onSettingsChange(settings.copy(litertActivation = it)) }
                }
                Spacer(Modifier.height(10.dp))
                Text("Speculative decoding (MTP)", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LlmBackendChip("За замовч.", "default", settings.litertMtp) { onSettingsChange(settings.copy(litertMtp = it)) }
                    LlmBackendChip("Увімк.", "on", settings.litertMtp) { onSettingsChange(settings.copy(litertMtp = it)) }
                    LlmBackendChip("Вимк.", "off", settings.litertMtp) { onSettingsChange(settings.copy(litertMtp = it)) }
                }
                SwitchRow(
                    label = "Окремий кеш для кожної комбінації",
                    checked = settings.litertSeparateCache,
                    icon = Icons.Default.Settings,
                    tint = AppTheme.colors.accent
                ) { onSettingsChange(settings.copy(litertSeparateCache = it)) }

                }
            }
        }

        // === MNN Models ===
        if (modelFilter == "all" || modelFilter == "mnn") {
            // Список і розміри беремо з ModelRepository (єдине джерело правди)
            val mnnModels = remember {
                ModelRepository.AVAILABLE_MODELS
                    .filter { it.id.startsWith("mnn-") }
                    .map { Triple(it.id, it.name.removePrefix("MNN ").substringBefore(" ("), formatModelSize(it.sizeBytes)) }
            }
            val mnnProgress = vm.downloadProgress.collectAsStateWithLifecycle().value

            GlassCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("MNN моделі", color = AppTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Row {
                            TextButton(onClick = { try { com.dynamicai.launcher.CrashDiagnostics.share(ctx) } catch (_: Throwable) {} }) {
                                Text("Лог", color = AppTheme.colors.accent, fontSize = 11.sp)
                            }
                            TextButton(onClick = { vm.clearMnnCache() }) {
                                Text("Очистити кеш", color = AppTheme.colors.danger, fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    mnnModels.forEach { (id, name, size) ->
                        val isCurrent = vm.currentModelId == id
                        val isDownloaded = id in downloadedIds
                        val isDownloading = vm.downloadingModelId == id && mnnProgress is ModelDownloader.Progress.Downloading

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isDownloading) {
                                    if (isDownloaded) {
                                        if (!isCurrent) vm.setCurrentModel(id)
                                    } else {
                                        vm.downloadMnnModelById(id)
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, color = AppTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                if (isDownloaded) {
                                    Text(if (isCurrent) "✓ Активна" else "✓ Завантажено • Обрати", color = if (isCurrent) AppTheme.colors.success else AppTheme.colors.accent, fontSize = 11.sp)
                                } else if (isDownloading) {
                                    val prog = mnnProgress as ModelDownloader.Progress.Downloading
                                    LinearProgressIndicator(progress = { prog.percent / 100f }, modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 6.dp), color = AppTheme.colors.accent)
                                    Text("${prog.percent}% (${prog.downloadedMb} / ${prog.totalMb} МБ)", color = AppTheme.colors.accent, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                                } else {
                                    Text("$size • Натисніть для завантаження", color = AppTheme.colors.textSecondary, fontSize = 11.sp)
                                }
                            }
                            if (isDownloaded) {
                                IconButton(enabled = !isDownloading, onClick = {
                                    modelToDelete = ModelRepository.AVAILABLE_MODELS.find { it.id == id }
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Видалити",
                                        tint = AppTheme.colors.danger
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        GlassCard {
            val progressState = vm.downloadProgress.collectAsStateWithLifecycle().value

            val filteredModels = ModelRepository.AVAILABLE_MODELS.filter { m ->
            when (modelFilter) {
                "litert" -> !m.id.startsWith("mnn-")
                "mnn" -> m.id.startsWith("mnn-")
                else -> true
            }
        }
        filteredModels.filterNot { it.id.startsWith("mnn-") }.forEach { model ->
                val isDownloaded = model.id in downloadedIds
                val isSelected = model.id == vm.currentModelId
                
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(model.name, color = AppTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "Підтримка: ${model.supportedBackends.joinToString(", ")}",
                            color = AppTheme.colors.textMuted,
                            fontSize = 10.sp
                        )
                            
                            val statusText = if (isDownloaded) "Завантажено" else "Не завантажено (${model.sizeBytes / 1_000_000} МБ)"
                            Text(statusText, color = AppTheme.colors.textSecondary, fontSize = 11.sp)
                            
                            if (vm.downloadingModelId == model.id && progressState is ModelDownloader.Progress.Downloading && !isDownloaded) {
                                Text("Завантаження... ${progressState.percent}%", color = AppTheme.colors.accent, fontSize = 11.sp)
                            }
                        }
                        
                        if (isDownloaded) {
                            if (isSelected) {
                                Text("✓ Активна", color = AppTheme.colors.success, fontSize = 12.sp)
                            } else {
                                TextButton(onClick = { vm.setCurrentModel(model.id) }) {
                                    Text("Обрати", color = AppTheme.colors.accent, fontSize = 12.sp)
                                }
                            }
                            IconButton(onClick = { modelToDelete = model }) {
                                Icon(Icons.Default.Delete, contentDescription = "Видалити", tint = AppTheme.colors.danger)
                            }
                        } else {
                            if (vm.downloadingModelId == model.id && progressState is ModelDownloader.Progress.Downloading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AppTheme.colors.accent, strokeWidth = 2.dp)
                            } else {
                                TextButton(
                                    onClick = { vm.downloadModel(model) },
                                    enabled = vm.downloadingModelId == null
                                ) {
                                    Text("Завантажити", color = AppTheme.colors.accent, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    
                    // Блок прогресу та помилок (з'являється тільки для моделі, що завантажується)
                    if (vm.downloadingModelId == model.id) {
                        val prog = vm.downloadProgress.value
                        if (prog is ModelDownloader.Progress.Downloading) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { prog.percent / 100f },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = AppTheme.colors.accent
                            )
                            val progressText = if (prog.totalMb > 0) 
                                "${prog.percent}% (${prog.downloadedMb} / ${prog.totalMb} МБ)" 
                            else 
                                "${prog.downloadedMb} МБ завантажено..."
                            Text(progressText, color = AppTheme.colors.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        } else if (prog is ModelDownloader.Progress.Error) {
                            Text("Помилка: ${prog.message}", color = AppTheme.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            TextButton(onClick = { vm.clearDownloadError() }) {
                                Text("Сховати", color = AppTheme.colors.textMuted, fontSize = 11.sp)
                            }
                        }
                    }
                    
                    HorizontalDivider(color = AppTheme.colors.textPrimary.copy(alpha = 0.1f), modifier = Modifier.padding(top = 8.dp))
                }
            }
}  // LiteRT GlassCard
            }  // if (showModels)
            if (showVoice) {
            GlassCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    VoskSettingsBlock(settings, onSettingsChange)
                }
            }
            GlassCard {
            // Toggle завжди видно
            SwitchRow(
                label = "Озвучувати відповіді (TTS)",
                checked = settings.useTts,
                icon = Icons.Default.PlayArrow,
                tint = AppTheme.colors.success
            ) {
                onSettingsChange(settings.copy(useTts = it))
            }
            ActionRow(
                "Обрати голос TTS",
                icon = Icons.Default.Settings,
                tint = AppTheme.colors.purple
            ) {
                try {
                    val intent = Intent("com.android.settings.TTS_SETTINGS")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                } catch (_: Exception) {}
            }
            // TTS слайдери
            TtsSlider(
                label = "Швидкість озвучення",
                value = settings.ttsSpeed,
                values = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f),
                onChange = { onSettingsChange(settings.copy(ttsSpeed = it)) }
            )
            TtsSlider(
                label = "Тон голосу",
                value = settings.ttsPitch,
                values = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f),
                onChange = { onSettingsChange(settings.copy(ttsPitch = it)) }
            )
            SwitchRow(
                label = "Активація фразою (експериментально)",
                checked = settings.wakeWordEnabled,
                icon = Icons.Default.Search,
                tint = AppTheme.colors.warning
            ) { on ->
                onSettingsChange(settings.copy(wakeWordEnabled = on))
                val appCtx = ctx.applicationContext
                if (on) {
                    val ok = ContextCompat.checkSelfPermission(appCtx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (ok) {
                        WakeWordService.start(appCtx)
                    }
                    if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(appCtx)) {
                        onNeedOverlayPermission()
                    }
                } else {
                    WakeWordService.stop(appCtx)
                }
            }
            if (settings.wakeWordEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Фраза:",
                        color = AppTheme.colors.textPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.width(60.dp)
                    )
                    BasicTextField(
                        value = settings.wakeWord,
                        onValueChange = { onSettingsChange(settings.copy(wakeWord = it)) },
                        textStyle = TextStyle(color = AppTheme.colors.textPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(AppTheme.colors.accent),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppTheme.colors.textPrimary.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
                Text(
                    "Потребує постійного слухання — буде додано у наступній версії",
                    color = AppTheme.colors.textSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            
}  // GlassCard (voice)
            }  // if (showVoice)
            if (showModels) {
                        GlassSectionHeader("Промпти для рушіїв (файли)")
        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                val localCtx = LocalContext.current
                var mnnText by remember { mutableStateOf(PromptManager.loadMnn(localCtx)) }
                var litertText by remember { mutableStateOf(PromptManager.loadLitert(localCtx)) }

                Text("MNN Prompt ({{question}})", color = AppTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("Шаблон чату (<|im_start|>…) MNN додає сам. Тут — інструкція й {{question}}", color = AppTheme.colors.textSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 4.dp))
                BasicTextField(
                    value = mnnText,
                    onValueChange = { mnnText = it },
                    textStyle = TextStyle(color = AppTheme.colors.textPrimary, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth().height(100.dp).background(AppTheme.colors.textPrimary.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).padding(8.dp)
                )
                
                Spacer(Modifier.height(16.dp))
                
                Text("LiteRT Prompt (Gemma та інші)", color = AppTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("Шаблон чату (<start_of_turn>…) LiteRT додає сам. Тут — інструкція й {{question}}", color = AppTheme.colors.textSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 4.dp))
                BasicTextField(
                    value = litertText,
                    onValueChange = { litertText = it },
                    textStyle = TextStyle(color = AppTheme.colors.textPrimary, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth().height(100.dp).background(AppTheme.colors.textPrimary.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).padding(8.dp)
                )
                
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = {
                        PromptManager.saveMnn(localCtx, mnnText)
                        PromptManager.saveLitert(localCtx, litertText)
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Зберегти", color = AppTheme.colors.accent)
                }
            }
        }

}  // if (showModels): prompts
            if (showAssistant) {
            GlassCard {
            SwitchRow(
                label = "Використовувати LLM для команд",
                checked = settings.useLlm,
                icon = Icons.Default.Settings,
                tint = AppTheme.colors.purple
            ) {
                onSettingsChange(settings.copy(useLlm = it))
            }

            if (llmReady) {
                LlmTestRow(onTest = onTestLlm)
            }
        }
            }  // if (showAssistant)
        }

}
