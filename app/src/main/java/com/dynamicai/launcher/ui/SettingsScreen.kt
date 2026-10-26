package com.dynamicai.launcher.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.dynamicai.launcher.music.YouTubeMusicLoginScreen
import com.dynamicai.launcher.music.AuthManager
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
    onTestLlm: suspend (String) -> String? = { null }
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = settings.backgroundAlphaPercent / 100f))
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
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White)
            }
            Text("Налаштування", color = Color.White, fontSize = 22.sp)
        }

        val ctx = LocalContext.current
        var showOverlayPermDialog by remember { mutableStateOf(false) }
        var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
        LaunchedEffect(Unit) { installedApps = AppRepository(ctx).loadCached() }

        GlassSectionHeader("Застосунки за замовчуванням")
        GlassCard {
            AppPickerRow("Музика", settings.defaultMusicApp, installedApps, Icons.Default.PlayArrow, Color(0xFF1DB954)) {
                onSettingsChange(settings.copy(defaultMusicApp = it))
            }
            AppPickerRow("Браузер", settings.defaultBrowser, installedApps, Icons.Default.Search, Color(0xFF2196F3)) {
                onSettingsChange(settings.copy(defaultBrowser = it))
            }
            AppPickerRow("Телефон", settings.defaultDialer, installedApps, Icons.Default.Call, Color(0xFF4CAF50)) {
                onSettingsChange(settings.copy(defaultDialer = it))
            }
            AppPickerRow("SMS", settings.defaultSms, installedApps, Icons.Default.Email, Color(0xFF9C27B0)) {
                onSettingsChange(settings.copy(defaultSms = it))
            }
            AppPickerRow("Email", settings.defaultEmail, installedApps, Icons.Default.Email, Color(0xFFF44336)) {
                onSettingsChange(settings.copy(defaultEmail = it))
            }
            AppPickerRow("Карти", settings.defaultMaps, installedApps, Icons.Default.Place, Color(0xFF00BCD4)) {
                onSettingsChange(settings.copy(defaultMaps = it))
            }
            AppPickerRow("Камера", settings.defaultCamera, installedApps, Icons.Default.Star, Color(0xFFFF9800)) {
                onSettingsChange(settings.copy(defaultCamera = it))
            }
        }

        if (showOverlayPermDialog) {
            AlertDialog(
                onDismissRequest = { showOverlayPermDialog = false },
                title = { Text("Дозвіл для голосової активації", color = Color.White) },
                text = {
                    Text(
                        "Щоб показувати вікно підтвердження команд, дозвольте DynamicAI показ поверх інших застосунків.",
                        color = Color.White.copy(alpha = 0.8f)
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
                    }) { Text("Дозволити", color = Color(0xFF64B5F6)) }
                },
                dismissButton = {
                    TextButton(onClick = { showOverlayPermDialog = false }) {
                        Text("Пізніше", color = Color.White.copy(alpha = 0.6f))
                    }
                },
                containerColor = Color(0xFF1A1A1A)
            )
        }

        GlassSectionHeader("Нотатки")
        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput("notes") { detectTapGestures { onOpenNotes() } }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Edit, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text("Керування нотатками", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Default.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
            }
        }

        GlassSectionHeader("Макроси")
        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput("macros") { detectTapGestures { onOpenMacroManager() } }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text("Керування макросами", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Default.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
            }
        }

        // ==== Погода ====
        GlassSectionHeader("Погода")
        GlassCard {
            SwitchRow("Показувати погоду", settings.showWeather, Icons.Default.Star, Color(0xFFFFB300)) {
                onSettingsChange(settings.copy(showWeather = it))
            }
            SwitchRow("Використовувати GPS", settings.weatherUseGps, Icons.Default.Place, Color(0xFF4CAF50)) {
                onSettingsChange(settings.copy(weatherUseGps = it))
            }
            CityInputRow(
                cityName = settings.cityName,
                onCityChange = { onSettingsChange(settings.copy(cityName = it)) }
            )
            UnitRow(
                current = settings.weatherUnit,
                onChange = { onSettingsChange(settings.copy(weatherUnit = it)) }
            )
        }

        // ==== Фон лаунчера ====
        GlassSectionHeader("Фон лаунчера")
        GlassCard {
            SizeSliderRow(
                label = "Затемнення фону",
                value = settings.backgroundAlphaPercent,
                suffix = "%",
                values = listOf(0, 20, 40, 60, 80, 100),
                onChange = { onSettingsChange(settings.copy(backgroundAlphaPercent = it)) }
            )
        }

        // ==== Розміри ====
        GlassSectionHeader("Розміри")
        GlassCard {
            SizeSliderRow(
                label = "Іконки у списку",
                value = settings.iconSizeDp,
                suffix = "dp",
                values = listOf(28, 32, 40, 48, 56, 64),
                onChange = { onSettingsChange(settings.copy(iconSizeDp = it)) }
            )
            SizeSliderRow(
                label = "Іконки в доку",
                value = settings.dockIconSizeDp,
                suffix = "dp",
                values = listOf(28, 34, 40, 46, 52, 58, 64),
                onChange = { onSettingsChange(settings.copy(dockIconSizeDp = it)) }
            )
            SizeSliderRow(
                label = "Іконки в меню (сітка)",
                value = settings.menuIconSizeDp,
                suffix = "dp",
                values = listOf(40, 48, 56, 64, 72, 80),
                onChange = { onSettingsChange(settings.copy(menuIconSizeDp = it)) }
            )
            SizeSliderRow(
                label = "Розмір шрифту",
                value = settings.fontSizeSp,
                suffix = "sp",
                values = listOf(14, 16, 18, 20, 22, 24),
                onChange = { onSettingsChange(settings.copy(fontSizeSp = it)) }
            )
        }

        // ==== Вигляд ====
        GlassSectionHeader("Вигляд")
        GlassCard {
            SwitchRow("Показувати кнопку Recent", settings.showRecentButton, Icons.Default.Menu, Color(0xFF9C27B0)) {
                onSettingsChange(settings.copy(showRecentButton = it))
            }
            SwitchRow("Показувати пошук", settings.showSearchBar, Icons.Default.Search, Color(0xFF2196F3)) {
                onSettingsChange(settings.copy(showSearchBar = it))
            }
            SwitchRow("Показувати назви", settings.showLabels, Icons.Default.Info, Color(0xFF2196F3)) {
                onSettingsChange(settings.copy(showLabels = it))
            }
            ActionRow("Шпалери", Icons.Default.Star, Color(0xFFE91E63), onOpenWallpaper)
            ActionRow("Застосунки у дока", Icons.Default.Home, Color(0xFF9C27B0), onOpenDockEditor)
            ActionRow("Керування папками", Icons.Default.Add, Color(0xFF64B5F6), onOpenFolderManager)
        }

        // ==== Плаваючий док ====
        GlassSectionHeader("Плаваючий док")
        GlassCard {
            if (!overlayPermissionGranted) {
                ActionRow("Дозволити показ поверх інших", Icons.Default.Info, Color(0xFF9C27B0), onRequestOverlayPermission)
            } else {
                ActionRow(
                    if (overlayActive) "Вимкнути плаваючий док" else "Увімкнути плаваючий док",
                    Icons.Default.Info,
                    if (overlayActive) Color(0xFFFF6B6B) else Color(0xFF4CAF50),
                    onToggleOverlay
                )
            }
            SizeSliderRow(
                label = "Іконки в оверлеї",
                value = settings.overlayIconSizeDp,
                suffix = "dp",
                values = listOf(28, 34, 40, 46, 52, 58, 64),
                onChange = { onSettingsChange(settings.copy(overlayIconSizeDp = it)) }
            )
            SizeSliderRow(
                label = "Довжина риски",
                value = settings.overlayHandleLengthDp,
                suffix = "dp",
                values = listOf(60, 100, 140, 180, 220),
                onChange = { onSettingsChange(settings.copy(overlayHandleLengthDp = it)) }
            )
            SizeSliderRow(
                label = "Прозорість риски",
                value = settings.overlayHandleAlphaPercent,
                suffix = "%",
                values = listOf(20, 35, 50, 65, 80, 100),
                onChange = { onSettingsChange(settings.copy(overlayHandleAlphaPercent = it)) }
            )
            SizeSliderRow(
                label = "Відступ від краю",
                value = settings.overlayEdgePaddingDp,
                suffix = "dp",
                values = listOf(0, 4, 8, 14, 20, 28, 40),
                onChange = { onSettingsChange(settings.copy(overlayEdgePaddingDp = it)) }
            )
            SizeSliderRow(
                label = "По вертикалі",
                value = settings.overlayOffsetYPercent,
                suffix = "%",
                values = listOf(10, 25, 40, 50, 60, 75, 90),
                onChange = { onSettingsChange(settings.copy(overlayOffsetYPercent = it)) }
            )
            PositionRow(
                current = settings.overlayPosition,
                onChange = { onSettingsChange(settings.copy(overlayPosition = it)) }
            )
        }

        // ==== Енергозбереження ====
        GlassSectionHeader("Енергозбереження")
        GlassCard {
            SwitchRow("Пауза при вимкненому екрані", settings.wpPauseOnScreenOff, Icons.Default.Star, Color(0xFF607D8B)) {
                onSettingsChange(settings.copy(wpPauseOnScreenOff = it))
            }
            SwitchRow("Пауза при низькому заряді", settings.wpPauseOnLowBattery, Icons.Default.Star, Color(0xFFFFC107)) {
                onSettingsChange(settings.copy(wpPauseOnLowBattery = it))
            }
            FpsRow(
                current = settings.wpFps,
                onChange = { onSettingsChange(settings.copy(wpFps = it)) }
            )
        }

        // ==== Дії ====
        GlassSectionHeader("Shizuku")
        ShizukuTestRow()

        GlassSectionHeader("Дії")
        GlassCard {
            ActionRow("Оновити контакти", Icons.Default.Person, Color(0xFF4CAF50), onRefreshContacts)
            ActionRow("Очистити історію команд", Icons.Default.Delete, Color(0xFFFF6B6B), onClearHistory)
            ActionRow("Показати всі приховані ($hiddenCount)", Icons.Default.Info, Color(0xFF9E9E9E), onClearHidden)
        }

        // ==== LLM ====
        GlassSectionHeader("Дозволи")
        GlassCard {
            val ctx = LocalContext.current
            var hasWriteSettings by remember {
                mutableStateOf(
                    if (android.os.Build.VERSION.SDK_INT >= 23)
                        android.provider.Settings.System.canWrite(ctx)
                    else true
                )
            }
            LaunchedEffect(Unit) {
                while (true) {
                    if (android.os.Build.VERSION.SDK_INT >= 23) {
                        hasWriteSettings = android.provider.Settings.System.canWrite(ctx)
                    }
                    kotlinx.coroutines.delay(1000)
                }
            }
            SwitchRow(
                label = if (hasWriteSettings) "Зміна налаштувань ✓" else "Дозволити зміну (яскравість)",
                checked = hasWriteSettings,
                icon = Icons.Default.Settings,
                tint = if (hasWriteSettings) Color(0xFF4CAF50) else Color(0xFFFFA726),
                onChange = {
                    if (!hasWriteSettings) {
                        try {
                            ctx.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                    android.net.Uri.parse("package:${ctx.packageName}")
                                ).apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        } catch (_: Throwable) {}
                    }
                }
            )
            Text(
                "Потрібно для команд 'яскравіше' / 'темніше'",
                color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        GlassSectionHeader("Bluetooth")
        BluetoothTestRow()

        GlassSectionHeader("Локальна LLM")
        GlassCard {
            val downloadedModels = com.dynamicai.launcher.llm.ModelRepository.getDownloadedModels(ctx)
            val progressState = vm.downloadProgress.value

            com.dynamicai.launcher.llm.ModelRepository.AVAILABLE_MODELS.forEach { model ->
                val isDownloaded = downloadedModels.any { it.id == model.id }
                val isSelected = model.id == vm.currentModelId
                
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(model.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            
                            val statusText = if (isDownloaded) "Завантажено" else "Не завантажено (${model.sizeBytes / 1_000_000} МБ)"
                            Text(statusText, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                            
                            if (progressState is com.dynamicai.launcher.llm.ModelDownloader.Progress.Downloading && !isDownloaded) {
                                Text("Завантаження... ${progressState.percent}%", color = Color(0xFF64B5F6), fontSize = 11.sp)
                            }
                        }
                        
                        if (isDownloaded) {
                            if (isSelected) {
                                Text("✓ Активна", color = Color(0xFF4CAF50), fontSize = 12.sp)
                            } else {
                                TextButton(onClick = { vm.setCurrentModel(model.id) }) {
                                    Text("Обрати", color = Color(0xFF64B5F6), fontSize = 12.sp)
                                }
                            }
                            IconButton(onClick = { vm.deleteModel(model) }) {
                                Icon(androidx.compose.material.icons.Icons.Default.Delete, contentDescription = "Видалити", tint = Color(0xFFEF5350))
                            }
                        } else {
                            if (progressState is com.dynamicai.launcher.llm.ModelDownloader.Progress.Downloading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF64B5F6), strokeWidth = 2.dp)
                            } else {
                                TextButton(onClick = { vm.downloadModel(model) }) {
                                    Text("Завантажити", color = Color(0xFF64B5F6), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    
                    // Блок прогресу та помилок (з'являється тільки для моделі, що завантажується)
                    if (vm.downloadingModelId == model.id) {
                        val prog = vm.downloadProgress.value
                        if (prog is com.dynamicai.launcher.llm.ModelDownloader.Progress.Downloading) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { prog.percent / 100f },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = Color(0xFF64B5F6)
                            )
                            val progressText = if (prog.totalMb > 0) 
                                "${prog.percent}% (${prog.downloadedMb} / ${prog.totalMb} МБ)" 
                            else 
                                "${prog.downloadedMb} МБ завантажено..."
                            Text(progressText, color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        } else if (prog is com.dynamicai.launcher.llm.ModelDownloader.Progress.Error) {
                            Text("Помилка: ${prog.message}", color = Color(0xFFEF5350), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            TextButton(onClick = { vm.clearDownloadError() }) {
                                Text("Сховати", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                    
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(top = 8.dp))
                }
            }

            // Кнопка входу в YouTube Music
            if (!AuthManager.isLoggedIn(ctx)) {
                ActionRow("Увійти в YouTube Music", androidx.compose.material.icons.Icons.Default.PlayArrow, Color(0xFFFF0000)) {
                    // Тут треба відкрити екран входу (можна через діалог)
                }
            } else {
                Text("YouTube Music: авторизовано", color = Color(0xFF4CAF50), fontSize = 12.sp)
            }
            
            // Toggle завжди видно
            SwitchRow(
                label = "Озвучувати відповіді (TTS)",
                checked = settings.useTts,
                icon = Icons.Default.PlayArrow,
                tint = Color(0xFF4CAF50)
            ) {
                onSettingsChange(settings.copy(useTts = it))
            }
            ActionRow(
                "Обрати голос TTS",
                icon = Icons.Default.Settings,
                tint = Color(0xFF9C27B0)
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
                tint = Color(0xFFFFA726)
            ) { on ->
                onSettingsChange(settings.copy(wakeWordEnabled = on))
                val appCtx = ctx.applicationContext
                if (on) {
                    val ok = ContextCompat.checkSelfPermission(appCtx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (ok) {
                        com.dynamicai.launcher.voice.WakeWordService.start(appCtx)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(appCtx)) {
                        showOverlayPermDialog = true
                    }
                } else {
                    com.dynamicai.launcher.voice.WakeWordService.stop(appCtx)
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
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.width(60.dp)
                    )
                    androidx.compose.foundation.text.BasicTextField(
                        value = settings.wakeWord,
                        onValueChange = { onSettingsChange(settings.copy(wakeWord = it)) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF64B5F6)),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
                Text(
                    "⚠️ Потребує постійного слухання — буде додано у наступній версії",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            
            SwitchRow(
                label = "Використовувати LLM для команд",
                checked = settings.useLlm,
                icon = Icons.Default.Settings,
                tint = Color(0xFF9C27B0)
            ) {
                onSettingsChange(settings.copy(useLlm = it))
            }

            if (llmReady) {
                LlmTestRow(onTest = onTestLlm)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "DynamicAI · $versionName · папок: $foldersCount",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(48.dp))
    }
}

// ===== Компоненти =====

@Composable
private fun SizeSliderRow(
    label: String,
    value: Int,
    suffix: String,
    values: List<Int>,
    onChange: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("$value$suffix", color = Color(0xFF64B5F6), fontSize = 13.sp)
        }
        Slider(
            value = values.indexOf(value).coerceAtLeast(0).toFloat(),
            onValueChange = { v ->
                val idx = v.roundToInt().coerceIn(0, values.size - 1)
                onChange(values[idx])
            },
            valueRange = 0f..(values.size - 1).toFloat(),
            steps = (values.size - 2).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF64B5F6),
                activeTrackColor = Color(0xFF64B5F6)
            )
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    tint: Color = Color(0xFF64B5F6),
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(checked) { detectTapGestures { onChange(!checked) } }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint.copy(alpha = 0.18f)),
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF64B5F6)
            )
        )
    }
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.06f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 16.dp)
    )
}

