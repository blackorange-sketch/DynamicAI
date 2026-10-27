package com.dynamicai.launcher.macro

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF14141E), Color(0xFF050505))))
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚡ Макроси", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("✕", color = Color.White.copy(alpha = 0.6f), fontSize = 24.sp, modifier = Modifier.padding(8.dp).clickable(onClick = onClose))
            }
            
            Spacer(Modifier.height(16.dp))

            // Пресети (якщо порожньо)
            if (macros.isEmpty()) {
                Text("Створи свій перший макрос", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                PresetMacroCard("Я на роботі", "🏢", Color(0xFF4CAF50)) {
                    val m = storage.create(
                        name = "Я на роботі",
                        steps = listOf(
                            "TOOL_CALL: openWifiSettings()",
                            "TOOL_CALL: setVolume(level=100)",
                            "TOOL_CALL: setAirplaneMode()"
                        )
                    )
                    macros = storage.get()
                }
                PresetMacroCard("Я в авто", "🚗", Color(0xFFFF9800)) {
                    val m = storage.create(
                        name = "Я в авто",
                        steps = listOf(
                            "TOOL_CALL: setAirplaneMode()",
                            "TOOL_CALL: openBluetoothSettings()",
                            "TOOL_CALL: playMusic(query=\"музика в авто\")"
                        )
                    )
                    macros = storage.get()
                }
                PresetMacroCard("Кіно", "🎬", Color(0xFF9C27B0)) {
                    val m = storage.create(
                        name = "Кіно",
                        steps = listOf(
                            "TOOL_CALL: setBrightness(percent=30)",
                            "TOOL_CALL: openApp(name=\"Netflix\")"
                        )
                    )
                    macros = storage.get()
                }
            }

            // Список макросів
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(macros) { macro ->
                    MacroCard(
                        macro = macro,
                        isRunning = runningMacro == macro.id,
                        onRun = {
                            scope.launch {
                                runningMacro = macro.id
                                val agent = com.dynamicai.launcher.llm.AgentEngine(ctx, null) // null, бо нам не потрібна LLM для виконання
                                for (step in macro.steps) {
                                    // Виконуємо команду через рефлексію (або спеціальний метод)
                                    // Оскільки AgentEngine.executeTool приватний, ми викликаємо його через рефлексію
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
                        onDelete = {
                            storage.delete(macro.id)
                            macros = storage.get()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PresetMacroCard(name: String, icon: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(0.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Text("Створити: $name", color = Color.White, fontSize = 15.sp)
    }
}

@Composable
fun MacroCard(macro: Macro, isRunning: Boolean, onRun: () -> Unit, onDelete: () -> Unit) {
    val color = Color(macro.colorHex)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(0.5.dp, if (isRunning) color else Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .clickable(enabled = !isRunning, onClick = onRun)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(macro.icon, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(macro.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("${macro.steps.size} кроків", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        if (isRunning) {
            Text("⏳", fontSize = 20.sp)
        } else {
            Text("▶️", fontSize = 20.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text("🗑️", fontSize = 18.sp, modifier = Modifier.padding(4.dp).clickable { onDelete() })
    }
}
