package com.dynamicai.launcher.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsMenuScreen(
    onCategorySelected: (String) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF14141E), Color(0xFF050505))))
    ) {
        // Фонові розмиті плями (Liquid Glass)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFF2196F3).copy(alpha = 0.08f), Color.Transparent)),
                radius = 600f,
                center = Offset(size.width * 0.8f, size.height * 0.2f)
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFE91E63).copy(alpha = 0.08f), Color.Transparent)),
                radius = 600f,
                center = Offset(size.width * 0.2f, size.height * 0.8f)
            )
        }

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Налаштування", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("✕", color = Color.White.copy(alpha = 0.6f), fontSize = 24.sp,
                    modifier = Modifier.padding(8.dp).clickable(onClick = onClose))
            }
            Spacer(Modifier.height(24.dp))

            CategoryCard("🤖", "ШІ та Голос", "LLM, TTS, Ранковий брифінг", Color(0xFF2196F3)) { onCategorySelected("ai") }
            Spacer(Modifier.height(12.dp))
            CategoryCard("🎨", "Вигляд", "Фон, Док, Розміри, Стиль", Color(0xFFE91E63)) { onCategorySelected("appearance") }
            Spacer(Modifier.height(12.dp))
            CategoryCard("🧩", "Модулі та Інструменти", "Wild Rift, OCR, Погода, Нотатки", Color(0xFF9C27B0)) { onCategorySelected("tools") }
            Spacer(Modifier.height(12.dp))
            CategoryCard("⚙️", "Система", "Shizuku, Дозволи, Bluetooth, Док", Color(0xFF4CAF50)) { onCategorySelected("system") }
        }
    }
}

@Composable
private fun CategoryCard(icon: String, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            // Напівпрозорий фон + рамка для ефекту скла
            .background(Color.White.copy(alpha = 0.05f))
            .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
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
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        Text("›", color = Color.White.copy(alpha = 0.4f), fontSize = 24.sp)
    }
}
