package com.dynamicai.launcher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun AlphabetSidebar(
    letters: List<Char>,
    activeLetter: Char?,
    onLetterSelected: (Char) -> Unit,
    onLetterReleased: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var sidebarHeight by remember { mutableStateOf(0f) }
    var lastLetter by remember { mutableStateOf<Char?>(null) }

    val activeIndex = letters.indexOf(activeLetter).takeIf { it >= 0 }

    // Обчислюємо padding у @Composable scope
    val paddingPx = with(density) { 24.dp.toPx() }

    fun letterAtOffset(y: Float): Char? {
        if (sidebarHeight <= 0f || letters.isEmpty()) return null
        val innerH = sidebarHeight - paddingPx * 2f
        if (innerH <= 0f) return null
        val localY = (y - paddingPx).coerceIn(0f, innerH - 0.001f)
        val rel = localY / innerH
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
            .width(34.dp)
            .onGloballyPositioned { coords ->
                sidebarHeight = coords.size.height.toFloat()
            }
            .pointerInput(letters) {
                detectDragGestures(
                    onDragStart = { offset -> select(letterAtOffset(offset.y)) },
                    onDrag = { change, _ ->
                        change.consume()
                        select(letterAtOffset(change.position.y))
                    },
                    onDragEnd = { lastLetter = null; onLetterReleased(false) },
                    onDragCancel = { lastLetter = null; onLetterReleased(false) }
                )
            }
            .pointerInput(letters) {
                detectTapGestures(
                    onTap = { offset ->
                        select(letterAtOffset(offset.y))
                        onLetterReleased(true)
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

                val distance = if (activeIndex != null) abs(index - activeIndex) else 99
                val targetScale = when {
                    distance == 0 -> 2.0f
                    distance == 1 -> 1.6f
                    distance == 2 -> 1.3f
                    distance == 3 -> 1.12f
                    else -> 1f
                }
                val scale by animateFloatAsState(
                    targetValue = targetScale,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "waveScale"
                )

                val targetOffset = when {
                    distance == 0 -> (-14).dp
                    distance == 1 -> (-10).dp
                    distance == 2 -> (-6).dp
                    else -> 0.dp
                }
                val xOffset by animateDpAsState(
                    targetValue = targetOffset,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "waveOffset"
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .offset(x = xOffset)
                            .size(22.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(
                                brush = if (isActive) {
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xFF64B5F6),
                                            Color(0xFF2196F3)
                                        )
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.10f),
                                            Color.White.copy(alpha = 0.02f)
                                        )
                                    )
                                }
                            )
                            .border(
                                width = 0.5.dp,
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = if (isActive) 0.7f else 0.2f),
                                        Color.White.copy(alpha = if (isActive) 0.3f else 0.05f)
                                    )
                                ),
                                shape = CircleShape
                            )
                    ) {
                        Text(
                            text = letter.toString(),
                            color = if (isActive) Color.White else Color.White.copy(
                                alpha = (1f - distance * 0.12f).coerceIn(0.4f, 1f)
                            ),
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            style = androidx.compose.material3.LocalTextStyle.current.copy(
                                fontSize = 11.sp,
                                lineHeight = 11.sp,
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.Both
                                )
                            )
                        )
                    }
                }
            }
        }
    }
}
