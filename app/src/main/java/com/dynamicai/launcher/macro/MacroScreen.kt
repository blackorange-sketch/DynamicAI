package com.dynamicai.launcher.macro

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
            .background(Brush.verticalGradient(listOf(Color(0xFF14141E), Color(0xFF050505))))
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚡ Макроси", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("+", color = Color(0xFF00E676), fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(8.dp).clickable {
                            editingMacro = null
                            showEditor = true
                        })
                    Text("✕", color = Color.White.copy(alpha = 0.6f), fontSize = 24.sp,
                        modifier = Modifier.padding(8.dp).clickable(onClick = onClose))
                }
            }
            Spacer(Modifier.height(16.dp))

            if (macros.isEmpty()) {
                Text("Натисни +, щоб створити свій перший макрос", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
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
        containerColor = Color(0xFF14141E),
        title = { Text(if (macro == null) "Новий макрос" else "Редагувати", color = Color.White) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 500.dp)) {
                Text("Назва", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color(0xFF00E676)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.05f))
                        .padding(12.dp),
                    decorationBox = { inner ->
                        if (name.isEmpty()) Text("Наприклад: Доброї ночі", color = Color.White.copy(alpha = 0.4f))
                        inner()
                    }
                )

                Spacer(Modifier.height(12.dp))
                Text("Іконка", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    iconOptions.take(6).forEach { ic ->
                        Box(
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (icon == ic) Color(0xFF00E676).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f))
                                .clickable { icon = ic },
                            contentAlignment = Alignment.Center
                        ) { Text(ic, fontSize = 20.sp) }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Колір", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    colorOptions.forEach { c ->
                        Box(
                            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(50))
                                .background(Color(c))
                                .border(if (colorHex == c) 2.dp else 0.dp, Color.White, RoundedCornerShape(50))
                                .clickable { colorHex = c }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Кроки (${steps.size})", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                steps.forEachIndexed { i, s ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.05f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${i + 1}.", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(s, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("✕", color = Color(0xFFFF5252), fontSize = 14.sp,
                            modifier = Modifier.clickable { steps.removeAt(i) })
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Додати крок", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                stepTemplates.forEach { (label, cmd) ->
                    Text(
                        "+ $label",
                        color = Color(0xFF64B5F6),
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clickable { steps.add(cmd) }
                    )
                }

                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = newStep,
                    onValueChange = { newStep = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    cursorBrush = SolidColor(Color(0xFF00E676)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.05f))
                        .padding(8.dp),
                    decorationBox = { inner ->
                        if (newStep.isEmpty()) Text("TOOL_CALL: ...", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp)
                        inner()
                    }
                )
                if (newStep.isNotBlank()) {
                    TextButton(onClick = { steps.add(newStep); newStep = "" }) {
                        Text("Додати цей крок", color = Color(0xFF00E676), fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank() && steps.isNotEmpty()) {
                    onSave(name, icon, colorHex, steps.toList())
                }
            }) { Text("Зберегти", color = Color(0xFF00E676)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати", color = Color.White.copy(alpha = 0.6f)) } }
    )
}

@Composable
fun MacroCard(macro: Macro, isRunning: Boolean, onRun: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val color = Color(macro.colorHex)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(0.5.dp, if (isRunning) color else Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(macro.icon, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).clickable(enabled = !isRunning, onClick = onEdit)) {
            Text(macro.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("${macro.steps.size} кроків · тап для редагування", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        }
        Text(if (isRunning) "⏳" else "▶️", fontSize = 20.sp, modifier = Modifier.clickable(enabled = !isRunning, onClick = onRun).padding(8.dp))
        Text("🗑️", fontSize = 18.sp, modifier = Modifier.padding(4.dp).clickable { onDelete() })
    }
}
