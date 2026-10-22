package com.dynamicai.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.note.Note

@Composable
fun NotesWidget(
    notes: List<Note>,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: (String) -> Unit,
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newText by remember { mutableStateOf("") }
    val maxVisible = 4
    val visible = notes.take(maxVisible)
    val remaining = (notes.size - maxVisible).coerceAtLeast(0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xCC1F1F1F),
                        Color(0xAA0D0D0D)
                    )
                )
            )
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // Заголовок
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "📝 Нотатки",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (notes.isEmpty()) "" else "${notes.count { !it.done }}/${notes.size}",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
            Spacer(Modifier.width(12.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
                    .pointerInput(Unit) {
                        detectTapGestures { showAddDialog = true }
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Список нотаток
        if (visible.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Немає нотаток. Тап + щоб додати",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 13.sp
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                visible.forEach { note ->
                    NoteWidgetRow(
                        note = note,
                        onToggle = { onToggle(note.id) },
                        onDelete = { onDelete(note.id) }
                    )
                }
            }

            if (remaining > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "і ще $remaining…",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Кнопка "Всі"
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x22FFFFFF))
                .pointerInput(Unit) {
                    detectTapGestures { onOpenAll() }
                }
                .padding(vertical = 8.dp)
        ) {
            Text(
                "Всі нотатки →",
                color = Color(0xFF64B5F6),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Нова нотатка", color = Color.White) },
            text = {
                BasicTextField(
                    value = newText,
                    onValueChange = { newText = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(Color(0xFF64B5F6)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    decorationBox = { inner ->
                        if (newText.isEmpty()) {
                            Text("Що запам'ятати?", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                        }
                        inner()
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newText.isNotBlank()) {
                        onAdd(newText)
                        newText = ""
                    }
                    showAddDialog = false
                }) { Text("Додати", color = Color(0xFF64B5F6)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Скасувати", color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = Color(0xFF1A1A1A)
        )
    }
}

@Composable
private fun NoteWidgetRow(
    note: Note,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .pointerInput(note.id) {
                detectTapGestures(
                    onTap = { onToggle() },
                    onLongPress = { onDelete() }
                )
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(
                    if (note.done) Color(0xFF4CAF50).copy(alpha = 0.6f)
                    else Color.White.copy(alpha = 0.12f)
                )
                .border(
                    1.dp,
                    if (note.done) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.3f),
                    CircleShape
                )
        ) {
            if (note.done) {
                Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            note.text,
            color = if (note.done) Color.White.copy(alpha = 0.5f) else Color.White,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (note.done) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier.weight(1f)
        )
    }
}
