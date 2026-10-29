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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
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

/** Запис для пошуку по налаштуваннях: відкриває потрібний розділ (і вкладку для "ai:*"). */
private data class SettingEntry(val title: String, val section: String, val category: String, val keywords: String)

private val SETTING_ENTRIES = listOf(
    SettingEntry("Моделі LLM", "ШІ · Моделі", "ai:models", "модель llm gemma qwen mnn litert завантажити видалити вибрати"),
    SettingEntry("Прискорювач (GPU/CPU) і температура", "ШІ · Моделі", "ai:models", "gpu cpu backend прискорювач температура auto"),
    SettingEntry("LiteRT GPU (експериментально)", "ШІ · Моделі", "ai:models", "float32 float16 mtp кеш перезапуск рушія агент точність speculative"),
    SettingEntry("Режим агента в голосовому чаті", "ШІ · Моделі", "ai:models", "агент інструменти промпт голосовий чат"),
    SettingEntry("Промпти для рушіїв", "ШІ · Моделі", "ai:models", "промпт шаблон prompt template"),
    SettingEntry("Плаваючий асистент", "ШІ · Асистент", "ai:assistant", "куля орб оверлей плаваючий jarvis"),
    SettingEntry("Ранковий брифінг", "ШІ · Асистент", "ai:assistant", "ранок брифінг будильник прокидання"),
    SettingEntry("Використовувати LLM для команд", "ШІ · Асистент", "ai:assistant", "команди llm розуміння"),
    SettingEntry("Озвучення відповідей (TTS)", "ШІ · Голос", "ai:voice", "голос озвучення швидкість тон tts говорити"),
    SettingEntry("Активація фразою", "ШІ · Голос", "ai:voice", "wake word фраза слухання hey мікрофон"),
    SettingEntry("Тема оформлення", "Вигляд", "appearance", "тема темна світла amoled океан material you кольори стиль"),
    SettingEntry("Фон лаунчера", "Вигляд", "appearance", "фон шпалери розмиття прозорість фото"),
    SettingEntry("Розміри іконок і шрифтів", "Вигляд", "appearance", "розмір іконки шрифт підписи док меню"),
    SettingEntry("Плаваючий док", "Вигляд", "appearance", "док оверлей смужка край"),
    SettingEntry("Застосунки за замовчуванням", "Модулі", "tools", "браузер музика пошта email карти камера дзвінки смс"),
    SettingEntry("Нотатки", "Модулі", "tools", "нотатки записки"),
    SettingEntry("Макроси", "Модулі", "tools", "макрос автоматизація"),
    SettingEntry("Віджети та інструменти", "Модулі", "tools", "віджет wild rift ocr переклад модулі"),
    SettingEntry("Погода", "Модулі", "tools", "погода місто gps температура"),
    SettingEntry("Дозволи", "Система", "system", "дозволи bluetooth доступ"),
    SettingEntry("Shizuku", "Система", "system", "shizuku adb права"),
    SettingEntry("Енергозбереження", "Система", "system", "батарея fps пауза шпалери екран")
)

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
            Spacer(Modifier.height(AppDims.spaceL))

            // Пошук по налаштуваннях
            var query by remember { mutableStateOf("") }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppDims.radiusM))
                    .background(AppTheme.colors.glassBackground)
                    .border(1.dp, AppTheme.colors.glassBorder, RoundedCornerShape(AppDims.radiusM))
                    .padding(horizontal = AppDims.spaceL, vertical = AppDims.spaceM),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = AppTheme.colors.textMuted)
                Spacer(Modifier.width(AppDims.spaceM))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Пошук у налаштуваннях…", color = AppTheme.colors.textMuted, fontSize = AppDims.textBodyLarge)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = AppTheme.colors.textPrimary, fontSize = AppDims.textBodyLarge),
                        cursorBrush = SolidColor(AppTheme.colors.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) {
                    GlassCloseButton(onClick = { query = "" })
                }
            }
            Spacer(Modifier.height(AppDims.spaceL))

            val q = query.trim().lowercase()
            if (q.isNotEmpty()) {
                val results = SETTING_ENTRIES.filter {
                    it.title.lowercase().contains(q) || it.keywords.contains(q) || it.section.lowercase().contains(q)
                }
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (results.isEmpty()) {
                        Text("Нічого не знайдено", color = AppTheme.colors.textMuted, fontSize = AppDims.textBody)
                    }
                    results.forEach { e ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AppDims.radiusS))
                                .clickable { onCategorySelected(e.category) }
                                .padding(horizontal = AppDims.spaceS, vertical = AppDims.spaceM)
                        ) {
                            Text(e.title, color = AppTheme.colors.textPrimary, fontSize = AppDims.textBodyLarge, fontWeight = FontWeight.Medium)
                            Text(e.section, color = AppTheme.colors.accent, fontSize = AppDims.textSmall)
                        }
                    }
                }
                return@Column
            }

            CategoryCard(Icons.Default.Face, "ШІ та Голос", "Асистент, Моделі, Голос", AppTheme.colors.accentDark) { onCategorySelected("ai") }
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
