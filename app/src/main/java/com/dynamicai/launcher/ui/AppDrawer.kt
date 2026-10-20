package com.dynamicai.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.model.AppInfo

private data class Category(val id: String, val label: String)

@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    backgroundAlphaPercent: Int,
    iconSizeDp: Int,
    fontSizeSp: Int,
    onLaunch: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
    visible: Boolean,
    selectedCategory: String = "all",
    onCategoryChange: (String) -> Unit = {},
    gridState: LazyGridState = rememberLazyGridState()
) {
    val haptic = LocalHapticFeedback.current

    // Pull-to-dismiss: свайп вниз будь-де, але тільки коли список на верху
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }
    val dismissThreshold = 120f
    
    val nestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.Drag) {
                    val atTop = gridState.firstVisibleItemIndex == 0 
                                && gridState.firstVisibleItemScrollOffset == 0
                    if (atTop && available.y > 0f) {
                        accumulatedDrag += available.y
                        if (accumulatedDrag > dismissThreshold) {
                            onDismiss()
                            accumulatedDrag = 0f
                        }
                    } else {
                        accumulatedDrag = 0f
                    }
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (available.y < 0f) {
                    accumulatedDrag = 0f
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    val columns = remember(iconSizeDp) {
        GridCells.Adaptive(minSize = (iconSizeDp + 24).dp)
    }

    // Категорії — тільки ті, де є ≥ 3 застосунки
    val categories: List<Category> = remember(apps) {
        val counts = apps.groupingBy { it.category }.eachCount()
        val list = mutableListOf(Category("all", "Всі"))
        val labels = mapOf(
            "games" to "Ігри",
            "social" to "Соц",
            "media" to "Медіа",
            "tools" to "Інструменти",
            "maps" to "Навігація"
        )
        for ((id, label) in labels) {
            if ((counts[id] ?: 0) >= 3) list.add(Category(id, label))
        }
        list
    }

    val filtered = remember(apps, selectedCategory) {
        if (selectedCategory == "all") apps
        else apps.filter { it.category == selectedCategory }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(280, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(220)),
        exit = slideOutVertically(
            targetOffsetY = { it / 2 },
            animationSpec = tween(220, easing = FastOutSlowInEasing)
        ) + fadeOut(tween(180))
    ) {
        val bgAlpha = kotlin.math.max(0.65f, backgroundAlphaPercent / 100f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x1F1F1F).copy(alpha = bgAlpha),
                            Color(0x0D0D0D).copy(alpha = bgAlpha)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(start = 4.dp, end = 32.dp)
            ) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = columns,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppGridItem(app, iconSizeDp, fontSizeSp) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLaunch(app)
                            onDismiss()
                        }
                    }
                }

                // ==== Категорії внизу ====
                if (categories.size > 1) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            CategoryChip(
                                label = cat.label,
                                selected = selectedCategory == cat.id,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onCategoryChange(cat.id)
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        color = if (selected) Color.White else Color.White.copy(alpha = 0.65f),
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    if (selected) listOf(
                        Color(0xFF64B5F6).copy(alpha = 0.55f),
                        Color(0xFF2196F3).copy(alpha = 0.25f)
                    ) else listOf(
                        Color(0xE61F1F1F),
                        Color(0xCC0D0D0D)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    if (selected) listOf(
                        Color(0xFF64B5F6).copy(alpha = 0.7f),
                        Color(0xFF64B5F6).copy(alpha = 0.2f)
                    ) else listOf(
                        Color.White.copy(alpha = 0.2f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

@Composable
private fun AppGridItem(
    app: AppInfo,
    iconSizeDp: Int,
    fontSizeSp: Int,
    onTap: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onTap = onTap)
            .padding(vertical = 6.dp)
    ) {
        val glassBrush = remember {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.10f),
                    Color.White.copy(alpha = 0.02f)
                )
            )
        }
        val glassBorderBrush = remember {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.22f),
                    Color.White.copy(alpha = 0.04f)
                )
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size((iconSizeDp + 16).dp)
                .clip(RoundedCornerShape((iconSizeDp / 2 + 8).dp))
                .background(brush = glassBrush)
                .border(
                    width = 0.5.dp,
                    brush = glassBorderBrush,
                    shape = RoundedCornerShape((iconSizeDp / 2 + 8).dp)
                )
        ) {
            Icon(
                bitmap = app.bitmap!!.asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier.size(iconSizeDp.dp),
                tint = Color.Unspecified
            )
        }
        if (fontSizeSp > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = app.label,
                color = Color.White,
                fontSize = fontSizeSp.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