@Composable
private fun ActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    tint: Color = Color(0xFF64B5F6),
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint.copy(alpha = 0.18f)),
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(18.dp)
        )
    }
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.06f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 16.dp)
    )
}

@Composable
private fun CityInputRow(cityName: String, onCityChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Місто (вручну)", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        BasicTextField(
            value = cityName,
            onValueChange = onCityChange,
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
            cursorBrush = SolidColor(Color(0xFF64B5F6)),
            singleLine = true,
            modifier = Modifier
                .width(150.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            decorationBox = { inner ->
                if (cityName.isEmpty()) {
                    Text("напр. Київ", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                }
                inner()
            }
        )
    }
}

@Composable
private fun UnitRow(current: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GlassChip("°C", current == "C") { onChange("C") }
        GlassChip("°F", current == "F") { onChange("F") }
    }
}

@Composable
private fun PositionRow(current: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Сторона:", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        GlassChip("Зліва", current == "left") { onChange("left") }
        GlassChip("Справа", current == "right") { onChange("right") }
    }
}

@Composable
private fun FpsRow(current: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("FPS:", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        listOf(15, 24, 30, 60).forEach { fps ->
            GlassChip("$fps", current == fps) { onChange(fps) }
        }
    }
}

@Composable
private fun GlassChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.verticalGradient(
                    if (selected) listOf(
                        Color(0xFF64B5F6).copy(alpha = 0.6f),
                        Color(0xFF2196F3).copy(alpha = 0.3f)
                    ) else listOf(
                        Color.White.copy(alpha = 0.1f),
                        Color.White.copy(alpha = 0.02f)
                    )
                )
            )
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 13.sp
        )
    }
}




