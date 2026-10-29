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
    val bgScope = androidx.compose.runtime.rememberCoroutineScope()
    val bgPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            bgScope.launch {
                val savedPath = CustomBackgroundLoader.saveFromUri(ctx, uri)
                if (savedPath != null) {
                    onSettingsChange(settings.copy(backgroundImageUri = savedPath))
                }
            }
        }
    }
            var showOverlayPermDialog by remember { mutableStateOf(false) }
        var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
        var modelFilter by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("all") }
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

                SizeSliderRow(
                    label = "Розмиття фону",
                    value = settings.wallpaperBlurRadius,
                    suffix = " dp",
                    values = listOf(0, 20, 40, 60, 80, 100),
                    onChange = { onSettingsChange(settings.copy(wallpaperBlurRadius = it)) }
                )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppTheme.colors.accent.copy(alpha = 0.2f))
                                .clickable {
                                    bgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (settings.backgroundImageUri.isBlank()) "Обрати фото" else "Змінити фото",
                                color = AppTheme.colors.textPrimary, fontSize = 14.sp
                            )
                        }
                        if (settings.backgroundImageUri.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AppTheme.colors.danger.copy(alpha = 0.2f))
                                    .clickable {
                                        bgScope.launch {
                                            CustomBackgroundLoader.clear(ctx)
                                            onSettingsChange(settings.copy(backgroundImageUri = ""))
                                        }
                                    }
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Видалити", color = AppTheme.colors.textPrimary, fontSize = 14.sp)
                            }
                        }
                    }
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
        var modelToDelete by remember { mutableStateOf<com.dynamicai.launcher.llm.LlmModel?>(null) }
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
        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("LiteRT GPU (експериментально)", color = AppTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = AppTheme.colors.glassBorder)
                Spacer(Modifier.height(8.dp))

                val stateText = when {
                    llmRestarting -> "⏳ " + llmStatus.ifBlank { "Перезапуск…" }
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

        // === MNN Models ===
        if (modelFilter == "all" || modelFilter == "mnn") {
            // Список і розміри беремо з ModelRepository (єдине джерело правди)
            val mnnModels = remember {
                com.dynamicai.launcher.llm.ModelRepository.AVAILABLE_MODELS
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
                        Text("🧪 MNN Models", color = AppTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { vm.clearMnnCache() }) {
                            Text("Очистити кеш", color = AppTheme.colors.danger, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    mnnModels.forEach { (id, name, size) ->
                        val isCurrent = vm.currentModelId == id
                        val isDownloaded = id in downloadedIds
                        val isDownloading = vm.downloadingModelId == id && mnnProgress is com.dynamicai.launcher.llm.ModelDownloader.Progress.Downloading

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
                                    val prog = mnnProgress as com.dynamicai.launcher.llm.ModelDownloader.Progress.Downloading
                                    LinearProgressIndicator(progress = { prog.percent / 100f }, modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 6.dp), color = AppTheme.colors.accent)
                                    Text("${prog.percent}% (${prog.downloadedMb} / ${prog.totalMb} МБ)", color = AppTheme.colors.accent, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                                } else {
                                    Text("$size • Натисніть для завантаження", color = AppTheme.colors.textSecondary, fontSize = 11.sp)
                                }
                            }
                            if (isDownloaded) {
                                IconButton(enabled = !isDownloading, onClick = {
                                    modelToDelete = com.dynamicai.launcher.llm.ModelRepository.AVAILABLE_MODELS.find { it.id == id }
                                }) {
                                    Icon(
                                        androidx.compose.material.icons.Icons.Default.Delete,
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

            val filteredModels = com.dynamicai.launcher.llm.ModelRepository.AVAILABLE_MODELS.filter { m ->
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
                            
                            if (vm.downloadingModelId == model.id && progressState is com.dynamicai.launcher.llm.ModelDownloader.Progress.Downloading && !isDownloaded) {
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
                                Icon(androidx.compose.material.icons.Icons.Default.Delete, contentDescription = "Видалити", tint = AppTheme.colors.danger)
                            }
                        } else {
                            if (vm.downloadingModelId == model.id && progressState is com.dynamicai.launcher.llm.ModelDownloader.Progress.Downloading) {
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
                            Text(progressText, color = com.dynamicai.launcher.ui.theme.AppTheme.colors.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        } else if (prog is com.dynamicai.launcher.llm.ModelDownloader.Progress.Error) {
                            Text("Помилка: ${prog.message}", color = AppTheme.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            TextButton(onClick = { vm.clearDownloadError() }) {
                                Text("Сховати", color = com.dynamicai.launcher.ui.theme.AppTheme.colors.textMuted, fontSize = 11.sp)
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
            
                        GlassSectionHeader("Промпти для рушіїв (файли)")
        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                val localCtx = androidx.compose.ui.platform.LocalContext.current
                var mnnText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.dynamicai.launcher.llm.PromptManager.loadMnn(localCtx)) }
                var litertText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.dynamicai.launcher.llm.PromptManager.loadLitert(localCtx)) }

                Text("MNN Prompt ({{question}})", color = AppTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("Формат Qwen: <|im_start|>user...<|im_end|>", color = AppTheme.colors.textSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 4.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = mnnText,
                    onValueChange = { mnnText = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = AppTheme.colors.textPrimary, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth().height(100.dp).background(AppTheme.colors.textPrimary.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).padding(8.dp)
                )
                
                Spacer(Modifier.height(16.dp))
                
                Text("LiteRT Prompt (для Gemma)", color = AppTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("Формат Gemma: <start_of_turn>user...<end_of_turn>", color = AppTheme.colors.textSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 4.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = litertText,
                    onValueChange = { litertText = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = AppTheme.colors.textPrimary, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth().height(100.dp).background(AppTheme.colors.textPrimary.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).padding(8.dp)
                )
                
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = {
                        com.dynamicai.launcher.llm.PromptManager.saveMnn(localCtx, mnnText)
                        com.dynamicai.launcher.llm.PromptManager.saveLitert(localCtx, litertText)
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("💾 Записати у файли", color = AppTheme.colors.accent)
                }
            }
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

@Composable
private fun RowScope.LlmBackendChip(label: String, backendId: String, current: String, onSelect: (String) -> Unit) {
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
private fun formatModelSize(bytes: Long): String =
    if (bytes >= 1_000_000_000L) "%.1f ГБ".format(bytes / 1_000_000_000.0) else "${bytes / 1_000_000} МБ"
