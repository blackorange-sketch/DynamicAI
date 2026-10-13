package com.dynamicai.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.dynamicai.launcher.model.AppInfo

data class DockItem(
    val id: String,               // "app:com.xxx" або "folder:UUID"
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
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xCC1A1A1A))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Кнопка пошуку — перша, відрізняється кольором
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(iconSizeDp.dp)
                .clip(CircleShape)
                .background(Color(0xFF2196F3).copy(alpha = 0.2f))
                .clickable(onClick = onSearch)
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = "Пошук",
                tint = Color(0xFF64B5F6),
                modifier = Modifier.size((iconSizeDp * 0.5f).dp)
            )
        }

        items.forEach { item ->
            if (item.isFolder) {
                // Папка — рамка з декількома мінііконками
                Box(
                    modifier = Modifier
                        .size(iconSizeDp.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2A2A2A))
                        .clickable { onLaunch(item) },
                    contentAlignment = Alignment.Center
                ) {
                    item.icon?.let {
                        Icon(
                            bitmap = it.toBitmap(96, 96).asImageBitmap(),
                            contentDescription = item.label,
                            tint = Color.Unspecified,
                            modifier = Modifier.size((iconSizeDp * 0.55f).dp)
                        )
                    }
                }
            } else {
                item.icon?.let {
                    Icon(
                        bitmap = it.toBitmap(96, 96).asImageBitmap(),
                        contentDescription = item.label,
                        tint = Color.Unspecified,
                        modifier = Modifier
                            .size(iconSizeDp.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onLaunch(item) }
                    )
                }
            }
        }
    }
}
