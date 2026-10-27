package com.dynamicai.launcher.ui

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
            IconButton(onClick = {
                if (initialCategory != null) onBackToMenu() else onClose()
            }) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = AppTheme.colors.textPrimary)
            }
            Text("Налаштування", color = AppTheme.colors.textPrimary, fontSize = 22.sp)
        }

        val ctx = LocalContext.current
            var showOverlayPermDialog by remember { mutableStateOf(false) }
        var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
        LaunchedEffect(Unit) { installedApps = AppRepository(ctx).loadCached() }

        if (initialCategory == null || initialCategory == "tools") {
        GlassSectionHeader("Застосунки за замовчуванням")
        GlassCard {
            AppPickerRow("Музика", settings.defaultMusicApp, installedApps, Icons.Default.PlayArrow, Color(0xFF1DB954)) {
                onSettingsChange(settings.copy(defaultMusicApp = it))
            }
            AppPickerRow("Браузер", settings.defaultBrowser, installedApps, Icons.Default.Search, AppTheme.colors.accentDark) {
                onSettingsChange(settings.copy(defaultBrowser = it))
            }
            AppPickerRow("Телефон", settings.defaultDialer, installedApps, Icons.Default.Call, AppTheme.colors.success) {
                onSettingsChange(settings.copy(defaultDialer = it))
            }
            AppPickerRow("SMS", settings.defaultSms, installedApps, Icons.Default.Email, AppTheme.colors.purple) {
                onSettingsChange(settings.copy(defaultSms = it))
            }
            AppPickerRow("Email", settings.defaultEmail, installedApps, Icons.Default.Email, Color(0xFFF44336)) {
                onSettingsChange(settings.copy(defaultEmail = it))
            }
            AppPickerRow("Карти", settings.defaultMaps, installedApps, Icons.Default.Place, Color(0xFF00BCD4)) {
                onSettingsChange(settings.copy(defaultMaps = it))
            }
            AppPickerRow("Камера", settings.defaultCamera, installedApps, Icons.Default.Star, AppTheme.colors.warning) {
                onSettingsChange(settings.copy(defaultCamera = it))
            }
        }
        }

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

        if (initialCategory == null || initialCategory == "tools") {
        GlassSectionHeader("Нотатки")
        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput("notes") { detectTapGestures { onOpenNotes() } }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Edit, null, tint = AppTheme.colors.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text("Керування нотатками", color = AppTheme.colors.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Default.KeyboardArrowRight, null, tint = AppTheme.colors.glassBorder, modifier = Modifier.size(18.dp))
            }
        }
        }

        if (initialCategory == null || initialCategory == "tools") {
        GlassSectionHeader("Макроси")
        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput("macros") { detectTapGestures { onOpenMacroManager() } }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = AppTheme.colors.warning, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text("Керування макросами", color = AppTheme.colors.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Default.KeyboardArrowRight, null, tint = AppTheme.colors.glassBorder, modifier = Modifier.size(18.dp))
            }
        }
        }

        // ==== Погода ====
        if (initialCategory == null || initialCategory == "tools") {
        GlassSectionHeader("🧩 Віджети та Інструменти")
        if (initialCategory == null || initialCategory == "tools") {
        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { val intent = Intent(ctx, ModuleManagerActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Build, null, tint = AppTheme.colors.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text("Менеджер модулів", color = AppTheme.colors.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Default.KeyboardArrowRight, null, tint = AppTheme.colors.glassBorder, modifier = Modifier.size(18.dp))
            }
        }

        GlassSectionHeader("Погода")
        GlassCard {
            SwitchRow("Показувати погоду", settings.showWeather, Icons.Default.Star, AppTheme.colors.warning) {
                onSettingsChange(settings.copy(showWeather = it))
            }
            SwitchRow("Використовувати GPS", settings.weatherUseGps, Icons.Default.Place, AppTheme.colors.success) {
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
        }
        }

        // ==== Фон лаунчера ====
        if (initialCategory == null || initialCategory == "appearance") {
        GlassSectionHeader("🎨 Зовнішній вигляд")
        if (initialCategory == null || initialCategory == "appearance") {
        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Стиль інтерфейсу", color = AppTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChip("Темна", "glass_dark", settings.appTheme) { onSettingsChange(settings.copy(appTheme = it)) }
                    ThemeChip("Світла", "glass_light", settings.appTheme) { onSettingsChange(settings.copy(appTheme = it)) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChip("AMOLED", "glass_amoled", settings.appTheme) { onSettingsChange(settings.copy(appTheme = it)) }
                    ThemeChip("Океан", "glass_ocean", settings.appTheme) { onSettingsChange(settings.copy(appTheme = it)) }
                }
            }
        }
        
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
        }
        }

        // ==== Розміри ====
        if (initialCategory == null || initialCategory == "appearance") {
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
        }

        // ==== Вигляд ====
        if (initialCategory == null || initialCategory == "appearance") {
        GlassSectionHeader("Вигляд")
        GlassCard {
            SwitchRow("Показувати кнопку Recent", settings.showRecentButton, Icons.Default.Menu, AppTheme.colors.purple) {
                onSettingsChange(settings.copy(showRecentButton = it))
            }
            SwitchRow("Показувати пошук", settings.showSearchBar, Icons.Default.Search, AppTheme.colors.accentDark) {
                onSettingsChange(settings.copy(showSearchBar = it))
            }
            SwitchRow("Показувати назви", settings.showLabels, Icons.Default.Info, AppTheme.colors.accentDark) {
                onSettingsChange(settings.copy(showLabels = it))
            }
            ActionRow("Шпалери", Icons.Default.Star, AppTheme.colors.pink, onOpenWallpaper)
            ActionRow("Застосунки у дока", Icons.Default.Home, AppTheme.colors.purple, onOpenDockEditor)
            ActionRow("Керування папками", Icons.Default.Add, AppTheme.colors.accent, onOpenFolderManager)
        }
        }

        // ==== Плаваючий док ====
        if (initialCategory == null || initialCategory == "appearance") {
        GlassSectionHeader("Плаваючий док")
        GlassCard {
            if (!overlayPermissionGranted) {
                ActionRow("Дозволити показ поверх інших", Icons.Default.Info, AppTheme.colors.purple, onRequestOverlayPermission)
            } else {
                ActionRow(
                    if (overlayActive) "Вимкнути плаваючий док" else "Увімкнути плаваючий док",
                    Icons.Default.Info,
                    if (overlayActive) AppTheme.colors.danger else AppTheme.colors.success,
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
        }

        // ==== Енергозбереження ====
        if (initialCategory == null || initialCategory == "system") {
        GlassSectionHeader("Енергозбереження")
        GlassCard {
            SwitchRow("Пауза при вимкненому екрані", settings.wpPauseOnScreenOff, Icons.Default.Star, AppTheme.colors.textMuted) {
                onSettingsChange(settings.copy(wpPauseOnScreenOff = it))
            }
            SwitchRow("Пауза при низькому заряді", settings.wpPauseOnLowBattery, Icons.Default.Star, AppTheme.colors.warning) {
                onSettingsChange(settings.copy(wpPauseOnLowBattery = it))
            }
            FpsRow(
                current = settings.wpFps,
                onChange = { onSettingsChange(settings.copy(wpFps = it)) }
            )
        }
        }

        // ==== Дії ====
        if (initialCategory == null || initialCategory == "system") {
        GlassSectionHeader("⚙️ Система")
        if (initialCategory == null || initialCategory == "system") {
        GlassSectionHeader("Shizuku")
        ShizukuTestRow()

        if (initialCategory == null || initialCategory == "system") {
        GlassSectionHeader("Дії")
        GlassCard {
            ActionRow("Оновити контакти", Icons.Default.Person, AppTheme.colors.success, onRefreshContacts)
            ActionRow("Очистити історію команд", Icons.Default.Delete, AppTheme.colors.danger, onClearHistory)
            ActionRow("Показати всі приховані ($hiddenCount)", Icons.Default.Info, AppTheme.colors.textMuted, onClearHidden)
        }
        }
        }
        }

        // ==== LLM ====
        if (initialCategory == null || initialCategory == "system") {
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
                tint = if (hasWriteSettings) AppTheme.colors.success else AppTheme.colors.warning,
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
                color = AppTheme.colors.glassBorder, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        }

        if (initialCategory == null || initialCategory == "system") {
        GlassSectionHeader("Bluetooth")
        BluetoothTestRow()

        if (initialCategory == null || initialCategory == "ai") {
        GlassSectionHeader("🤖 ШІ та Асистент")
                if (initialCategory == null || initialCategory == "ai") {
                GlassSectionHeader("🫧 Плаваючий асистент")
        GlassCard {
            val prefs = remember { ctx.getSharedPreferences("floating_orb", android.content.Context.MODE_PRIVATE) }
            var orbEnabled by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Плаваюча куля JARVIS", color = AppTheme.colors.textPrimary, fontSize = 14.sp)
                    Text(
                        if (orbEnabled) "Активна — куля на екрані" else "Вимкнена",
                        color = AppTheme.colors.textSecondary, fontSize = 11.sp
                    )
                }
                androidx.compose.material3.Switch(
                    checked = orbEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (!Settings.canDrawOverlays(ctx)) {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                                ctx.startActivity(intent)
                                return@Switch
                            }
                            val intent = Intent(ctx, com.dynamicai.launcher.overlay.FloatingOrbService::class.java)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                ctx.startForegroundService(intent)
                            } else {
                                ctx.startService(intent)
                            }
                            orbEnabled = true
                            prefs.edit().putBoolean("enabled", true).apply()
                        } else {
                            val intent = Intent(ctx, com.dynamicai.launcher.overlay.FloatingOrbService::class.java)
                            ctx.stopService(intent)
                            orbEnabled = false
                            prefs.edit().putBoolean("enabled", false).apply()
                        }
                    },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = AppTheme.colors.success
                    )
                )
            }
        }
                }
        }
        }

                if (initialCategory == null || initialCategory == "ai") {
                GlassSectionHeader("🌅 Ранковий брифінг")
        GlassCard {
            val wakeDetector = remember { WakeUpDetector(ctx) }
            var morningEnabled by remember { mutableStateOf(wakeDetector.isEnabled()) }
            val avgHour = remember { wakeDetector.getAverageWakeHour() }
            val avgMinute = remember { wakeDetector.getAverageWakeMinute() }
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = AppTheme.colors.success
                    )
                )
            }
            
            ActionRow(
                "Тестувати брифінг зараз",
                androidx.compose.material.icons.Icons.Default.PlayArrow,
                AppTheme.colors.warning
            ) {
                MorningBriefingService.start(ctx)
            }
        }
                }

        if (initialCategory == null || initialCategory == "ai") {
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
                            Text(model.name, color = AppTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            
                            val statusText = if (isDownloaded) "Завантажено" else "Не завантажено (${model.sizeBytes / 1_000_000} МБ)"
                            Text(statusText, color = AppTheme.colors.textSecondary, fontSize = 11.sp)
                            
                            if (progressState is com.dynamicai.launcher.llm.ModelDownloader.Progress.Downloading && !isDownloaded) {
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
                            IconButton(onClick = { vm.deleteModel(model) }) {
                                Icon(androidx.compose.material.icons.Icons.Default.Delete, contentDescription = "Видалити", tint = AppTheme.colors.danger)
                            }
                        } else {
                            if (progressState is com.dynamicai.launcher.llm.ModelDownloader.Progress.Downloading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AppTheme.colors.accent, strokeWidth = 2.dp)
                            } else {
                                TextButton(onClick = { vm.downloadModel(model) }) {
                                    Text("Завантажити", color = AppTheme.colors.accent, fontSize = 12.sp)
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
                                color = AppTheme.colors.accent
                            )
                            val progressText = if (prog.totalMb > 0) 
                                "${prog.percent}% (${prog.downloadedMb} / ${prog.totalMb} МБ)" 
                            else 
                                "${prog.downloadedMb} МБ завантажено..."
                            Text(progressText, color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        } else if (prog is com.dynamicai.launcher.llm.ModelDownloader.Progress.Error) {
                            Text("Помилка: ${prog.message}", color = AppTheme.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            TextButton(onClick = { vm.clearDownloadError() }) {
                                Text("Сховати", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                    
                    HorizontalDivider(color = AppTheme.colors.textPrimary.copy(alpha = 0.1f), modifier = Modifier.padding(top = 8.dp))
                }
            }
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
                        color = AppTheme.colors.textPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.width(60.dp)
                    )
                    androidx.compose.foundation.text.BasicTextField(
                        value = settings.wakeWord,
                        onValueChange = { onSettingsChange(settings.copy(wakeWord = it)) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = AppTheme.colors.textPrimary, fontSize = 14.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.accent),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppTheme.colors.textPrimary.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
                Text(
                    "⚠️ Потребує постійного слухання — буде додано у наступній версії",
                    color = AppTheme.colors.textSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            
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
        }

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
private fun RowScope.ThemeChip(label: String, themeId: String, currentTheme: String, onSelect: (String) -> Unit) {
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
