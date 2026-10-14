package com.dynamicai.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.macro.Macro

@Composable
fun MacroScreen(
    macros: List<Macro>,
    onSave: (Macro) -> Unit,
    onDelete: (String) -> Unit,
    onRun: (Macro) -> Unit,
    onClose: () -> Unit
) {
    var showEditor by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var editSteps by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White)
            }
            Text("Макроси", color = Color.White, fontSize = 22.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                editId = null
                editName = ""
                editSteps = ""
                showEditor = true
            }) {
                Icon(Icons.Default.Add, "Новий макрос", tint = Color(0xFF64B5F6))
            }
        }

        if (macros.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Немає макросів.\nТап + — створити перший.\n\nПриклад:\nІм'я: Доброго ранку\nКроки (кожен на новому рядку):\nувімкни wifi\nяскравіше\nвідкрий spotify",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(macros, key = { it.id }) { m ->
                    MacroRow(
                        macro = m,
                        onRun = { onRun(m) },
                        onEdit = {
                            editId = m.id
                            editName = m.name
                            editSteps = m.steps.joinToString("\n")
                            showEditor = true
                        },
                        onDelete = { onDelete(m.id) }
                    )
                }
            }
        }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (editId == null) "Новий макрос" else "Редагувати", color = Color.White) },
            text = {
                Column {
                    Text("Назва", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    BasicTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color(0xFF64B5F6)),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        decorationBox = { inner ->
                            if (editName.isEmpty()) Text("Доброго ранку", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                            inner()
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Кроки (кожен на новому рядку)", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    BasicTextField(
                        value = editSteps,
                        onValueChange = { editSteps = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        cursorBrush = SolidColor(Color(0xFF64B5F6)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        decorationBox = { inner ->
                            if (editSteps.isEmpty()) {
                                Text("увімкни wifi\nяскравіше\nвідкрий spotify", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                            }
                            inner()
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val steps = editSteps.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                    if (editName.isNotBlank() && steps.isNotEmpty()) {
                        val id = editId ?: java.util.UUID.randomUUID().toString()
                        onSave(Macro(id = id, name = editName.trim(), steps = steps))
                    }
                    showEditor = false
                }) { Text("Зберегти", color = Color(0xFF64B5F6)) }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) { Text("Скасувати", color = Color.White.copy(alpha = 0.6f)) }
            },
            containerColor = Color(0xFF1A1A1A)
        )
    }
}

@Composable
private fun MacroRow(
    macro: Macro,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .pointerInput(macro.id) { detectTapGestures(onLongPress = { onEdit() }, onTap = { onRun() }) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFFFB300).copy(alpha = 0.18f))
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = Color(0xFFFFB300), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(macro.name, color = Color.White, fontSize = 15.sp)
            Text("${macro.steps.size} крок(ів) • тап — запустити", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, "Редагувати", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, "Видалити", tint = Color(0xFFFF8A80), modifier = Modifier.size(18.dp))
        }
    }
}
