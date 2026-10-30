package com.dynamicai.launcher.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.dynamicai.launcher.ui.theme.AppTheme

/** Єдиний вигляд картки віджета (скло). Кольори беруться з поточної теми. */
val WidgetShape = RoundedCornerShape(24.dp)

fun Modifier.widgetCard(): Modifier = composed {
    this
        .clip(WidgetShape)
        .background(Brush.verticalGradient(listOf(AppTheme.colors.glassBackground, AppTheme.colors.glassBackgroundAlt)))
        .border(0.5.dp, AppTheme.colors.glassBorder, WidgetShape)
}
