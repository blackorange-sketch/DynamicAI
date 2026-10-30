package com.dynamicai.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.overlay.ChatArchive
import com.dynamicai.launcher.overlay.SavedChat
import com.dynamicai.launcher.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Список збережених розмов: пошук, закріплення, видалення, відкриття. */
@Composable
fun ChatArchiveScreen(onOpen: (SavedChat) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var chats by remember { mutableStateOf(ChatArchive.sorted()) }
    var query by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<SavedChat?>(null) }
    val fmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale("uk", "UA")) }
    val shown = chats.filter { c ->
        query.isBlank() || c.title.contains(query, ignoreCase = true) || c.messages.any { it.content.contains(query, ignoreCase = true) }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AppTheme.colors.backdropStart, AppTheme.colors.backdropEnd)))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("←", color = AppTheme.colors.textPrimary, fontSize = 24.sp, modifier = Modifier.clickable(onClick = onClose).padding(end = 16.dp))
                Text("Історія розмов", color = AppTheme.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            BasicTextField(
                value = query, onValueChange = { query = it },
                textStyle = TextStyle(color = AppTheme.colors.textPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(AppTheme.colors.accent),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(AppTheme.colors.glassBackground).border(1.dp, AppTheme.colors.glassBorder, RoundedCornerShape(16.dp)).padding(14.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("🔍 Пошук по розмовах", color = AppTheme.colors.textMuted, fontSize = 14.sp)
                    inner()
                }
            )
            Spacer(Modifier.height(8.dp))
            if (shown.isEmpty()) {
                Text(
                    if (chats.isEmpty()) "Поки порожньо. Коли почнете нову розмову (кнопка ＋ у чаті), поточна збережеться тут."
                    else "Нічого не знайдено",
                    color = AppTheme.colors.textMuted, fontSize = 13.sp, modifier = Modifier.padding(16.dp)
                )
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(shown, key = { it.id }) { c ->
                    val last = c.messages.lastOrNull { it.role == "assistant" }?.content?.replace("\n", " ").orEmpty()
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp))
                            .background(AppTheme.colors.textPrimary.copy(alpha = if (c.pinned) 0.1f else 0.05f))
                            .clickable { onOpen(c) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text((if (c.pinned) "📌 " else "") + c.title, color = AppTheme.colors.textPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${fmt.format(Date(c.ts))} · ${c.messages.size} повід.", color = AppTheme.colors.textMuted, fontSize = 11.sp)
                            if (last.isNotBlank()) Text(last, color = AppTheme.colors.textSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text(if (c.pinned) "📍" else "📌", fontSize = 18.sp, modifier = Modifier.clickable {
                            ChatArchive.togglePin(c.id); chats = ChatArchive.sorted()
                        }.padding(8.dp))
                        Text("🗑", fontSize = 18.sp, modifier = Modifier.clickable { confirmDelete = c }.padding(8.dp))
                    }
                }
            }
        }
        confirmDelete?.let { c ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmDelete = null },
                containerColor = AppTheme.colors.surface,
                title = { Text("Видалити розмову?", color = AppTheme.colors.textPrimary) },
                text = { Text(c.title, color = AppTheme.colors.textSecondary) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        ChatArchive.delete(c.id); chats = ChatArchive.sorted(); confirmDelete = null
                    }) { Text("Видалити", color = AppTheme.colors.danger) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { confirmDelete = null }) { Text("Скасувати", color = AppTheme.colors.textSecondary) }
                }
            )
        }
    }
}
