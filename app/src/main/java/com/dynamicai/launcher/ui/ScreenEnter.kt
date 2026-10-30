package com.dynamicai.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Плавна поява екрана: від 94% масштабу й прозорості до повного вигляду. */
@Composable
fun ScreenEnter(content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val a by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow), label = "screenEnter")
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            alpha = a.coerceIn(0f, 1f)
            val s = 0.94f + 0.06f * a
            scaleX = s; scaleY = s
        }
    ) { content() }
}
