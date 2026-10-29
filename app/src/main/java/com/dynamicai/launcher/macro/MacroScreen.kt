package com.dynamicai.launcher.macro

import com.dynamicai.launcher.ui.theme.AppTheme
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MacroScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val storage = remember { MacroStorage(ctx) }
    var macros by remember { mutableStateOf(storage.get()) }
    val scope = rememberCoroutineScope()
    var runningMacro by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editingMacro by remember { mutableStateOf<Macro?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AppTheme.colors.backdropStart, AppTheme.colors.backdropEnd)))
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚡ Макроси", color = AppTheme.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("+", color = AppTheme.colors.successLight, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(8.dp).clickable {
                            editingMacro = null
                            showEditor = true
                        })
                    Text("✕", color = AppTheme.colors.textSecondary, fontSize = 24.sp,
                        modifier = Modifier.padding(8.dp).clickable(onClick = onClose))
                }
            }
            Spacer(Modifier.height(16.dp))

            if (macros.isEmpty()) {
                Text("Натисни +, щоб створити свій перший макрос", color = AppTheme.colors.textSecondary, fontSize = 14.sp)
            }

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(macros) { macro ->
                    MacroCard(
                        macro = macro,
                        isRunning = runningMacro == macro.id,
                        onRun = {
                            scope.launch {
                                runningMacro = macro.id
                                val agent = com.dynamicai.launcher.llm.AgentEngine(ctx, null)
                                for (step in macro.steps) {
                                    try {
                                        val method = agent.javaClass.getDeclaredMethod("executeTool", String::class.java)
                                        method.isAccessible = true
                                        val result = method.invoke(agent, step)
                                        Toast.makeText(ctx, result.toString(), Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(ctx, "Помилка: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    delay(macro.delayMs)
                                }
                                runningMacro = null
                            }
                        },
                        onEdit = {
                            editingMacro = macro
                            showEditor = true
                        },
                        onDelete = {
                            storage.delete(macro.id)
                            macros = storage.get()
                        }
                    )
                }
            }
        }

        if (showEditor) {
            MacroEditorDialog(
                macro = editingMacro,
                onDismiss = { showEditor = false },
                onSave = { name, icon, color, steps ->
                    val m = if (editingMacro != null) {
                        editingMacro!!.copy(name = name, icon = icon, colorHex = color, steps = steps)
                    } else {
                        storage.create(name = name, steps = steps)
                    }
                    storage.add(m)
                    macros = storage.get()
                    showEditor = false
                }
            )
        }
    }
}

