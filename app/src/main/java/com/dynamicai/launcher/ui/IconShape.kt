package com.dynamicai.launcher.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Форма іконок додатків: "original" | "circle" | "squircle" | "square". */
val LocalIconShape = compositionLocalOf { "original" }

@Composable
fun Modifier.iconShape(): Modifier = when (LocalIconShape.current) {
    "circle" -> this.clip(CircleShape)
    "squircle" -> this.clip(RoundedCornerShape(28))
    "square" -> this.clip(RoundedCornerShape(6.dp))
    else -> this
}
