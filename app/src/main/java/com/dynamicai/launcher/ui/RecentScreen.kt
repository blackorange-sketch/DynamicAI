package com.dynamicai.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
            // ==== Прозорий шар для тапу-закриття ====
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

            // ==== Контент знизу ====
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
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
                        fontSize = 22.sp,
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
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Очистити", color = Color(0xFF64B5F6), fontSize = 14.sp)
                        }
                    }
                }

                // Картки
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 40.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
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

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(180.dp)
            .pointerInput(task.id) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (offsetY < -80f) onForceStop()
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
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0xF02A2A2A),
                            Color(0xE01A1A1A)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.4f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            task.icon?.let {
                androidx.compose.material3.Icon(
                    bitmap = it.toBitmap(192, 192).asImageBitmap(),
                    contentDescription = task.label,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(80.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            task.label,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            modifier = Modifier.width(180.dp)
        )
    }
}
