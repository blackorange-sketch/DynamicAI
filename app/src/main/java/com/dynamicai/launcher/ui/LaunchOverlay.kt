package com.dynamicai.launcher.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import com.dynamicai.launcher.model.AppInfo

/**
 * Анімація запуску: з місця дотику розкривається коло кольору іконки,
 * а сама іконка їде до центру, збільшується й тане. Вимикається, коли швидкість анімацій = 0.
 */
@Composable
fun LaunchOverlay(app: AppInfo?, origin: Offset) {
    if (LocalAnimSpeed.current <= 0) return
    val progress by animateFloatAsState(
        targetValue = if (app != null) 1f else 0f,
        animationSpec = if (app != null) tween(animMs(300), easing = FastOutSlowInEasing) else snap(),
        label = "launchProgress"
    )
    if (progress <= 0f) return
    val last = remember { mutableStateOf<AppInfo?>(null) }
    if (app != null) last.value = app
    val shown = app ?: last.value
    val color = remember(shown?.packageName) {
        val bmp = shown?.bitmap
        if (bmp == null) Color(0xFF2A2A2A) else try {
            val p = Palette.from(bmp).generate()
            Color(p.getVibrantColor(p.getMutedColor(p.getDominantColor(0xFF2A2A2A.toInt()))))
        } catch (_: Throwable) { Color(0xFF2A2A2A) }
    }
    Canvas(Modifier.fillMaxSize()) {
        val screenCenter = Offset(size.width / 2f, size.height / 2f)
        val from = if (origin == Offset.Unspecified) screenCenter else origin
        val maxR = kotlin.math.hypot(size.width, size.height)
        drawCircle(color.copy(alpha = 0.94f), radius = maxR * progress, center = from)
        shown?.bitmap?.let { bmp ->
            val c = Offset(from.x + (screenCenter.x - from.x) * progress, from.y + (screenCenter.y - from.y) * progress)
            val side = (56.dp.toPx() * (1f + 1.8f * progress)).toInt().coerceAtLeast(1)
            drawImage(
                image = bmp.asImageBitmap(),
                dstOffset = IntOffset((c.x - side / 2f).toInt(), (c.y - side / 2f).toInt()),
                dstSize = IntSize(side, side),
                alpha = (1f - progress * 0.9f).coerceIn(0f, 1f)
            )
        }
    }
}