@Composable
private fun ShizukuTestRow() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Перевірка…") }
    var tasks by remember { mutableStateOf("") }
    var permCode by remember { mutableStateOf(0) }

    val manager = remember { com.dynamicai.launcher.system.ShizukuManager(ctx) }

    LaunchedEffect(Unit) {
        status = buildString {
            append("Installed: ").append(manager.isShizukuInstalled()).append("\n")
            append("Binder received: ").append(manager.isBinderReceived()).append("\n")
            append("Ping binder: ").append(manager.isShizukuAvailable()).append("\n")
            append("API version: ").append(manager.getShizukuVersion()).append("\n")
            append("Permission: ").append(manager.hasPermission())
        }
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(status, color = Color.White, fontSize = 13.sp)
        if (tasks.isNotBlank()) {
            Text(
                tasks,
                color = Color(0xFF64B5F6),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 6.dp)
        ) {
            GlassChip("Запит дозволу", false) {
                val msg = manager.requestPermission(permCode)
                permCode++
                tasks = msg
            }
            GlassChip("Завантажити tasks", false) {
                scope.launch {
                    val list = manager.getRecentTasks()
                    tasks = "Знайдено: ${list.size}\n\n" +
                        manager.lastDiagnostic + "\n\n" +
                        list.take(5).joinToString("\n") {
                            "• ${it.label} (${it.packageName})"
                        }
                }
            }
        }
    }
}


