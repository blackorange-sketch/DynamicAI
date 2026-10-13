package com.dynamicai.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.SwipeUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ChevronRight
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

@Composable
fun SettingsScreen(
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
    llmDownloaded: Boolean,
    llmReady: Boolean,
    versionName: String,
    onClose: () -> Unit
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

        // ==== Погода ====
        GlassSectionHeader("Погода")
        GlassCard {
            SwitchRow("Показувати погоду", settings.showWeather, Icons.Default.WbSunny, Color(0xFFFFB300)) {
                onSettingsChange(settings.copy(showWeather = it))
            }
            SwitchRow("Використовувати GPS", settings.weatherUseGps, Icons.Default.LocationOn, Color(0xFF4CAF50)) {
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
            SwitchRow("Показувати кнопку Recent", settings.showRecentButton, Icons.Default.ViewCarousel, Color(0xFF9C27B0)) {
                onSettingsChange(settings.copy(showRecentButton = it))
            }
            SwitchRow("Показувати пошук", settings.showSearchBar, Icons.Default.Search, Color(0xFF2196F3)) {
                onSettingsChange(settings.copy(showSearchBar = it))
            }
            SwitchRow("Показувати назви", settings.showLabels, Icons.Default.Visibility, Color(0xFF2196F3)) {
                onSettingsChange(settings.copy(showLabels = it))
            }
            ActionRow("Шпалери", Icons.Default.Palette, Color(0xFFE91E63), onOpenWallpaper)
            ActionRow("Застосунки у дока", Icons.Default.Dashboard, Color(0xFF9C27B0), onOpenDockEditor)
        }

        // ==== Плаваючий док ====
        GlassSectionHeader("Плаваючий док")
        GlassCard {
            if (!overlayPermissionGranted) {
                ActionRow("Дозволити показ поверх інших", Icons.Default.Layers, Color(0xFF9C27B0), onRequestOverlayPermission)
            } else {
                ActionRow(
                    if (overlayActive) "Вимкнути плаваючий док" else "Увімкнути плаваючий док",
                    Icons.Default.Layers,
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
            SwitchRow("Пауза при вимкненому екрані", settings.wpPauseOnScreenOff, Icons.Default.Brightness6, Color(0xFF607D8B)) {
                onSettingsChange(settings.copy(wpPauseOnScreenOff = it))
            }
            SwitchRow("Пауза при низькому заряді", settings.wpPauseOnLowBattery, Icons.Default.Bolt, Color(0xFFFFC107)) {
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
            ActionRow("Оновити контакти", Icons.Default.People, Color(0xFF4CAF50), onRefreshContacts)
            ActionRow("Очистити історію команд", Icons.Default.Delete, Color(0xFFFF6B6B), onClearHistory)
            ActionRow("Показати всі приховані ($hiddenCount)", Icons.Default.Visibility, Color(0xFF9E9E9E), onClearHidden)
        }

        // ==== LLM ====
        GlassSectionHeader("Локальна LLM")
        GlassCard {
            if (!llmDownloaded) {
                ActionRow("Завантажити Gemma 4 E2B (~2.6 ГБ)", Icons.Default.Download, Color(0xFF64B5F6), onDownloadLlm)
            } else if (!llmReady) {
                Text(
                    "Модель завантажена. Рушій очікує на активацію",
                    color = Color.Gray, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            } else {
                Text(
                    "LLM активна ✓",
                    color = Color(0xFF4CAF50), fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                LlmTestRow()
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
            Icons.Default.ChevronRight,
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
private fun LlmTestRow() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("Відкрий телеграм") }
    var answer by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Тест LLM", color = Color(0xFF64B5F6), fontSize = 13.sp)

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
                // Отримуємо ViewModel з Activity
                val activity = ctx as? android.app.Activity
                val vm = androidx.lifecycle.ViewModelProvider(
                    activity ?: return@launch
                )[com.dynamicai.launcher.AppListViewModel::class.java]

                val result = vm.testLlm(query)
                answer = result ?: "(порожньо)"
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
