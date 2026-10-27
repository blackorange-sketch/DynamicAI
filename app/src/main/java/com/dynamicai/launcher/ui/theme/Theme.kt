package com.dynamicai.launcher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

// 1. Описуємо структуру нашої теми
data class AppColors(
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val glassBorder: Color,
    val glassBackground: Color
)

// 2. Створюємо набори кольорів (наші майбутні теми)
val GlassDarkTheme = AppColors(
    background = Color(0xFF0F0F0F),
    surface = Color(0xFF1A1A1A),
    textPrimary = Color.White,
    textSecondary = Color.White.copy(alpha = 0.6f),
    accent = Color(0xFF64B5F6),
    glassBorder = Color.White.copy(alpha = 0.15f),
    glassBackground = Color(0xCC1A1A1A)
)

val GlassLightTheme = AppColors(
    background = Color(0xFFF0F0F0),
    surface = Color.White,
    textPrimary = Color.Black,
    textSecondary = Color.Black.copy(alpha = 0.6f),
    accent = Color(0xFF2196F3),
    glassBorder = Color.Black.copy(alpha = 0.1f),
    glassBackground = Color(0xCCFFFFFF)
)

// 3. Поточний провайдер теми (за замовчуванням - темна)
val LocalAppColors = staticCompositionLocalOf { GlassDarkTheme }

@Composable
fun DynamicAITheme(
    themeId: String = "glass_dark",
    content: @Composable () -> Unit
) {
    val colors = when (themeId) {
        "glass_light" -> GlassLightTheme
        else -> GlassDarkTheme
    }

    CompositionLocalProvider(LocalAppColors provides colors) {
        // Тут ми можемо в майбутньому підключити MaterialTheme
        content()
    }
}

// 4. Зручний об'єкт для доступу до кольорів
object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}
