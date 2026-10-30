package com.dynamicai.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.dynamicai.launcher.ui.theme.AppTheme

/**
 * Єдина підкладка під іконку для всього лаунчера (список, шторка, папки, док, пошук).
 * Розмір підкладки = розмір іконки + 12dp, кружок зі скляним градієнтом і тонкою рамкою.
 * innerModifier застосовується після рамки (для pressable: масштабується лише вміст).
 */
@Composable
fun IconPlate(
    iconSizeDp: Int,
    modifier: Modifier = Modifier,
    innerModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val radius = (iconSizeDp / 2 + 6).dp
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size((iconSizeDp + 12).dp)
            .clip(RoundedCornerShape(radius))
            .background(
                Brush.verticalGradient(
                    listOf(
                        AppTheme.colors.textPrimary.copy(alpha = 0.10f),
                        AppTheme.colors.textPrimary.copy(alpha = 0.03f)
                    )
                )
            )
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        AppTheme.colors.textPrimary.copy(alpha = 0.22f),
                        AppTheme.colors.textPrimary.copy(alpha = 0.05f)
                    )
                ),
                shape = RoundedCornerShape(radius)
            )
            .then(innerModifier),
        content = content
    )
}
