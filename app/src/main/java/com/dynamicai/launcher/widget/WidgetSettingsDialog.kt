package com.dynamicai.launcher.widget

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
    onMove: (Int) -> Unit
) {
    var selectedPage by remember { mutableStateOf(widget.page) }
    var newPageCount by remember { mutableStateOf(pageCount.coerceAtLeast(1)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(WidgetRegistry.iconFor(widget.type), fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Text(WidgetRegistry.labelFor(widget.type), color = Color.White, fontSize = 17.sp)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Розмір
                Text("Розмір", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("small" to "S", "medium" to "M", "large" to "L").forEach { (id, label) ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (widget.size == id) Color(0x3364B5F6)
                                    else Color.White.copy(alpha = 0.06f)
                                )
                                .border(
                                    1.dp,
                                    if (widget.size == id) Color(0xFF64B5F6)
                                    else Color.White.copy(alpha = 0.15f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onSizeChange(id) }
                        ) {
                            Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Сторінка
                Text("Сторінка", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (p in 0 until newPageCount) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selectedPage == p) Color(0x3364B5F6)
                                    else Color.White.copy(alpha = 0.06f)
                                )
                                .border(
                                    1.dp,
                                    if (selectedPage == p) Color(0xFF64B5F6)
                                    else Color.White.copy(alpha = 0.15f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    selectedPage = p
                                    if (p != widget.page) onMove(p)
                                }
                        ) {
                            Text("${p + 1}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x224CAF50))
                            .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .clickable {
                                selectedPage = newPageCount
                                onMove(newPageCount)
                                newPageCount += 1
                            }
                    ) {
                        Text("+", color = Color(0xFF4CAF50), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDelete()
                onDismiss()
            }) {
                Text("Видалити", color = Color(0xFFFF8A80), fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрити", color = Color.White.copy(alpha = 0.6f))
            }
        },
        containerColor = Color(0xFF1A1A1A)
    )
}
