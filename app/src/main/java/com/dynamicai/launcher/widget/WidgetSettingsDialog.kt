package com.dynamicai.launcher.widget

import com.dynamicai.launcher.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WidgetSettingsDialog(
    widget: PlacedWidget,
    pageCount: Int,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSizeChange: (String) -> Unit,
    onMove: (Int) -> Unit,
    onStackWithPrevious: () -> Unit = {},
    onUnstack: () -> Unit = {}
) {
    var selectedPage by remember { mutableStateOf(widget.page) }
    var newPageCount by remember { mutableStateOf(pageCount.coerceAtLeast(1)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(WidgetRegistry.iconFor(widget.type), fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Text(WidgetRegistry.labelFor(widget.type), color = AppTheme.colors.textPrimary, fontSize = 17.sp)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Розмір
                Text("Розмір", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("small" to "S", "medium" to "M", "large" to "L", "full" to "F").forEach { (id, label) ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (widget.size == id) AppTheme.colors.accent.copy(alpha = 0.2f)
                                    else AppTheme.colors.textPrimary.copy(alpha = 0.06f)
                                )
                                .border(
                                    1.dp,
                                    if (widget.size == id) AppTheme.colors.accent
                                    else AppTheme.colors.glassBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onSizeChange(id) }
                        ) {
                            Text(label, color = AppTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { if (widget.group.isBlank()) onStackWithPrevious() else onUnstack(); onDismiss() }) {
                    Text(
                        if (widget.group.isBlank()) "⧉ Об'єднати в стек із попереднім" else "⧉ Вийти зі стека",
                        color = AppTheme.colors.accent
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Сторінка
                Text("Сторінка", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (p in 0 until newPageCount) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selectedPage == p) AppTheme.colors.accent.copy(alpha = 0.2f)
                                    else AppTheme.colors.textPrimary.copy(alpha = 0.06f)
                                )
                                .border(
                                    1.dp,
                                    if (selectedPage == p) AppTheme.colors.accent
                                    else AppTheme.colors.glassBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    selectedPage = p
                                    if (p != widget.page) onMove(p)
                                }
                        ) {
                            Text("${p + 1}", color = AppTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppTheme.colors.success.copy(alpha = 0.13f))
                            .border(1.dp, AppTheme.colors.success.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .clickable {
                                selectedPage = newPageCount
                                onMove(newPageCount)
                                newPageCount += 1
                            }
                    ) {
                        Text("+", color = AppTheme.colors.success, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDelete()
                onDismiss()
            }) {
                Text("Видалити", color = AppTheme.colors.danger, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрити", color = AppTheme.colors.textSecondary)
            }
        },
        containerColor = AppTheme.colors.surface
    )
}
