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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
            values = listOf(32, 40, 48, 56, 64),
            onChange = { onSettingsChange(settings.copy(iconSizeDp = it)) }
        )
        SizeSlider(
            label = "Іконки у дока",
            value = settings.dockIconSizeDp,
            values = listOf(32, 40, 48, 56, 64),
            onChange = { onSettingsChange(settings.copy(dockIconSizeDp = it)) }
        )
        SizeSlider(
            label = "Розмір шрифту",
            value = settings.fontSizeSp,
            values = listOf(14, 16, 18, 20, 22, 24),
            onChange = { onSettingsChange(settings.copy(fontSizeSp = it)) }
        )

        Section("Вигляд")
        SwitchRow(
            label = "Показувати назви застосунків",
            checked = settings.showLabels,
            onChange = { onSettingsChange(settings.copy(showLabels = it)) }
        )

        Section("Дії")
        ActionRow("Оновити контакти", onRefreshContacts)
        ActionRow("Очистити історію команд", onClearHistory)
        ActionRow(
            "Показати всі приховані (${hiddenCount})",
            onClearHidden
        )

        Section("Плаваючий док")
        if (!overlayPermissionGranted) {
            ActionRow("Дозволити показ поверх інших застосунків", onRequestOverlayPermission)
        } else {
            ActionRow(
                if (overlayActive) "Вимкнути плаваючий док" else "Увімкнути плаваючий док",
                onToggleOverlay
            )
        }

        Section("Розмір у плаваючому дока")
        SizeSlider(
            label = "Іконки",
            value = settings.overlayIconSizeDp,
            values = listOf(38, 44, 50, 56, 62, 68),
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
