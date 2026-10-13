package com.dynamicai.launcher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun AlphabetSidebar(
    letters: List<Char>,
    activeLetter: Char?,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var sidebarHeight by remember { mutableStateOf(0f) }
    var lastLetter by remember { mutableStateOf<Char?>(null) }

    // Індекс активної літери для розрахунку хвилі
    val activeIndex = letters.indexOf(activeLetter).takeIf { it >= 0 }

    fun letterAtOffset(y: Float): Char? {
        if (sidebarHeight <= 0f || letters.isEmpty()) return null
        val localY = y
        val rel = (localY / sidebarHeight).coerceIn(0f, 0.9999f)
        val idx = (rel * letters.size).toInt().coerceIn(0, letters.size - 1)
        return letters[idx]
    }

    fun select(letter: Char?) {
        if (letter == null) return
        if (letter != lastLetter) {
            lastLetter = letter
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onLetterSelected(letter)
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(32.dp)
            .onGloballyPositioned { coords ->
                sidebarHeight = coords.size.height.toFloat()
            }
            .pointerInput(letters) {
                detectDragGestures(
                    onDragStart = { offset ->
                        select(letterAtOffset(offset.y))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        select(letterAtOffset(change.position.y))
                    },
                    onDragEnd = { lastLetter = null },
                    onDragCancel = { lastLetter = null }
                )
            }
            .pointerInput(letters) {
                detectTapGestures(
                    onTap = { offset ->
                        select(letterAtOffset(offset.y))
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            letters.forEachIndexed { index, letter ->
                val isActive = letter == activeLetter

                // Wave: близькість до активної літери → більша шкала
                val distance = if (activeIndex != null) abs(index - activeIndex) else 99
                val targetScale = when {
                    distance == 0 -> 2.2f
                    distance == 1 -> 1.8f
                    distance == 2 -> 1.45f
                    distance == 3 -> 1.2f
                    distance == 4 -> 1.08f
                    else -> 1f
                }
                val scale by animateFloatAsState(
                    targetValue = targetScale,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "waveScale"
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(18.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(if (isActive) Color.White else Color.Transparent)
                ) {
                    Text(
                        text = letter.toString(),
                        color = if (isActive) Color.Black else Color.White.copy(alpha = (1f - distance * 0.15f).coerceIn(0.5f, 1f)),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
