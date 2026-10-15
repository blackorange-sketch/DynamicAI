package com.dynamicai.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.note.Note
import com.dynamicai.launcher.note.NotesStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotesScreen(
    onClose: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val storage = remember { NotesStorage(ctx) }
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var showEditor by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { notes = storage.get() }

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
            Text("Нотатки", color = Color.White, fontSize = 22.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                editText = ""
                showEditor = true
            }) {
                Icon(Icons.Default.Add, "Нова нотатка", tint = Color(0xFF64B5F6))
            }
        }

        if (notes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Немає нотаток.\nТап + — створити.\n\nАбо скажи: «занотуй купити хліб»",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteRow(
                        note = note,
                        onToggle = { storage.toggle(note.id); notes = storage.get() },
                        onDelete = { storage.remove(note.id); notes = storage.get() },
                        onEdit = {
                            editText = note.text
                            showEditor = true
                        }
                    )
                }
            }
        }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text("Нотатка", color = Color.White) },
            text = {
                BasicTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(Color(0xFF64B5F6)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    decorationBox = { inner ->
                        if (editText.isEmpty()) {
                            Text("Купити хліб…", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                        }
                        inner()
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editText.isNotBlank()) {
                        storage.add(editText)
                        notes = storage.get()
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
private fun NoteRow(
    note: Note,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("d MMM HH:mm", Locale("uk", "UA")) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .pointerInput(note.id) { detectTapGestures(onLongPress = { onEdit() }) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (note.done) Color(0xFF4CAF50).copy(alpha = 0.5f)
                    else Color.White.copy(alpha = 0.1f)
                )
                .pointerInput(note.id) { detectTapGestures { onToggle() } }
        ) {
            if (note.done) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                note.text,
                color = if (note.done) Color.White.copy(alpha = 0.5f) else Color.White,
                fontSize = 14.sp,
                textDecoration = if (note.done) TextDecoration.LineThrough else TextDecoration.None
            )
            Text(
                dateFmt.format(Date(note.createdAt)),
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 11.sp
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, "Видалити", tint = Color(0xFFFF8A80), modifier = Modifier.size(18.dp))
        }
    }
}
