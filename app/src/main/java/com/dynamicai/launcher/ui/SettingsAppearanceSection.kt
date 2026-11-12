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
import android.os.Build
import androidx.compose.foundation.layout.ColumnScope

/** Налаштування → Вигляд: тема, фон, іконки й сітка, елементи екрана, ефекти, жести, анімації, плаваючий док. */
@Composable
internal fun ColumnScope.SettingsAppearanceSection(
    settings: LauncherSettings,
    onSettingsChange: (LauncherSettings) -> Unit,
    overlayActive: Boolean,
    overlayPermissionGranted: Boolean,
    onToggleOverlay: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onOpenWallpaper: () -> Unit,
    onOpenDockEditor: () -> Unit,
    onOpenFolderManager: () -> Unit
) {
    val ctx = LocalContext.current
    val bgScope = rememberCoroutineScope()
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
        GlassSectionHeader("🎨 Зовнішній вигляд")
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
                if (Build.VERSION.SDK_INT >= 31) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChip("Material You (з шпалер)", "glass_dynamic", settings.appTheme) { onSettingsChange(settings.copy(appTheme = it)) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Акцентний колір (A = колір теми)", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                AccentPicker(settings.accentColor) { onSettingsChange(settings.copy(accentColor = it)) }
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

        // ==== Розміри ====
        GlassSectionHeader("Іконки та сітка")
        GlassCard {
            GridStylePreview()
            SwitchRow("Однаковий розмір іконок скрізь", settings.uniformIcons, Icons.Default.Settings, AppTheme.colors.accent) {
                // При вмиканні всі розміри вирівнюються за розміром у сітці
                onSettingsChange(
                    if (it) settings.copy(uniformIcons = true, iconSizeDp = settings.menuIconSizeDp, dockIconSizeDp = settings.menuIconSizeDp)
                    else settings.copy(uniformIcons = false)
                )
            }
            if (settings.uniformIcons) {
                SizeSliderRow(
                    label = "Розмір іконок (список, док, сітка, папки, пошук)",
                    value = settings.menuIconSizeDp,
                    suffix = "dp",
                    values = listOf(28, 32, 36, 40, 44, 48, 52, 56, 64, 72, 80),
                    onChange = { onSettingsChange(settings.copy(iconSizeDp = it, dockIconSizeDp = it, menuIconSizeDp = it)) }
                )
            } else {
                SizeSliderRow(
                    label = "Іконки у списку",
                    value = settings.iconSizeDp,
                    suffix = "dp",
                    values = listOf(28, 32, 36, 40, 44, 48, 52, 56, 64, 72, 80),
                    onChange = { onSettingsChange(settings.copy(iconSizeDp = it)) }
                )
                SizeSliderRow(
                    label = "Іконки в доку",
                    value = settings.dockIconSizeDp,
                    suffix = "dp",
                    values = listOf(28, 32, 36, 40, 44, 48, 52, 56, 64, 72, 80),
                    onChange = { onSettingsChange(settings.copy(dockIconSizeDp = it)) }
                )
                SizeSliderRow(
                    label = "Іконки в сітці (шторка, папки, пошук)",
                    value = settings.menuIconSizeDp,
                    suffix = "dp",
                    values = listOf(28, 32, 36, 40, 44, 48, 52, 56, 64, 72, 80),
                    onChange = { onSettingsChange(settings.copy(menuIconSizeDp = it)) }
                )
            }
            SizeSliderRow(
                label = "Підкладка під іконкою",
                value = settings.iconPlatePadDp,
                suffix = "dp",
                values = listOf(0, 2, 4, 6, 8, 10, 12, 16),
                onChange = { onSettingsChange(settings.copy(iconPlatePadDp = it)) }
            )
            SizeSliderRow(
                label = "Відступ між іконками по горизонталі",
                value = settings.gridGapHDp,
                suffix = "dp",
                values = listOf(0, 2, 4, 8, 12, 16, 24),
                onChange = { onSettingsChange(settings.copy(gridGapHDp = it)) }
            )
            SizeSliderRow(
                label = "Відступ між іконками по вертикалі",
                value = settings.gridGapVDp,
                suffix = "dp",
                values = listOf(0, 4, 8, 12, 16, 20, 24, 32),
                onChange = { onSettingsChange(settings.copy(gridGapVDp = it)) }
            )
            SizeSliderRow(
                label = "Відступ між рядками списку",
                value = settings.listGapDp,
                suffix = "dp",
                values = listOf(0, 2, 4, 6, 8, 12),
                onChange = { onSettingsChange(settings.copy(listGapDp = it)) }
            )
            SizeSliderRow(
                label = "Док: відступ між іконками",
                value = settings.dockGapDp,
                suffix = "dp",
                values = listOf(0, 2, 4, 6, 10, 16, 24),
                onChange = { onSettingsChange(settings.copy(dockGapDp = it)) }
            )
            SizeSliderRow(
                label = "Док: заокруглення панелі",
                value = settings.dockCornerDp,
                suffix = "dp",
                values = listOf(0, 8, 16, 26, 34, 40),
                onChange = { onSettingsChange(settings.copy(dockCornerDp = it)) }
            )
            SizeSliderRow(
                label = "Док: прозорість панелі",
                value = settings.dockOpacityPercent,
                suffix = "%",
                values = listOf(0, 25, 50, 75, 100),
                onChange = { onSettingsChange(settings.copy(dockOpacityPercent = it)) }
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
        GlassSectionHeader("Елементи екрана")
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

        GlassSectionHeader("Ефекти та форма іконок")
        GlassCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                SwitchRow("Жива аврора на фоні (більше споживає батарею)", settings.liveAurora, Icons.Default.Star, AppTheme.colors.accent) {
                    onSettingsChange(settings.copy(liveAurora = it))
                }
                SwitchRow("Підказки на головному екрані", settings.proactiveHints, Icons.Default.Star, AppTheme.colors.accent) {
                    onSettingsChange(settings.copy(proactiveHints = it))
                }
                SwitchRow("Паралакс при гортанні сторінок", settings.pageParallax, Icons.Default.Star, AppTheme.colors.accent) {
                    onSettingsChange(settings.copy(pageParallax = it))
                }
                SwitchRow("Вночі (00–06) лише годинник", settings.nightClockMode, Icons.Default.Settings, AppTheme.colors.purple) {
                    onSettingsChange(settings.copy(nightClockMode = it))
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
        GlassSectionHeader("Жести")
        GlassCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                GestureRow("Подвійний тап по порожньому місцю", settings.gestureDoubleTap) { onSettingsChange(settings.copy(gestureDoubleTap = it)) }
                Spacer(Modifier.height(10.dp))
                GestureRow("Свайп вниз по екрану", settings.gestureSwipeDown) { onSettingsChange(settings.copy(gestureSwipeDown = it)) }
                Spacer(Modifier.height(10.dp))
                GestureRow("Свайп вгору по екрану", settings.gestureSwipeUp) { onSettingsChange(settings.copy(gestureSwipeUp = it)) }
            }
        }
        GlassSectionHeader("Анімації")
        GlassCard {
            SizeSliderRow(
                label = "Швидкість анімацій (шторка, папки, головний екран; 0 = вимкнено)",
                value = settings.animSpeedPercent,
                suffix = "%",
                values = listOf(0, 50, 75, 100, 150, 200),
                onChange = { onSettingsChange(settings.copy(animSpeedPercent = it)) }
            )
        }


        // ==== Плаваючий док ====
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
            Text("Ландшафт (якщо док не ховається)", color = AppTheme.colors.textSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            SizeSliderRow(
                label = "По вертикалі (ландшафт)",
                value = settings.overlayOffsetYPercentLandscape,
                suffix = "%",
                values = listOf(10, 25, 40, 50, 60, 75, 90),
                onChange = { onSettingsChange(settings.copy(overlayOffsetYPercentLandscape = it)) }
            )
            PositionRow(
                current = settings.overlayPositionLandscape,
                onChange = { onSettingsChange(settings.copy(overlayPositionLandscape = it)) }
            )
        }

        // ==== Система ====
}