@Composable
fun MacroEditorDialog(
    macro: Macro?,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String, colorHex: Long, steps: List<String>) -> Unit
) {
    var name by remember { mutableStateOf(macro?.name ?: "") }
    var icon by remember { mutableStateOf(macro?.icon ?: "⚡") }
    var colorHex by remember { mutableStateOf(macro?.colorHex ?: 0xFF2196F3) }
    val steps = remember { mutableStateListOf<String>().apply { addAll(macro?.steps ?: emptyList()) } }
    var newStep by remember { mutableStateOf("") }

    val iconOptions = listOf("⚡", "🏢", "🚗", "🎬", "🌙", "🏠", "🎮", "🎵", "☕", "💼", "🌞", "🚀")
    val colorOptions = listOf(0xFF2196F3, 0xFF4CAF50, 0xFFFF9800, 0xFF9C27B0, 0xFFE91E63, 0xFF00BCD4)
    val stepTemplates = mapOf(
        "Wi-Fi ON" to "TOOL_CALL: openWifiSettings()",
        "Wi-Fi OFF" to "TOOL_CALL: setAirplaneMode()",
        "Bluetooth ON" to "TOOL_CALL: openBluetoothSettings()",
        "Авіарежим toggle" to "TOOL_CALL: setAirplaneMode()",
        "Гучність 100%" to "TOOL_CALL: setVolume(level=100)",
        "Яскравість 30%" to "TOOL_CALL: setBrightness(percent=30)",
        "Музика" to "TOOL_CALL: playMusic(query=\"музика\")",
        "Будильник 7:00" to "TOOL_CALL: setAlarm(hour=7, minute=0, message=\"Прокидайся\")",
        "Нотатка" to "TOOL_CALL: takeNote(text=\"Текст\")"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.backdropStart,
        title = { Text(if (macro == null) "Новий макрос" else "Редагувати", color = AppTheme.colors.textPrimary) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 500.dp)) {
                Text("Назва", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    textStyle = TextStyle(color = AppTheme.colors.textPrimary, fontSize = 16.sp),
                    cursorBrush = SolidColor(AppTheme.colors.successLight),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp)).background(AppTheme.colors.textPrimary.copy(alpha = 0.05f))
                        .padding(12.dp),
                    decorationBox = { inner ->
                        if (name.isEmpty()) Text("Наприклад: Доброї ночі", color = AppTheme.colors.textSecondary)
                        inner()
                    }
                )

                Spacer(Modifier.height(12.dp))
                Text("Іконка", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    iconOptions.take(6).forEach { ic ->
                        Box(
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (icon == ic) AppTheme.colors.successLight.copy(alpha = 0.3f) else AppTheme.colors.textPrimary.copy(alpha = 0.05f))
                                .clickable { icon = ic },
                            contentAlignment = Alignment.Center
                        ) { Text(ic, fontSize = 20.sp) }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Колір", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    colorOptions.forEach { c ->
                        Box(
                            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(50))
                                .background(Color(c))
                                .border(if (colorHex == c) 2.dp else 0.dp, AppTheme.colors.textPrimary, RoundedCornerShape(50))
                                .clickable { colorHex = c }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Кроки (${steps.size})", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                steps.forEachIndexed { i, s ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp)).background(AppTheme.colors.textPrimary.copy(alpha = 0.05f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${i + 1}.", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(s, color = AppTheme.colors.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("✕", color = com.dynamicai.launcher.ui.theme.AppTheme.colors.danger, fontSize = 14.sp,
                            modifier = Modifier.clickable { steps.removeAt(i) })
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Додати крок", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                stepTemplates.forEach { (label, cmd) ->
                    Text(
                        "+ $label",
                        color = AppTheme.colors.accent,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clickable { steps.add(cmd) }
                    )
                }

                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = newStep,
                    onValueChange = { newStep = it },
                    textStyle = TextStyle(color = AppTheme.colors.textPrimary, fontSize = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    cursorBrush = SolidColor(AppTheme.colors.successLight),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp)).background(AppTheme.colors.textPrimary.copy(alpha = 0.05f))
                        .padding(8.dp),
                    decorationBox = { inner ->
                        if (newStep.isEmpty()) Text("TOOL_CALL: ...", color = AppTheme.colors.textMuted, fontSize = 12.sp)
                        inner()
                    }
                )
                if (newStep.isNotBlank()) {
                    TextButton(onClick = { steps.add(newStep); newStep = "" }) {
                        Text("Додати цей крок", color = AppTheme.colors.successLight, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank() && steps.isNotEmpty()) {
                    onSave(name, icon, colorHex, steps.toList())
                }
            }) { Text("Зберегти", color = AppTheme.colors.successLight) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати", color = AppTheme.colors.textSecondary) } }
    )
}

@Composable
fun MacroCard(macro: Macro, isRunning: Boolean, onRun: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val color = Color(macro.colorHex)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.textPrimary.copy(alpha = 0.05f))
            .border(0.5.dp, if (isRunning) color else AppTheme.colors.glassBorder.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(macro.icon, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).clickable(enabled = !isRunning, onClick = onEdit)) {
            Text(macro.name, color = AppTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("${macro.steps.size} кроків · тап для редагування", color = AppTheme.colors.textSecondary, fontSize = 11.sp)
        }
        Text(if (isRunning) "⏳" else "▶️", fontSize = 20.sp, modifier = Modifier.clickable(enabled = !isRunning, onClick = onRun).padding(8.dp))
        Text("🗑️", fontSize = 18.sp, modifier = Modifier.padding(4.dp).clickable { onDelete() })
    }
}
