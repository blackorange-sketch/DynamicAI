package com.dynamicai.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.dynamicai.launcher.model.AppInfo

data class DockItem(
    val id: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable?,
    val isFolder: Boolean
)

@Composable
fun Dock(
    items: List<DockItem>,
    iconSizeDp: Int,
    onLaunch: (DockItem) -> Unit,
    onSearch: () -> Unit,
    onSwipeUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -20) onSwipeUp()
                }
            }
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xE61F1F1F),
                        Color(0xCC0D0D0D)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.4f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Кнопка пошуку — акцентна
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(iconSizeDp.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF64B5F6).copy(alpha = 0.4f),
                            Color(0xFF2196F3).copy(alpha = 0.15f)
                        )
                    )
                )
                .clickable(onClick = onSearch)
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = "Пошук",
                tint = Color(0xFFB3E5FC),
                modifier = Modifier.size((iconSizeDp * 0.5f).dp)
            )
        }

        items.forEach { item ->
            if (item.isFolder) {
                Box(
                    modifier = Modifier
                        .size(iconSizeDp.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.15f),
                                    Color.White.copy(alpha = 0.05f)
                                )
                            )
                        )
                        .clickable { onLaunch(item) },
                    contentAlignment = Alignment.Center
                ) {
                    item.icon?.let {
                        Icon(
                            bitmap = it.toBitmap(96, 96).asImageBitmap(),
                            contentDescription = item.label,
                            tint = Color.Unspecified,
                            modifier = Modifier.size((iconSizeDp * 0.6f).dp)
                        )
                    }
                }
            } else {
                item.icon?.let {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size((iconSizeDp + 8).dp)
                            .clip(RoundedCornerShape((iconSizeDp / 2 + 4).dp))
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.08f),
                                        Color.White.copy(alpha = 0.02f)
                                    )
                                )
                            )
                            .border(
                                width = 0.5.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.2f),
                                        Color.White.copy(alpha = 0.04f)
                                    )
                                ),
                                shape = RoundedCornerShape((iconSizeDp / 2 + 4).dp)
                            )
                            .clickable { onLaunch(item) }
                    ) {
                        Icon(
                            bitmap = it.toBitmap(96, 96).asImageBitmap(),
                            contentDescription = item.label,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(iconSizeDp.dp)
                        )
                    }
                }
            }
        }
    }
}
