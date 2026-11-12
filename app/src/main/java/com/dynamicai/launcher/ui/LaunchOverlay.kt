package com.dynamicai.launcher.ui

import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dynamicai.launcher.model.AppInfo
import com.dynamicai.launcher.ui.theme.AppTheme
import androidx.compose.ui.graphics.Color

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * Анімація запуску в стилі iOS: іконка на своєму місці перетворюється на «вікно»,
 * яке розтягується від розміру іконки до всього екрана, а кути поступово випрямляються.
 * Колір — з теми лаунчера. Вимикається, коли швидкість анімацій = 0.
 */
@Composable
fun LaunchOverlay(app: AppInfo?, origin: Offset) {
    if (LocalAnimSpeed.current <= 0) return
    val progressState = animateFloatAsState(
        targetValue = if (app != null) 1f else 0f,
        animationSpec = if (app != null) tween(animMs(320), easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)) else snap(),
        label = "launchProgress"
    )
    val visible by remember { androidx.compose.runtime.derivedStateOf { progressState.value > 0f } }
    if (!visible) return
    val last = remember { mutableStateOf<AppInfo?>(null) }
    if (app != null) last.value = app
    val shown = app ?: last.value
    val bg = AppTheme.colors.background
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val from = if (origin == Offset.Unspecified) Offset(w / 2f, h / 2f) else origin
        val start = 56.dp.toPx()
        val p = progressState.value
        // Прямокутник «вікна»: від квадрата навколо іконки до всього екрана
        val left = lerp(from.x - start / 2f, 0f, p)
        val top = lerp(from.y - start / 2f, 0f, p)
        val right = lerp(from.x + start / 2f, w, p)
        val bottom = lerp(from.y + start / 2f, h, p)
        val radius = lerp(14.dp.toPx(), 0f, p)
        // Заливка з'являється швидко, щоб іконка спочатку лишалась «на місці»
        val fill = (p * 3.5f).coerceIn(0f, 1f)
        // М'яка тінь навколо вікна: кілька напівпрозорих шарів, що розходяться й гаснуть
        for (i in 1..4) {
            val grow = i * 5.dp.toPx() * (1f - p * 0.7f)
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.11f * (1f - p) * fill.coerceAtLeast(0.4f)),
                topLeft = Offset(left - grow, top - grow + 3.dp.toPx()),
                size = Size(right - left + grow * 2, bottom - top + grow * 2),
                cornerRadius = CornerRadius(radius + grow, radius + grow)
            )
        }
        drawRoundRect(
            color = bg.copy(alpha = fill),
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            cornerRadius = CornerRadius(radius, radius)
        )
        shown?.bitmap?.let { bmp ->
            val side = (start * (1f + 0.35f * p)).toInt().coerceAtLeast(1)
            drawImage(
                image = bmp.asImageBitmap(),
                dstOffset = IntOffset((from.x - side / 2f).toInt(), (from.y - side / 2f).toInt()),
                dstSize = IntSize(side, side),
                alpha = (1f - p * 3f).coerceIn(0f, 1f)
            )
        }
    }
}
