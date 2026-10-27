package com.dynamicai.launcher.ui

import com.dynamicai.launcher.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
                Text("✕", color = AppTheme.colors.textSecondary, fontSize = 24.sp,
                    modifier = Modifier.padding(8.dp).clickable(onClick = onClose))
            }
            Spacer(Modifier.height(24.dp))

            CategoryCard("🤖", "ШІ та Голос", "LLM, TTS, Ранковий брифінг", AppTheme.colors.accentDark) { onCategorySelected("ai") }
            Spacer(Modifier.height(12.dp))
            CategoryCard("🎨", "Вигляд", "Фон, Док, Розміри, Стиль", AppTheme.colors.pink) { onCategorySelected("appearance") }
            Spacer(Modifier.height(12.dp))
            CategoryCard("🧩", "Модулі та Інструменти", "Wild Rift, OCR, Погода, Нотатки", AppTheme.colors.purple) { onCategorySelected("tools") }
            Spacer(Modifier.height(12.dp))
            CategoryCard("⚙️", "Система", "Shizuku, Дозволи, Bluetooth, Док", AppTheme.colors.success) { onCategorySelected("system") }
        }
    }
}

@Composable
private fun CategoryCard(icon: String, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
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
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) { Text(icon, fontSize = 24.sp) }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = AppTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = AppTheme.colors.textSecondary, fontSize = 12.sp)
        }
        Text("›", color = AppTheme.colors.textPrimary.copy(alpha = 0.4f), fontSize = 24.sp)
    }
}
