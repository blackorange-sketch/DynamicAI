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
    var pagesAvailable by remember { mutableStateOf(currentPageCount.coerceAtLeast(1)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.surface,
        title = { Text("Додати віджет", color = AppTheme.colors.textPrimary) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Тип", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                WidgetRegistry.available.forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selectedType == type.id) AppTheme.colors.accent.copy(alpha = 0.2f)
                                else AppTheme.colors.textPrimary.copy(alpha = 0.05f)
                            )
                            .border(
                                1.dp,
                                if (selectedType == type.id) AppTheme.colors.accent
                                else AppTheme.colors.glassBorder.copy(alpha = 0.7f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedType = type.id }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(type.icon, fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(type.label, color = AppTheme.colors.textPrimary, fontSize = 14.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Сторінка", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (p in 0 until pagesAvailable) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selectedPage == p) AppTheme.colors.accent.copy(alpha = 0.2f)
                                    else AppTheme.colors.textPrimary.copy(alpha = 0.05f)
                                )
                                .border(
                                    1.dp,
                                    if (selectedPage == p) AppTheme.colors.accent
                                    else AppTheme.colors.glassBorder.copy(alpha = 0.7f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedPage = p }
                        ) {
                            Text("${p + 1}", color = AppTheme.colors.textPrimary, fontSize = 14.sp)
                        }
                    }
                    // Кнопка "+" для нової сторінки
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppTheme.colors.textPrimary.copy(alpha = 0.05f))
                            .border(1.dp, AppTheme.colors.success.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .clickable {
                                pagesAvailable++
                                selectedPage = pagesAvailable - 1
                            }
                    ) {
                        Text("+", color = AppTheme.colors.success, fontSize = 20.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(selectedType, selectedPage) }) {
                Text("Додати", color = AppTheme.colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Скасувати", color = AppTheme.colors.textSecondary)
            }
        }
    )
}
