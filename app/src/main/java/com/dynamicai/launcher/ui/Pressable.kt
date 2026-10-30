package com.dynamicai.launcher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback

/** Press scale + haptic. Без bounds. */
fun Modifier.pressable(
    scaleDown: Float = 0.88f,
    haptic: Boolean = true,
    onLongPress: (() -> Unit)? = null,
    onTap: () -> Unit
): Modifier = pressableWithBounds(scaleDown, haptic, onLongPress) { onTap() }

/** Press scale + haptic + повертає bounds іконки (для zoom-launch). */
fun Modifier.pressableWithBounds(
    scaleDown: Float = 0.88f,
    haptic: Boolean = true,
    onLongPress: (() -> Unit)? = null,
    onTapWithBounds: (Rect) -> Unit
): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val hapticFb = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )

    val dim by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = androidx.compose.animation.core.tween(90),
        label = "pressDim"
    )
    this
        .onGloballyPositioned { coords ->
            bounds = coords.boundsInRoot()
        }
        .scale(scale)
        .alpha(dim)
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    if (haptic) hapticFb.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    tryAwaitRelease()
                    pressed = false
                },
                onTap = { onTapWithBounds(bounds) },
                onLongPress = onLongPress?.let { fn -> { fn() } }
            )
        }
}
