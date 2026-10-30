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

        if (initialCategory == null || cat == "tools") {
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

        if (initialCategory == null || cat == "tools") {
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

        if (initialCategory == null || cat == "tools") {
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
        if (initialCategory == null || cat == "tools") {
        GlassSectionHeader("🧩 Віджети та Інструменти")
        if (initialCategory == null || cat == "tools") {
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
        if (initialCategory == null || cat == "appearance") {
        GlassSectionHeader("🎨 Зовнішній вигляд")
        if (initialCategory == null || cat == "appearance") {
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
                // Material You: кольори з шпалер (Android 12+)
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChip("Material You (з шпалер)", "glass_dynamic", settings.appTheme) { onSettingsChange(settings.copy(appTheme = it)) }
                    }
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
        if (initialCategory == null || cat == "appearance") {
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
        if (initialCategory == null || cat == "appearance") {
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
        if (initialCategory == null || cat == "appearance") {
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
            SwitchRow("Ховати док при відкритій клавіатурі (потрібна служба доступності)", settings.overlayHideOnKeyboard, Icons.Default.Settings, AppTheme.colors.accentDark) {
                onSettingsChange(settings.copy(overlayHideOnKeyboard = it))
            }
            SwitchRow("Ховати док у ландшафті (відео, ігри)", settings.overlayHideInLandscape, Icons.Default.Settings, AppTheme.colors.accentDark) {
                onSettingsChange(settings.copy(overlayHideInLandscape = it))
            }
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
        if (initialCategory == null || cat == "system") {
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
        if (initialCategory == null || cat == "system") {
        GlassSectionHeader("⚙️ Система")
        if (initialCategory == null || cat == "system") {
        GlassSectionHeader("Shizuku")
        ShizukuTestRow()

        if (initialCategory == null || cat == "system") {
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
        if (initialCategory == null || cat == "system") {
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

        if (initialCategory == null || cat == "system") {
        GlassSectionHeader("Bluetooth")
        BluetoothTestRow()
        GlassSectionHeader("🎨 Вигляд лаунчера")
        GlassCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                SwitchRow("Жива аврора на фоні (більше споживає батарею)", settings.liveAurora, Icons.Default.Star, AppTheme.colors.accent) {
                    onSettingsChange(settings.copy(liveAurora = it))
                }
                SwitchRow("Режим однієї руки (вміст нижче)", settings.oneHandMode, Icons.Default.Settings, AppTheme.colors.success) {
                    onSettingsChange(settings.copy(oneHandMode = it))
                }
                Spacer(Modifier.height(8.dp))
                Text("Форма іконок додатків", color = AppTheme.colors.textPrimary, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Оригінал" to "original", "Коло" to "circle", "Сквірк" to "squircle", "Квадрат" to "square").forEach { (label, id) ->
                        LlmBackendChip(label, id, settings.iconShape) { onSettingsChange(settings.copy(iconShape = it)) }
                    }
                }
            }
        }
        GlassSectionHeader("💾 Резервна копія")
        GlassCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                BackupSettingsBlock()
            }
        }
        GlassSectionHeader("👆 Жести")
        GlassCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                GestureRow("Подвійний тап по порожньому місцю", settings.gestureDoubleTap) { onSettingsChange(settings.copy(gestureDoubleTap = it)) }
                Spacer(Modifier.height(10.dp))
                GestureRow("Свайп вниз по екрану", settings.gestureSwipeDown) { onSettingsChange(settings.copy(gestureSwipeDown = it)) }
            }
        }
        GlassSectionHeader("🧹 Робот-пилосос")
        GlassCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                VacuumSettingsBlock(settings, onSettingsChange)
            }
        }
        GlassSectionHeader("🚗 Авто")
        GlassCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                CarModeSettingsBlock(settings, onSettingsChange)
            }
        }


        if (showAssistant) {
        GlassSectionHeader("🤖 ШІ та Асистент")
                if (showAssistant) {
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

                if (showAssistant) {
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

        if (initialCategory == null || cat == "ai") {
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
        LaunchedEffect(Unit) {
            while (true) { vm.checkLlmReady(); kotlinx.coroutines.delay(3000) }
        }
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
                    label = "Режим агента в голосовому чаті (довгий промпт)",
                    checked = settings.useAgent,
                    icon = Icons.Default.Settings,
                    tint = AppTheme.colors.purple
                ) { onSettingsChange(settings.copy(useAgent = it)) }
                SwitchRow(
                    label = "Окремий кеш для кожної комбінації",
                    checked = settings.litertSeparateCache,
                    icon = Icons.Default.Settings,
                    tint = AppTheme.colors.accent
                ) { onSettingsChange(settings.copy(litertSeparateCache = it)) }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = AppTheme.colors.glassBorder)
                Spacer(Modifier.height(8.dp))

                Text("Вивантажувати модель з пам'яті після простою", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("3 хв" to 3, "15 хв" to 15, "60 хв" to 60, "Ніколи" to 0).forEach { (label, min) ->
                        LlmBackendChip(label, min.toString(), settings.llmUnloadMinutes.toString()) {
                            onSettingsChange(settings.copy(llmUnloadMinutes = it.toInt()))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Макс. довжина відповіді (токени, застосується після перезапуску рушія)", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(128, 256, 512, 1024).forEach { n ->
                        LlmBackendChip(n.toString(), n.toString(), settings.maxResponseTokens.toString()) {
                            onSettingsChange(settings.copy(maxResponseTokens = it.toInt()))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                VoskSettingsBlock(settings, onSettingsChange)
                Spacer(Modifier.height(10.dp))
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
}  // LiteRT GlassCard
            }  // if (showModels)
            if (showVoice) {
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
                val localCtx = androidx.compose.ui.platform.LocalContext.current
                var mnnText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.dynamicai.launcher.llm.PromptManager.loadMnn(localCtx)) }
                var litertText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.dynamicai.launcher.llm.PromptManager.loadLitert(localCtx)) }

                Text("MNN Prompt ({{question}})", color = AppTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("Шаблон чату (<|im_start|>…) MNN додає сам. Тут — інструкція й {{question}}", color = AppTheme.colors.textSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 4.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = mnnText,
                    onValueChange = { mnnText = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = AppTheme.colors.textPrimary, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth().height(100.dp).background(AppTheme.colors.textPrimary.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).padding(8.dp)
                )
                
                Spacer(Modifier.height(16.dp))
                
                Text("LiteRT Prompt (Gemma та інші)", color = AppTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("Шаблон чату (<start_of_turn>…) LiteRT додає сам. Тут — інструкція й {{question}}", color = AppTheme.colors.textSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 4.dp))
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


/** Розпізнавання голосу: Google (онлайн) або Vosk (офлайн, українська). Модель завантажується тут же. */
@Composable
private fun VoskSettingsBlock(settings: LauncherSettings, onSettingsChange: (LauncherSettings) -> Unit) {
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
private fun CarModeSettingsBlock(settings: LauncherSettings, onSettingsChange: (LauncherSettings) -> Unit) {
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
private fun VacuumSettingsBlock(settings: LauncherSettings, onSettingsChange: (LauncherSettings) -> Unit) {
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
private fun GestureRow(title: String, value: String, onPick: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var showMacros by remember { mutableStateOf(false) }
    val macroName = remember(value) {
        if (value.startsWith("macro:")) com.dynamicai.launcher.macro.MacroStorage(ctx).get().firstOrNull { it.id == value.removePrefix("macro:") }?.name else null
    }
    Text(title, color = AppTheme.colors.textPrimary, fontSize = 14.sp)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val current = if (value.startsWith("macro:")) "macro" else value
        listOf("Нічого" to "none", "Шторка" to "notifications", "Голос" to "voice", "Блок." to "lock", "Макрос" to "macro").forEach { (label, id) ->
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
private fun BackupSettingsBlock() {
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