@Composable
private fun LlmTestRow(onTest: suspend (String) -> String?) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("Відкрий телеграм") }
    var answer by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    val ctx = LocalContext.current

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Тест LLM", color = Color(0xFF64B5F6), fontSize = 13.sp)

        // Діагностика
        val diag = remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            // Отримуємо через ctx як Activity
            diag.value = "Запит…"
        }

        BasicTextField(
            value = query,
            onValueChange = { query = it },
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("Prompt", color = Color.Gray, fontSize = 14.sp)
                }
                inner()
            }
        )

        GlassChip(if (loading) "Думаю…" else "Виконати", false) {
            if (loading) return@GlassChip
            loading = true
            answer = ""
            scope.launch {
                answer = try {
                    onTest(query) ?: "(порожньо)"
                } catch (t: Throwable) {
                    "Помилка: ${t.message}"
                }
                loading = false
            }
        }

        if (answer.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                answer,
                color = Color(0xFF81C784),
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(8.dp)
            )
        }
    }
}


@Composable
private fun BluetoothTestRow() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { com.dynamicai.launcher.system.BluetoothManager(ctx) }
    var diagnostic by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var hasPermission by remember { mutableStateOf(false) }

    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result.values.all { it }
        diagnostic = manager.getDiagnostic()
    }

    fun checkPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 31) return true
        return androidx.core.content.ContextCompat.checkSelfPermission(
            ctx, android.Manifest.permission.BLUETOOTH_CONNECT
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun requestPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            permLauncher.launch(arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN
            ))
        }
    }

    LaunchedEffect(Unit) {
        hasPermission = checkPermission()
        if (!hasPermission) {
            requestPermissions()
        } else {
            diagnostic = manager.getDiagnostic()
        }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (!hasPermission) {
            Text(
                "❌ Потрібен дозвіл Bluetooth",
                color = Color(0xFFFF6B6B), fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            GlassChip("Дати дозвіл", false) {
                requestPermissions()
            }
        } else {
            Text(
                diagnostic.ifBlank { "Перевірка…" },
                color = Color(0xFF81C784), fontSize = 11.sp
            )

            BasicTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                decorationBox = { inner ->
                    if (nameInput.isEmpty()) {
                        Text("Назва пристрою (напр. Toyota)", color = Color.Gray, fontSize = 14.sp)
                    }
                    inner()
                }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip("Оновити", false) {
                    diagnostic = manager.getDiagnostic()
                }
                GlassChip("Підключити", false) {
                    scope.launch {
                        val ok = manager.connect(nameInput)
                        val prefix = if (ok) "OK підключення" else "Помилка"
                        diagnostic = prefix + "\n\n" + manager.getDiagnostic()
                    }
                }
            }
        }
    }
}


