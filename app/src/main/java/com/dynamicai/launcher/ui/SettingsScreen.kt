package com.dynamicai.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.data.LauncherSettings
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    onSettingsChange: (LauncherSettings) -> Unit,
    hiddenCount: Int,
    onClearHistory: () -> Unit,
    onRefreshContacts: () -> Unit,
    onClearHidden: () -> Unit,
    onDownloadLlm: () -> Unit,
    overlayActive: Boolean,
    overlayPermissionGranted: Boolean,
    onToggleOverlay: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    llmDownloaded: Boolean,
    llmReady: Boolean,
    versionName: String,
    onOpenWallpaper: () -> Unit,
    onOpenDockEditor: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = settings.backgroundAlphaPercent / 100f))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White)
            }
            Text("Налаштування", color = Color.White, fontSize = 22.sp)
        }

        Section("Розмір іконок")
        SizeSlider(
            label = "Іконки у списку",
            value = settings.iconSizeDp,
            values = listOf(28, 32, 40, 48, 56, 64),
            onChange = { onSettingsChange(settings.copy(iconSizeDp = it)) }
        )
        SizeSlider(
            label = "Іконки у головному дока",
            value = settings.dockIconSizeDp,
            values = listOf(28, 34, 40, 46, 52, 58, 64),
            onChange = { onSettingsChange(settings.copy(dockIconSizeDp = it)) }
        )
        SizeSlider(
            label = "Розмір шрифту",
            value = settings.fontSizeSp,
            values = listOf(14, 16, 18, 20, 22, 24),
            onChange = { onSettingsChange(settings.copy(fontSizeSp = it)) }
        )

        Section("Погода")
        SwitchRow(
            label = "Показувати погоду",
            checked = settings.showWeather,
            onChange = { onSettingsChange(settings.copy(showWeather = it)) }
        )
        SwitchRow(
            label = "Використовувати GPS",
            checked = settings.weatherUseGps,
            onChange = { onSettingsChange(settings.copy(weatherUseGps = it)) }
        )

        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onSettingsChange(settings.copy(weatherUnit = "C")) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (settings.weatherUnit == "C") Color(0xFF2196F3) else Color(0xFF2A2A2A)
                )
            ) { Text("°C", color = Color.White) }
            Button(
                onClick = { onSettingsChange(settings.copy(weatherUnit = "F")) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (settings.weatherUnit == "F") Color(0xFF2196F3) else Color(0xFF2A2A2A)
                )
            ) { Text("°F", color = Color.White) }
        }

        if (!settings.weatherUseGps) {
            Text(
                "Координати (широта, довгота):",
                color = Color.Gray, fontSize = 12.sp,
                modifier = Modifier.padding(start = 24.dp, top = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "%.2f".format(settings.weatherLat),
                    color = Color.White, fontSize = 15.sp,
                    modifier = Modifier
                        .background(Color(0xFF1A1A1A), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .pointerInput("lat") {
                            androidx.compose.foundation.gestures.detectTapGestures {
                                // мінус 1
                                onSettingsChange(settings.copy(weatherLat = settings.weatherLat - 1f))
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Text(
                    "%.2f".format(settings.weatherLon),
                    color = Color.White, fontSize = 15.sp,
                    modifier = Modifier
                        .background(Color(0xFF1A1A1A), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .pointerInput("lon") {
                            androidx.compose.foundation.gestures.detectTapGestures {
                                onSettingsChange(settings.copy(weatherLon = settings.weatherLon + 1f))
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            Text(
                "Тап по числу — змінити (lat -1, lon +1)",
                color = Color.DarkGray, fontSize = 11.sp,
                modifier = Modifier.padding(start = 24.dp)
            )
        }

        Section("Фон лаунчера")
        SizeSlider(
            label = "Затемнення фону (%)",
            value = settings.backgroundAlphaPercent,
            values = listOf(0, 20, 40, 60, 80, 100),
            onChange = { onSettingsChange(settings.copy(backgroundAlphaPercent = it)) }
        )
        Text(
            if (settings.backgroundAlphaPercent == 0) "Повністю прозорий — видно шпалери"
            else if (settings.backgroundAlphaPercent < 50) "Напівпрозорий — легка підкладка"
            else "Затемнений — краща читабельність",
            color = Color.Gray, fontSize = 12.sp,
            modifier = Modifier.padding(start = 24.dp, top = 4.dp)
        )

        Section("Вигляд")
        ActionRow("Шпалери", onOpenWallpaper)
        ActionRow("Застосунки у дока", onOpenDockEditor)
        SwitchRow(
            label = "Показувати назви застосунків",
            checked = settings.showLabels,
            onChange = { onSettingsChange(settings.copy(showLabels = it)) }
        )

        Section("Діагностика")
        Text(
            "Папок: ${com.dynamicai.launcher.folder.FolderStorage(LocalContext.current).get().size}",
            color = Color.Yellow, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Text(
            "Налаштування: iconSize=${settings.iconSizeDp} dock=${settings.dockIconSizeDp}",
            color = Color.Yellow, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Section("Дії")
        ActionRow("Оновити контакти", onRefreshContacts)
        ActionRow("Очистити історію команд", onClearHistory)
        ActionRow(
            "Показати всі приховані (${hiddenCount})",
            onClearHidden
        )

        Section("Плаваючий док")
        val ctxLocal = LocalContext.current
        Text(
            "perm=$overlayPermissionGranted active=$overlayActive\nLog: ${com.dynamicai.launcher.overlay.FloatingDockService.lastLog}",
            color = Color.Yellow, fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        if (!overlayPermissionGranted) {
            Button(
                onClick = {
                    Toast.makeText(ctxLocal, "Запит дозволу…", Toast.LENGTH_SHORT).show()
                    onRequestOverlayPermission()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)
            ) { Text("Дозволити показ поверх інших застосунків", color = Color.White) }
        } else {
            Button(
                onClick = {
                    Toast.makeText(ctxLocal, "Toggle: active=$overlayActive", Toast.LENGTH_SHORT).show()
                    onToggleOverlay()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)
            ) { Text(if (overlayActive) "Вимкнути плаваючий док" else "Увімкнути плаваючий док", color = Color.White) }
        }

        Section("Розмір у плаваючому дока")
        SizeSlider(
            label = "Іконки",
            value = settings.overlayIconSizeDp,
            values = listOf(28, 34, 40, 46, 52, 58, 64),
            onChange = { onSettingsChange(settings.copy(overlayIconSizeDp = it)) }
        )
        SizeSlider(
            label = "Довжина риски",
            value = settings.overlayHandleLengthDp,
            values = listOf(60, 100, 140, 180, 220),
            onChange = { onSettingsChange(settings.copy(overlayHandleLengthDp = it)) }
        )
        SizeSlider(
            label = "Прозорість риски (%)",
            value = settings.overlayHandleAlphaPercent,
            values = listOf(20, 35, 50, 65, 80, 100),
            onChange = { onSettingsChange(settings.copy(overlayHandleAlphaPercent = it)) }
        )

        Section("Позиція")
        SizeSlider(
            label = "По вертикалі (%)",
            value = settings.overlayOffsetYPercent,
            values = listOf(10, 25, 40, 50, 60, 75, 90),
            onChange = { onSettingsChange(settings.copy(overlayOffsetYPercent = it)) }
        )

        SizeSlider(
            label = "Відступ від краю екрана (dp)",
            value = settings.overlayEdgePaddingDp,
            values = listOf(0, 4, 8, 14, 20, 28, 40),
            onChange = { onSettingsChange(settings.copy(overlayEdgePaddingDp = it)) }
        )

        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onSettingsChange(settings.copy(overlayPosition = "left")) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (settings.overlayPosition == "left")
                        Color(0xFF2196F3) else Color(0xFF2A2A2A)
                )
            ) { Text("Зліва", color = Color.White) }
            Button(
                onClick = { onSettingsChange(settings.copy(overlayPosition = "right")) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (settings.overlayPosition == "right")
                        Color(0xFF2196F3) else Color(0xFF2A2A2A)
                )
            ) { Text("Справа", color = Color.White) }
        }

        Text(
            "Зміни застосовуються одразу",
            color = Color.Gray, fontSize = 12.sp,
            modifier = Modifier.padding(start = 24.dp, top = 4.dp)
        )

        Section("Локальна LLM")
        when {
            !llmDownloaded -> ActionRow("Завантажити Gemma 4 E2B (~2.6 ГБ)", onDownloadLlm)
            !llmReady -> Text(
                "Модель завантажена, але рушій ще не підключено",
                color = Color.Gray, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            else -> Text(
                "LLM активна ✓", color = Color(0xFF4CAF50), fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        Spacer(Modifier.height(32.dp))
        Text(
            "DynamicAI · $versionName",
            color = Color.Gray, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        color = Color(0xFF64B5F6),
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun SizeSlider(
    label: String,
    value: Int,
    values: List<Int>,
    onChange: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
        Text("$label: $value", color = Color.White, fontSize = 15.sp)
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
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(checked) {
                detectTapGestures(onTap = { onChange(!checked) })
            }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF64B5F6),
                checkedTrackColor = Color(0xFF64B5F6).copy(alpha = 0.4f)
            )
        )
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2A2A2A),
            contentColor = Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 15.sp)
    }
}
