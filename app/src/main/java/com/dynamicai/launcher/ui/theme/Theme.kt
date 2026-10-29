package com.dynamicai.launcher.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.dynamicai.launcher.data.SettingsStorage

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
    val backdropEnd: Color,
    // Світла тема? (для системних елементів Material)
    val isDark: Boolean = true,
    // Тінь карток: у світлій темі «скло» без тіні виглядає пласко
    val elevation: Dp = 0.dp
)

/** Єдині розміри інтерфейсу: використовуйте їх замість «магічних» чисел. */
object AppDims {
    // Радіуси
    val radiusS = 12.dp
    val radiusM = 20.dp
    val radiusL = 28.dp
    // Відступи
    val spaceXS = 4.dp
    val spaceS = 8.dp
    val spaceM = 12.dp
    val spaceL = 16.dp
    val spaceXL = 24.dp
    // Шрифти
    val textCaption = 11.sp
    val textSmall = 12.sp
    val textBody = 14.sp
    val textBodyLarge = 16.sp
    val textTitle = 20.sp
    val textHeadline = 24.sp
}

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
    glassBorder = Color.Black.copy(alpha = 0.10f),
    glassBackground = Color(0xF2FFFFFF),
    glassBackgroundAlt = Color(0xE6F3F3F6),
    backdropStart = Color(0xFFE8E8EC),
    backdropEnd = Color(0xFFF5F5F7),
    isDark = false,
    elevation = 3.dp
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

/** Material-схема, побудована з наших кольорів: діалоги, перемикачі, кнопки та слайдери автоматично слідують темі. */
private fun AppColors.toMaterialScheme(backgroundOverride: Color?): ColorScheme {
    val bg = backgroundOverride ?: background
    val onAccent = if (isDark) Color.Black else Color.White
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentDark,
        onPrimaryContainer = Color.White,
        secondary = accentLight,
        onSecondary = onAccent,
        tertiary = purple,
        background = bg,
        onBackground = textPrimary,
        surface = backgroundOverride ?: surface,
        onSurface = textPrimary,
        surfaceVariant = surface,
        onSurfaceVariant = textMuted,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surface,
        outline = glassBorder,
        outlineVariant = glassBorder,
        error = danger
    )
}

fun appColorsFor(themeId: String): AppColors = when (themeId) {
    "glass_light" -> GlassLightTheme
    "glass_amoled" -> GlassAmoledTheme
    "glass_ocean" -> GlassOceanTheme
    else -> GlassDarkTheme
}

/** Тема Material You: кольори з шпалер (Android 12+). На старих версіях — темна тема. */
private fun dynamicAppColors(ctx: Context, dark: Boolean): AppColors {
    val s = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    val fixed = if (dark) GlassDarkTheme else GlassLightTheme
    return AppColors(
        background = s.background,
        surface = s.surfaceContainer,
        textPrimary = s.onSurface,
        textSecondary = s.onSurface.copy(alpha = 0.7f),
        textMuted = s.onSurfaceVariant,
        accent = s.primary,
        accentLight = s.secondary,
        accentDark = s.primaryContainer,
        success = fixed.success,
        successLight = fixed.successLight,
        warning = fixed.warning,
        danger = fixed.danger,
        purple = s.tertiary,
        pink = fixed.pink,
        glassBorder = s.onSurface.copy(alpha = 0.14f),
        glassBackground = s.surfaceContainer.copy(alpha = 0.92f),
        glassBackgroundAlt = s.surfaceContainerLow.copy(alpha = 0.85f),
        backdropStart = s.surfaceContainerHigh,
        backdropEnd = s.surface,
        isDark = dark,
        elevation = if (dark) 0.dp else 3.dp
    )
}

@Composable
private fun resolveAppColors(themeId: String): AppColors {
    if (themeId != "glass_dynamic" || Build.VERSION.SDK_INT < 31) return appColorsFor(themeId)
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    return remember(ctx, dark) { dynamicAppColors(ctx, dark) }
}

/**
 * Єдина точка входу теми. Обгортає і наші кольори (AppTheme.colors), і MaterialTheme,
 * тож усі екрани, діалоги та оверлеї виглядають однаково.
 * @param backgroundOverride якщо задано — фон/поверхня Material (напр. прозорий, щоб було видно шпалери)
 */
@Composable
fun DynamicAITheme(
    themeId: String = "glass_dark",
    backgroundOverride: Color? = null,
    content: @Composable () -> Unit
) {
    val colors = resolveAppColors(themeId)
    val scheme = remember(colors, backgroundOverride) { colors.toMaterialScheme(backgroundOverride) }
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalContentColor provides colors.textPrimary
    ) {
        MaterialTheme(colorScheme = scheme) {
            // MaterialTheme не змінює LocalContentColor — задаємо ще раз усередині
            CompositionLocalProvider(LocalContentColor provides colors.textPrimary) {
                content()
            }
        }
    }
}

/**
 * Для Activity та оверлеїв, які не мають доступу до ViewModel: читає тему з налаштувань
 * і перечитує її щоразу, коли екран повертається на передній план (ON_RESUME).
 */
@Composable
fun StoredAppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var themeId by remember { mutableStateOf(runCatching { SettingsStorage(ctx).load().appTheme }.getOrDefault("glass_dark")) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                themeId = runCatching { SettingsStorage(ctx).load().appTheme }.getOrDefault(themeId)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DynamicAITheme(themeId = themeId, content = content)
}

object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}
