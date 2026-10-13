package com.dynamicai.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass стиль без Haze — градієнт + обводка + напівпрозорість.
 *
 * Візуально схоже на iOS 18 glass, але без backdrop blur.
 * Ефект досягається тим, що батьківський фон темний і елемент 
 * виглядає як напівпрозоре скло.
 */

data class LiquidGlassStyle(
    val cornerRadius: Dp = 24.dp,
    val tintAlpha: Float = 0.12f,
    val borderAlpha: Float = 0.4f,
    val borderWidth: Dp = 1.dp,
    val shadowAlpha: Float = 0.3f
)

fun Modifier.liquidGlass(
    style: LiquidGlassStyle = LiquidGlassStyle()
): Modifier = this
    .clip(RoundedCornerShape(style.cornerRadius))
    .background(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = style.tintAlpha),
                Color.White.copy(alpha = style.tintAlpha * 0.35f)
            )
        ),
        shape = RoundedCornerShape(style.cornerRadius)
    )
    .border(
        width = style.borderWidth,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = style.borderAlpha),
                Color.White.copy(alpha = style.borderAlpha * 0.25f)
            )
        ),
        shape = RoundedCornerShape(style.cornerRadius)
    )

/**
 * Темна версія — для елементів, під якими темний фон.
 */
fun Modifier.liquidGlassDark(
    cornerRadius: Dp = 24.dp
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xE61F1F1F),
                Color(0xCC0D0D0D)
            )
        ),
        shape = RoundedCornerShape(cornerRadius)
    )
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.5f),
                Color.White.copy(alpha = 0.1f)
            )
        ),
        shape = RoundedCornerShape(cornerRadius)
    )

/**
 * Простий варіант — тільки градієнт, без обводки.
 */
fun Modifier.liquidGlassFlat(
    cornerRadius: Dp = 24.dp,
    alpha: Float = 0.15f
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = alpha),
                Color.White.copy(alpha = alpha * 0.4f)
            )
        ),
        shape = RoundedCornerShape(cornerRadius)
    )
