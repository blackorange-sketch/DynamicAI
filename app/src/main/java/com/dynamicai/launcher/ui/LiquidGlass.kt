package com.dynamicai.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dynamicai.launcher.ui.theme.AppTheme

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

@Composable
fun Modifier.liquidGlass(
    style: LiquidGlassStyle = LiquidGlassStyle()
): Modifier {
    val colors = AppTheme.colors
    return this
        .clip(RoundedCornerShape(style.cornerRadius))
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    colors.textPrimary.copy(alpha = style.tintAlpha),
                    colors.textPrimary.copy(alpha = style.tintAlpha * 0.35f)
                )
            ),
            shape = RoundedCornerShape(style.cornerRadius)
        )
        .border(
            width = style.borderWidth,
            brush = Brush.verticalGradient(
                colors = listOf(
                    colors.glassBorder.copy(alpha = style.borderAlpha),
                    colors.glassBorder.copy(alpha = style.borderAlpha * 0.25f)
                )
            ),
            shape = RoundedCornerShape(style.cornerRadius)
        )
}

/**
 * Темна версія — для елементів, під якими темний фон.
 */
@Composable
fun Modifier.liquidGlassDark(
    cornerRadius: Dp = 24.dp
): Modifier {
    val colors = AppTheme.colors
    return this
        .clip(RoundedCornerShape(cornerRadius))
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    colors.glassBackground,
                    colors.glassBackgroundAlt
                )
            ),
            shape = RoundedCornerShape(cornerRadius)
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    colors.glassBorder,
                    colors.glassBorder.copy(alpha = 0.3f)
                )
            ),
            shape = RoundedCornerShape(cornerRadius)
        )
}

/**
 * Простий варіант — тільки градієнт, без обводки.
 */
@Composable
fun Modifier.liquidGlassFlat(
    cornerRadius: Dp = 24.dp,
    alpha: Float = 0.15f
): Modifier {
    val colors = AppTheme.colors
    return this
        .clip(RoundedCornerShape(cornerRadius))
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    colors.textPrimary.copy(alpha = alpha),
                    colors.textPrimary.copy(alpha = alpha * 0.4f)
                )
            ),
            shape = RoundedCornerShape(cornerRadius)
        )
}
