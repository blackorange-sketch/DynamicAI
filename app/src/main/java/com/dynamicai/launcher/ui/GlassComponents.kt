package com.dynamicai.launcher.ui

import com.dynamicai.launcher.ui.theme.AppTheme
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.dynamicai.launcher.ui.theme.AppDims
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Скляна іконка-круг для підказок (дії, налаштування, контакти).
 */
@Composable
fun GlassCircleIcon(
    icon: ImageVector,
    tint: Color,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        AppTheme.colors.textPrimary.copy(alpha = 0.12f),
                        AppTheme.colors.textPrimary.copy(alpha = 0.03f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        AppTheme.colors.textPrimary.copy(alpha = 0.35f),
                        AppTheme.colors.glassBorder.copy(alpha = 0.7f)
                    )
                ),
                shape = CircleShape
            )
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/**
 * Glass-картка для налаштувань і панелей.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = AppDims.radiusM,
    content: @Composable ColumnScope.() -> Unit
) {
    val elevation = AppTheme.colors.elevation
    Column(
        modifier = modifier
            // Світла тема: м'яка тінь, щоб картки відділялись від фону
            .then(if (elevation > 0.dp) Modifier.shadow(elevation, RoundedCornerShape(cornerRadius), clip = false) else Modifier)
            // Картка плавно змінює висоту, коли додається/зникає вміст (статус моделі, помилки тощо)
            .animateContentSize()
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                // Темний фон, як у решті деталей лаунчера (наприклад, панель пошуку)
                brush = Brush.verticalGradient(
                    colors = listOf(
                        AppTheme.colors.glassBackground, // ~90% непрозорості
                        AppTheme.colors.glassBackgroundAlt  // ~80% непрозорості
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        AppTheme.colors.glassBorder, // Легка світла рамка
                        AppTheme.colors.glassBorder.copy(alpha = 0.3f)
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            )
            .padding(vertical = 6.dp),
        content = content
    )
}

/**
 * Кнопка glass-стилю (велика, для налаштувань).
 */
@Composable
fun GlassButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.verticalGradient(
                    if (accent) listOf(
                        AppTheme.colors.accent.copy(alpha = 0.35f),
                        AppTheme.colors.accentDark.copy(alpha = 0.15f)
                    ) else listOf(
                        AppTheme.colors.textPrimary.copy(alpha = 0.12f),
                        AppTheme.colors.textPrimary.copy(alpha = 0.04f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    if (accent) listOf(
                        AppTheme.colors.accent.copy(alpha = 0.6f),
                        AppTheme.colors.accent.copy(alpha = 0.2f)
                    ) else listOf(
                        AppTheme.colors.glassBorder,
                        AppTheme.colors.glassBorder.copy(alpha = 0.5f)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            label,
            color = if (accent) AppTheme.colors.accentLight else AppTheme.colors.textPrimary,
            fontSize = 15.sp
        )
    }
}

/**
 * Заголовок секції у glass-стилі.
 */
@Composable
fun GlassSectionHeader(text: String) {
    // Прибираємо початкові емодзі («🤖 ШІ» → «ШІ»): однаковий стиль без піктограм-емодзі
    val clean = text.dropWhile { !it.isLetterOrDigit() }.ifBlank { text }
    Text(
        clean.uppercase(),
        color = AppTheme.colors.accent,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp)
    )
}

/** Єдина кнопка закриття для всіх екранів (замість текстового «✕»). */
@Composable
fun GlassCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = AppTheme.colors.textSecondary) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Default.Close, contentDescription = "Закрити", tint = tint)
    }
}
