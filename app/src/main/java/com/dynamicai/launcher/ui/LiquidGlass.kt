package com.dynamicai.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

data class LiquidGlassStyle(
    val cornerRadius: Dp = 24.dp,
    val tintAlpha: Float = 0.12f,
    val borderAlpha: Float = 0.4f,
    val borderWidth: Dp = 1.dp,
    val shadowAlpha: Float = 0.3f
)

fun Modifier.liquidGlass(
    style: LiquidGlassStyle = LiquidGlassStyle()
): Modifier = composed {
    val backdrop = LocalBackdrop.current
    val shape = RoundedCornerShape(style.cornerRadius)
    
    this
        .then(
            if (backdrop != null) {
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        blur(24.dp.toPx())
                        lens(refractionHeight = 12.dp.toPx(), refractionAmount = 16.dp.toPx())
                        vibrancy()
                    }
                )
            } else {
                Modifier
                    .clip(shape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = style.tintAlpha),
                                Color.White.copy(alpha = style.tintAlpha * 0.35f)
                            )
                        ),
                        shape = shape
                    )
            }
        )
        .border(
            width = style.borderWidth,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = style.borderAlpha),
                    Color.White.copy(alpha = style.borderAlpha * 0.25f)
                )
            ),
            shape = shape
        )
}

fun Modifier.liquidGlassDark(
    cornerRadius: Dp = 24.dp
): Modifier = composed {
    val backdrop = LocalBackdrop.current
    val shape = RoundedCornerShape(cornerRadius)
    
    this
        .then(
            if (backdrop != null) {
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        blur(16.dp.toPx())
                        lens(refractionHeight = 8.dp.toPx(), refractionAmount = 12.dp.toPx())
                        vibrancy()
                    }
                )
            } else {
                Modifier
                    .clip(shape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xE61F1F1F), Color(0xCC0D0D0D))
                        ),
                        shape = shape
                    )
            }
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.5f),
                    Color.White.copy(alpha = 0.1f)
                )
            ),
            shape = shape
        )
}

fun Modifier.liquidGlassFlat(
    cornerRadius: Dp = 24.dp,
    alpha: Float = 0.15f
): Modifier = composed {
    val backdrop = LocalBackdrop.current
    val shape = RoundedCornerShape(cornerRadius)
    
    this
        .then(
            if (backdrop != null) {
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        blur(20.dp.toPx())
                        vibrancy()
                    }
                )
            } else {
                Modifier
                    .clip(shape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = alpha),
                                Color.White.copy(alpha = alpha * 0.4f)
                            )
                        ),
                        shape = shape
                    )
            }
        )
}
