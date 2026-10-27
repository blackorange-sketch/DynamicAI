package com.dynamicai.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsMainScreen(onBack: () -> Unit) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF14141E), Color(0xFF050505))))
    ) {
        if (selectedCategory == null) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Налаштування", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("←", color = Color.White.copy(alpha = 0.6f), fontSize = 24.sp, modifier = Modifier.clickable(onClick = onBack))
                }
                Spacer(Modifier.height(24.dp))

                // Категорії
                CategoryCard("🤖", "ШІ та Голос", "LLM, TTS, Ранковий брифінг", Color(0xFF2196F3)) { selectedCategory = "ai" }
                CategoryCard("🎨", "Вигляд", "Фон, Док, Розміри", Color(0xFFE91E63)) { selectedCategory = "appearance" }
                CategoryCard("🧩", "Модулі та Інструменти", "Wild Rift, OCR, Погода, Нотатки", Color(0xFF9C27B0)) { selectedCategory = "modules" }
                CategoryCard("⚙️", "Система", "Shizuku, Bluetooth, Плаваючий асистент", Color(0xFF4CAF50)) { selectedCategory = "system" }
            }
        } else {
            // Перенаправляємо на існуючий екран, але з фільтром по категорії
            // Оскільки SettingsScreen дуже великий, ми просто покажемо його з параметром
            // (це тимчасове рішення, поки ми не розділимо SettingsScreen на файли)
            com.dynamicai.launcher.ui.SettingsScreen(
                vm = androidx.lifecycle.viewmodel.compose.viewModel(),
                onBack = { selectedCategory = null },
                initialCategory = selectedCategory
            )
        }
    }
}

@Composable
fun CategoryCard(icon: String, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(0.5.dp, color.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
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
