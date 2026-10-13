package com.dynamicai.launcher.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.dynamicai.launcher.system.ShizukuManager

@Composable
fun RecentScreen(
    tasks: List<ShizukuManager.RecentTask>,
    onLaunch: (String) -> Unit,
    onForceStop: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
    visible: Boolean
) {
    val listState = rememberLazyListState()

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
        ) {
            // Прозорий шар для тапу-закриття
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { onDismiss() }
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount > 60) onDismiss()
                        }
                    }
            )

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Заголовок
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Нещодавні",
                        color = Color.White,
                        fontSize = 20.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (tasks.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.12f),
                                            Color.White.copy(alpha = 0.03f)
                                        )
                                    )
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures { onClearAll() }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint = Color(0xFF64B5F6),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Очистити", color = Color(0xFF64B5F6), fontSize = 13.sp)
                        }
                    }
                }

                if (tasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Немає нещодавніх застосунків",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 15.sp
                        )
                    }
                } else {
                    LazyRow(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(tasks, key = { it.id }) { task ->
                            val visibleIndex = listState.firstVisibleItemIndex
                            val index = tasks.indexOf(task)
                            val distance = kotlin.math.abs(index - visibleIndex)
                            val scale = when {
                                distance == 0 -> 1.0f
                                distance == 1 -> 0.9f
                                distance == 2 -> 0.8f
                                else -> 0.7f
                            }

                            RecentCard(
                                task = task,
                                scale = scale,
                                onLaunch = {
                                    onLaunch(task.packageName)
                                    onDismiss()
                                },
                                onForceStop = { onForceStop(task.packageName) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentCard(
    task: ShizukuManager.RecentTask,
    scale: Float,
    onLaunch: () -> Unit,
    onForceStop: () -> Unit
) {
    var offsetY by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "cardScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(240.dp)
            .graphicsLayerCompat(scale = animatedScale, offsetY = offsetY)
            .pointerInput(task.id) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        if (offsetY < -100f) onForceStop()
                        offsetY = 0f
                        dragging = false
                    },
                    onVerticalDrag = { _, drag ->
                        offsetY = (offsetY + drag).coerceIn(-400f, 0f)
                    }
                )
            }
            .pointerInput(task.id) {
                detectTapGestures { onLaunch() }
            }
    ) {
        // Картка застосунку
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0xFF1F1F1F),
                            Color(0xFF0D0D0D)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.3f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
        ) {
            if (task.snapshot != null) {
                // Показуємо snapshot
                androidx.compose.foundation.Image(
                    bitmap = task.snapshot.asImageBitmap(),
                    contentDescription = task.label,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Fallback — іконка велика
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    task.icon?.let {
                        androidx.compose.material3.Icon(
                            bitmap = it.toBitmap(256, 256).asImageBitmap(),
                            contentDescription = task.label,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(120.dp)
                        )
                    }
                }
            }

            // Нижній рядок з назвою + іконкою
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                task.icon?.let {
                    androidx.compose.material3.Icon(
                        bitmap = it.toBitmap(48, 48).asImageBitmap(),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    task.label,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
        }
    }
}

// Хелпер для scale + offsetY
private fun Modifier.graphicsLayerCompat(scale: Float, offsetY: Float): Modifier {
    return this
        .scale(scale)
        .offset(y = offsetY.dp)
}
