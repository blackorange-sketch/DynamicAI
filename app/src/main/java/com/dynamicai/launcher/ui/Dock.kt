package com.dynamicai.launcher.ui

import com.dynamicai.launcher.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.alpha
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
    val isFolder: Boolean,
    val bitmap: android.graphics.Bitmap? = null
)

@Composable
fun Dock(
    items: List<DockItem>,
    iconSizeDp: Int,
    onLaunch: (DockItem) -> Unit,
    onSearch: () -> Unit,
    onRecent: () -> Unit,
    showRecentButton: Boolean = true,
    onSwipeUp: () -> Unit,
    onLongClick: (DockItem) -> Unit = {},
    appeared: Boolean = true,
    badges: Map<String, Int> = emptyMap(),
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
                        AppTheme.colors.glassBackground,
                        AppTheme.colors.glassBackgroundAlt
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        AppTheme.colors.textSecondary,
                        AppTheme.colors.glassBorder.copy(alpha = 0.5f)
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
                            AppTheme.colors.accent.copy(alpha = 0.4f),
                            AppTheme.colors.accentDark.copy(alpha = 0.15f)
                        )
                    )
                )
                .clickable(onClick = onSearch)
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = "Пошук",
                tint = AppTheme.colors.accentLight,
                modifier = Modifier.size((iconSizeDp * 0.5f).dp)
            )
        }

        // Кнопка Recent (опційна)
        if (showRecentButton) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(iconSizeDp.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                AppTheme.colors.purple.copy(alpha = 0.4f),
                                AppTheme.colors.purple.copy(alpha = 0.15f)
                            )
                        )
                    )
                    .pressable { onRecent() }
            ) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Нещодавні",
                    tint = AppTheme.colors.purple.copy(alpha = 0.7f),
                    modifier = Modifier.size((iconSizeDp * 0.5f).dp)
                )
            }
        }

        items.forEachIndexed { index, item ->
            val itemAlpha by animateFloatAsState(
                targetValue = if (appeared) 1f else 0f,
                animationSpec = tween(durationMillis = 280, delayMillis = 40 + index * 55),
                label = "dockItem$index"
            )
            if (item.isFolder) {
                IconPlate(iconSizeDp, Modifier.alpha(itemAlpha), Modifier.pressable { onLaunch(item) }) {
                    item.bitmap?.let {
                        Icon(
                            bitmap = it.asImageBitmap(),
                            contentDescription = item.label,
                            tint = Color.Unspecified,
                            modifier = Modifier.size((iconSizeDp * 0.6f).dp)
                        )
                    }
                }
            } else {
                item.bitmap?.let {
                    IconPlate(
                        iconSizeDp,
                        Modifier.alpha(itemAlpha),
                        Modifier.pressable(
                            onTap = { onLaunch(item) },
                            onLongPress = { onLongClick(item) }
                        )
                    ) {
                        Icon(
                            bitmap = it.asImageBitmap(),
                            contentDescription = item.label,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(iconSizeDp.dp).iconShape()
                        )
                        val n = badges[item.id] ?: 0
                        if (n > 0) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                                    .clip(CircleShape)
                                    .background(AppTheme.colors.accent)
                                    .padding(horizontal = 4.dp)
                            ) {
                                androidx.compose.material3.Text(
                                    if (n > 9) "9+" else n.toString(),
                                    color = Color.White,
                                    fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
