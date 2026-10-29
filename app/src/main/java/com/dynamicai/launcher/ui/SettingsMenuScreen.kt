package com.dynamicai.launcher.ui

import com.dynamicai.launcher.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import com.dynamicai.launcher.ui.theme.AppDims
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsMenuScreen(
    onCategorySelected: (String) -> Unit,
    onClose: () -> Unit,
    backgroundAlpha: Float = 0.8f
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.backdropStart.copy(alpha = backgroundAlpha))
    ) {
        // Фонові розмиті плями (Liquid Glass)
        

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Налаштування", color = AppTheme.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                GlassCloseButton(onClick = onClose)
            }
            Spacer(Modifier.height(24.dp))

            CategoryCard(Icons.Default.Face, "ШІ та Голос", "LLM, TTS, Ранковий брифінг", AppTheme.colors.accentDark) { onCategorySelected("ai") }
            Spacer(Modifier.height(12.dp))
            CategoryCard(Icons.Default.Edit, "Вигляд", "Фон, Док, Розміри, Стиль", AppTheme.colors.pink) { onCategorySelected("appearance") }
            Spacer(Modifier.height(12.dp))
            CategoryCard(Icons.Default.Build, "Модулі та Інструменти", "Wild Rift, OCR, Погода, Нотатки", AppTheme.colors.purple) { onCategorySelected("tools") }
            Spacer(Modifier.height(12.dp))
            CategoryCard(Icons.Default.Settings, "Система", "Shizuku, Дозволи, Bluetooth, Док", AppTheme.colors.success) { onCategorySelected("system") }
        }
    }
}

@Composable
private fun CategoryCard(icon: ImageVector, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDims.radiusM))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        AppTheme.colors.glassBackground,
                        AppTheme.colors.glassBackgroundAlt
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        AppTheme.colors.glassBorder,
                        AppTheme.colors.glassBorder.copy(alpha = 0.3f)
                    )
                ),
                shape = RoundedCornerShape(AppDims.radiusM)
            )
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = AppTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = AppTheme.colors.textSecondary, fontSize = 12.sp)
        }
        Text("›", color = AppTheme.colors.textPrimary.copy(alpha = 0.4f), fontSize = 24.sp)
    }
}
