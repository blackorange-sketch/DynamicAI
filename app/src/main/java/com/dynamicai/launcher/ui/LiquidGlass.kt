package com.dynamicai.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeStyle

/**
 * Liquid Glass — стиль iOS 18.
 *
 * Ефект:
 *  - Backdrop blur (розмиття того, що під елементом) через Haze
 *  - Напівпрозорий градієнт
 *  - Тонка біла обводка зверху
 */

data class LiquidGlassStyle(
    val cornerRadius: Dp = 24.dp,
    val blurRadius: Dp = 30.dp,
    val tintAlpha: Float = 0.15f,
    val borderAlpha: Float = 0.4f,
    val borderWidth: Dp = 1.dp
)

/**
 * Повний glass-ефект з backdrop blur.
 *
 * @param hazeState — HazeState з `Modifier.hazeSource` на батьківському контейнері.
 */
fun Modifier.liquidGlass(
    hazeState: HazeState,
    style: LiquidGlassStyle = LiquidGlassStyle()
): Modifier = this
    .clip(RoundedCornerShape(style.cornerRadius))
    .hazeEffect(
        state = hazeState,
        style = HazeStyle(
            backgroundColor = Color.Black,
            tint = dev.chrisbanes.haze.HazeTint(Color.White.copy(alpha = style.tintAlpha)),
            blurRadius = style.blurRadius,
            noiseFactor = 0.12f
        )
    )
    .border(
        width = style.borderWidth,
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = style.borderAlpha),
                Color.White.copy(alpha = style.borderAlpha * 0.25f)
            )
        ),
        shape = RoundedCornerShape(style.cornerRadius)
    )

/**
 * Спрощений варіант — без Haze (градієнт + обводка).
 * Працює завжди, не залежить від backdrop.
 */
fun Modifier.liquidGlassSimple(
    cornerRadius: Dp = 24.dp,
    tintAlpha: Float = 0.15f,
    borderAlpha: Float = 0.4f
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = tintAlpha),
                Color.White.copy(alpha = tintAlpha * 0.4f)
            )
        ),
        shape = RoundedCornerShape(cornerRadius)
    )
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = borderAlpha),
                Color.White.copy(alpha = borderAlpha * 0.25f)
            )
        ),
        shape = RoundedCornerShape(cornerRadius)
    )
