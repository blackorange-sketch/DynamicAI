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