@Composable
private fun TtsSlider(
    label: String,
    value: Float,
    values: List<Float>,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("%.2fx".format(value), color = Color(0xFF64B5F6), fontSize = 12.sp)
        }
        Slider(
            value = values.indexOf(value).coerceAtLeast(0).toFloat(),
            onValueChange = { v ->
                val idx = v.roundToInt().coerceIn(0, values.size - 1)
                onChange(values[idx])
            },
            valueRange = 0f..(values.size - 1).toFloat(),
            steps = (values.size - 2).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF64B5F6),
                activeTrackColor = Color(0xFF64B5F6)
            )
        )
    }

}

@Composable
private fun AppPickerDialog(
    title: String,
    apps: List<AppInfo>,
    currentPkg: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Обрати: $title", color = Color.White) },
        text = {
            Column {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(Color(0xFF64B5F6)),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("Пошук…", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                        }
                        inner()
                    }
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick("") }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Системний (за замовчуванням)",
                                color = if (currentPkg.isBlank()) Color(0xFF64B5F6) else Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                    items(filtered) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(app.packageName) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                app.label,
                                color = if (currentPkg == app.packageName) Color(0xFF64B5F6) else Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (app.isSystem) {
                                Text("сист.", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрити", color = Color(0xFF64B5F6)) }
        },
        containerColor = Color(0xFF1A1A1A),
    )
}

@Composable
private fun AppPickerRow(
    label: String,
    currentPkg: String,
    apps: List<AppInfo>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onPick: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val currentLabel = if (currentPkg.isBlank()) "Системний"
        else apps.find { it.packageName == currentPkg }?.label ?: currentPkg

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(label) { detectTapGestures { showDialog = true } }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tint.copy(alpha = 0.18f)),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 14.sp)
            Text(currentLabel, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(18.dp)
        )
    }
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.06f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 16.dp)
    )

    if (showDialog) {
        AppPickerDialog(
            title = label,
            apps = apps,
            currentPkg = currentPkg,
            onPick = { onPick(it); showDialog = false },
            onDismiss = { showDialog = false }
        )
    }
}