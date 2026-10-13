package com.dynamicai.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
            // Прозорий шар для тапу-закриття (верхня половина)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.35f)
                    .align(Alignment.TopCenter)
                    .pointerInput(Unit) {
                        detectTapGestures { onDismiss() }
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount > 60) onDismiss()
                        }
                    }
            )

            // Контент у нижній частині — зручно для пальця
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.65f)
                    .navigationBarsPadding()
            ) {
                // Заголовок
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Нещодавні",
                        color = Color.White,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${tasks.size}",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    if (tasks.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
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
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color(0xFF64B5F6),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
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
                    // Сітка 3×2
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tasks, key = { it.id }) { task ->
                            RecentCard(
                                task = task,
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
    onLaunch: () -> Unit,
    onForceStop: () -> Unit
) {
    var offsetY by remember { mutableStateOf(0f) }

    val accentColor = remember(task.packageName) {
        IconColors.extract(task.packageName, task.icon)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .offset(y = offsetY.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.5f),
                        accentColor.copy(alpha = 0.15f),
                        Color(0xFF0D0D0D)
                    ),
                    radius = 300f
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
                shape = RoundedCornerShape(20.dp)
            )
            .pointerInput(task.id) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (offsetY < -60f) onForceStop()
                        offsetY = 0f
                    },
                    onVerticalDrag = { _, drag ->
                        offsetY = (offsetY + drag).coerceIn(-200f, 0f)
                    }
                )
            }
            .pointerInput(task.id) {
                detectTapGestures { onLaunch() }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Іконка в glass-квадраті
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.04f)
                            )
                        )
                    )
                    .border(
                        width = 0.5.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.1f)
                            )
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
            ) {
                task.icon?.let {
                    androidx.compose.material3.Icon(
                        bitmap = it.toBitmap(128, 128).asImageBitmap(),
                        contentDescription = task.label,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Назва
            Text(
                task.label,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}
