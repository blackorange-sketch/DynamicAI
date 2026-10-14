package com.dynamicai.launcher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Press scale + haptic для іконок.
 * При утриманні — іконка стискається, легка вібрація.
 * При відпусканні — пружно повертається + onTap.
 */
fun Modifier.pressable(
    scaleDown: Float = 0.88f,
    haptic: Boolean = true,
    onLongPress: (() -> Unit)? = null,
    onTap: () -> Unit
): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val hapticFb = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )

    this
        .scale(scale)
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    if (haptic) hapticFb.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    tryAwaitRelease()
                    pressed = false
                },
                onTap = { onTap() },
                onLongPress = onLongPress?.let { fn -> { fn() } }
            )
        }
}
