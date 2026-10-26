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
fun AddWidgetDialog(
    currentPageCount: Int,
    onDismiss: () -> Unit,
    onAdd: (type: String, page: Int) -> Unit
) {
    var selectedType by remember { mutableStateOf(WidgetRegistry.available.first().id) }
    var selectedPage by remember { mutableStateOf(0) }
    // Якщо користувач хоче нову сторінку — +1
    var pagesAvailable by remember { mutableStateOf(currentPageCount.coerceAtLeast(1)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Додати віджет", color = Color.White) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Тип", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                WidgetRegistry.available.forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selectedType == type.id) Color(0x3364B5F6)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .border(
                                1.dp,
                                if (selectedType == type.id) Color(0xFF64B5F6)
                                else Color.White.copy(alpha = 0.1f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedType = type.id }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(type.icon, fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(type.label, color = Color.White, fontSize = 14.sp)
                    }
                }

                                // Системний музичний віджет
                val ctx = androidx.compose.ui.platform.LocalContext.current
                Text("Системні", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .clickable {
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🎵", fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Музичний плеєр", color = Color.White, fontSize = 14.sp)
                        Text("Системний (ReVanced)", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text("Сторінка", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (p in 0 until pagesAvailable) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selectedPage == p) Color(0x3364B5F6)
                                    else Color.White.copy(alpha = 0.05f)
                                )
                                .border(
                                    1.dp,
                                    if (selectedPage == p) Color(0xFF64B5F6)
                                    else Color.White.copy(alpha = 0.1f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedPage = p }
                        ) {
                            Text("${p + 1}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    // Кнопка "+ Нова сторінка"
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x224CAF50))
                            .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .clickable {
                                selectedPage = pagesAvailable
                                pagesAvailable += 1
                            }
                    ) {
                        Text("+", color = Color(0xFF4CAF50), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onAdd(selectedType, selectedPage)
                onDismiss()
            }) { Text("Додати", color = Color(0xFF64B5F6)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Скасувати", color = Color.White.copy(alpha = 0.6f))
            }
        },
        containerColor = Color(0xFF1A1A1A)
    )
}
