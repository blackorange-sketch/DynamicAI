package com.dynamicai.launcher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Структура нашої теми
data class AppColors(
    // Базові
    val background: Color,
    val surface: Color,
    // Текст
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    // Акценти
    val accent: Color,
    val accentLight: Color,
    val accentDark: Color,
    // Семантичні
    val success: Color,
    val successLight: Color,
    val warning: Color,
    val danger: Color,
    // Спеціальні
    val purple: Color,
    val pink: Color,
    // Скло
    val glassBorder: Color,
    val glassBackground: Color,
    val glassBackgroundAlt: Color,
    // Фон меню категорій
    val backdropStart: Color,
    val backdropEnd: Color
)

// 1. Темна (базова, як зараз)
val GlassDarkTheme = AppColors(
    background = Color(0xFF0F0F0F),
    surface = Color(0xFF1A1A1A),
    textPrimary = Color.White,
    textSecondary = Color.White.copy(alpha = 0.6f),
    textMuted = Color(0xFFB0BEC5),
    accent = Color(0xFF64B5F6),
    accentLight = Color(0xFFB3E5FC),
    accentDark = Color(0xFF2196F3),
    success = Color(0xFF4CAF50),
    successLight = Color(0xFF81C784),
    warning = Color(0xFFFF9800),
    danger = Color(0xFFFF5252),
    purple = Color(0xFF9C27B0),
    pink = Color(0xFFE91E63),
    glassBorder = Color.White.copy(alpha = 0.15f),
    glassBackground = Color(0xE61A1A1A),
    glassBackgroundAlt = Color(0xCC0D0D0D),
    backdropStart = Color(0xFF14141E),
    backdropEnd = Color(0xFF050505)
)

// 2. Світла
val GlassLightTheme = AppColors(
    background = Color(0xFFF5F5F7),
    surface = Color.White,
    textPrimary = Color(0xFF1A1A1A),
    textSecondary = Color(0xFF1A1A1A).copy(alpha = 0.7f),
    textMuted = Color(0xFF6B7280),
    accent = Color(0xFF2196F3),
    accentLight = Color(0xFF64B5F6),
    accentDark = Color(0xFF1565C0),
    success = Color(0xFF2E7D32),
    successLight = Color(0xFF66BB6A),
    warning = Color(0xFFEF6C00),
    danger = Color(0xFFD32F2F),
    purple = Color(0xFF7B1FA2),
    pink = Color(0xFFC2185B),
    glassBorder = Color.Black.copy(alpha = 0.08f),
    glassBackground = Color(0xCCFFFFFF),
    glassBackgroundAlt = Color(0xE6F0F0F0),
    backdropStart = Color(0xFFE8E8EC),
    backdropEnd = Color(0xFFF5F5F7)
)

// 3. AMOLED (чистий чорний)
val GlassAmoledTheme = AppColors(
    background = Color.Black,
    surface = Color(0xFF0A0A0A),
    textPrimary = Color.White,
    textSecondary = Color.White.copy(alpha = 0.5f),
    textMuted = Color(0xFF808080),
    accent = Color(0xFF64B5F6),
    accentLight = Color(0xFFB3E5FC),
    accentDark = Color(0xFF2196F3),
    success = Color(0xFF4CAF50),
    successLight = Color(0xFF81C784),
    warning = Color(0xFFFF9800),
    danger = Color(0xFFFF5252),
    purple = Color(0xFF9C27B0),
    pink = Color(0xFFE91E63),
    glassBorder = Color.White.copy(alpha = 0.12f),
    glassBackground = Color(0xE6000000),
    glassBackgroundAlt = Color(0xCC000000),
    backdropStart = Color.Black,
    backdropEnd = Color.Black
)

// 4. Океан (синьо-бірюзова)
val GlassOceanTheme = AppColors(
    background = Color(0xFF0A1420),
    surface = Color(0xFF1A2738),
    textPrimary = Color(0xFFE0F7FA),
    textSecondary = Color(0xFFE0F7FA).copy(alpha = 0.6f),
    textMuted = Color(0xFF80DEEA),
    accent = Color(0xFF4DD0E1),
    accentLight = Color(0xFFB2EBF2),
    accentDark = Color(0xFF00ACC1),
    success = Color(0xFF26A69A),
    successLight = Color(0xFF80CBC4),
    warning = Color(0xFFFFA726),
    danger = Color(0xFFEF5350),
    purple = Color(0xFF7E57C2),
    pink = Color(0xFFEC407A),
    glassBorder = Color(0xFF4DD0E1).copy(alpha = 0.25f),
    glassBackground = Color(0xE60F2430),
    glassBackgroundAlt = Color(0xCC0A1420),
    backdropStart = Color(0xFF0A1420),
    backdropEnd = Color(0xFF04080C)
)

val LocalAppColors = staticCompositionLocalOf { GlassDarkTheme }

@Composable
fun DynamicAITheme(
    themeId: String = "glass_dark",
    content: @Composable () -> Unit
) {
    val colors = when (themeId) {
        "glass_light" -> GlassLightTheme
        "glass_amoled" -> GlassAmoledTheme
        "glass_ocean" -> GlassOceanTheme
        else -> GlassDarkTheme
    }

    CompositionLocalProvider(LocalAppColors provides colors) {
        content()
    }
}

object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}
