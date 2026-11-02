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
import com.dynamicai.launcher.ui.theme.AppTheme
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
        animationSpec = if (app != null) tween(animMs(240), easing = FastOutSlowInEasing) else snap(),
        label = "launchProgress"
    )
    if (progress <= 0f) return
    val last = remember { mutableStateOf<AppInfo?>(null) }
    if (app != null) last.value = app
    val shown = app ?: last.value
    // Кольори беремо з теми лаунчера, а не з іконки: так анімація завжди пасує до оформлення
    val color = AppTheme.colors.background
    val accent = AppTheme.colors.accent
    Canvas(Modifier.fillMaxSize()) {
        val screenCenter = Offset(size.width / 2f, size.height / 2f)
        val from = if (origin == Offset.Unspecified) screenCenter else origin
        val maxR = kotlin.math.hypot(size.width, size.height)
        drawCircle(color.copy(alpha = 0.97f), radius = maxR * progress, center = from)
        drawCircle(
            accent.copy(alpha = 0.35f * (1f - progress)),
            radius = maxR * progress, center = from,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
        )
        shown?.bitmap?.let { bmp ->
            val c = Offset(from.x + (screenCenter.x - from.x) * progress, from.y + (screenCenter.y - from.y) * progress)
            val side = (56.dp.toPx() * (1f + 0.9f * progress)).toInt().coerceAtLeast(1)
            drawImage(
                image = bmp.asImageBitmap(),
                dstOffset = IntOffset((c.x - side / 2f).toInt(), (c.y - side / 2f).toInt()),
                dstSize = IntSize(side, side),
                alpha = (1f - progress * 0.9f).coerceIn(0f, 1f)
            )
        }
    }
}
