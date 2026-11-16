package com.dynamicai.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.Edition
import com.dynamicai.launcher.data.LauncherSettings
import com.dynamicai.launcher.llm.CloudLlm
import com.dynamicai.launcher.ui.theme.AppTheme

/** Налаштування відповідей і хмарної моделі (спільні для звичайної й хмарної версії). */
@Composable
internal fun ColumnScope.AnswersAndCloudSection(settings: LauncherSettings, onSettingsChange: (LauncherSettings) -> Unit) {
        GlassSectionHeader("Відповіді")
        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Мова відповіді", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LlmBackendChip("Українська", "uk", settings.answerLanguage) { onSettingsChange(settings.copy(answerLanguage = it)) }
                    LlmBackendChip("Як у питанні", "auto", settings.answerLanguage) { onSettingsChange(settings.copy(answerLanguage = it)) }
                    LlmBackendChip("English", "en", settings.answerLanguage) { onSettingsChange(settings.copy(answerLanguage = it)) }
                }
                Spacer(Modifier.height(10.dp))
                Text("Стиль відповіді", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LlmBackendChip("Коротко (1–3 речення)", "short", settings.answerStyle) { onSettingsChange(settings.copy(answerStyle = it)) }
                    LlmBackendChip("Звичайно", "normal", settings.answerStyle) { onSettingsChange(settings.copy(answerStyle = it)) }
                }
                Text("Макс. довжина відповіді (токени, застосується після перезапуску рушія)", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(128, 256, 512, 1024).forEach { n ->
                        LlmBackendChip(n.toString(), n.toString(), settings.maxResponseTokens.toString()) {
                            onSettingsChange(settings.copy(maxResponseTokens = it.toInt()))
                        }
                    }
                }
                SwitchRow(
                    label = "Автопошук в інтернеті (Вікіпедія + веб) для питань про світ",
                    checked = settings.autoSearch,
                    icon = Icons.Default.Settings,
                    tint = AppTheme.colors.accent
                ) { onSettingsChange(settings.copy(autoSearch = it)) }
                SwitchRow(
                    label = "Швидкі команди без моделі (ліхтарик, додатки, будильник…)",
                    checked = settings.fastCommands,
                    icon = Icons.Default.Settings,
                    tint = AppTheme.colors.success
                ) { onSettingsChange(settings.copy(fastCommands = it)) }
                SwitchRow(
                    label = "Режим агента в голосовому чаті (довгий промпт)",
                    checked = settings.useAgent,
                    icon = Icons.Default.Settings,
                    tint = AppTheme.colors.purple
                ) { onSettingsChange(settings.copy(useAgent = it)) }
            }
        }

        // --- Хмарна модель ---
        GlassSectionHeader("Хмарна модель (Gemini / Groq)")
        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                if (Edition.CLOUD_ONLY) {
                    Text(
                        "Ця версія працює лише з хмарними моделями: усі питання й керування телефоном — через Gemini, " +
                            "Groq — запасний. Потрібен хоча б один ключ.",
                        color = AppTheme.colors.textSecondary, fontSize = 12.sp
                    )
                    if (!CloudLlm.hasKey(settings)) {
                        Text("Ключ не вказано — асистент не відповідатиме.", color = AppTheme.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                } else {
                SwitchRow(
                    label = "Складні питання й пошук — через хмарну модель",
                    checked = settings.cloudEnabled,
                    icon = Icons.Default.Settings,
                    tint = AppTheme.colors.accent
                ) { onSettingsChange(settings.copy(cloudEnabled = it)) }
                Text(
                    "Лаунчер сам шукає в інтернеті, читає кращі сторінки й дає моделі джерела. Прості команди (ліхтарик, додатки…) " +
                        "і балачки лишаються локальними. Без мережі чи ключа працює локальна модель.",
                    color = AppTheme.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("Коли використовувати", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LlmBackendChip("Авто (факти й складні)", "auto", settings.cloudMode) { onSettingsChange(settings.copy(cloudMode = it)) }
                    LlmBackendChip("Усі питання", "always", settings.cloudMode) { onSettingsChange(settings.copy(cloudMode = it)) }
                }
                }
                TextFieldRow("Gemini API-ключ (aistudio.google.com/apikey)", settings.geminiKey, "AIza…", secret = true) {
                    onSettingsChange(settings.copy(geminiKey = it))
                }
                TextFieldRow("Модель Gemini", settings.geminiModel, "gemini-3.5-flash-lite") {
                    onSettingsChange(settings.copy(geminiModel = it))
                }
                TextFieldRow("Groq API-ключ — запасний (console.groq.com/keys)", settings.groqKey, "gsk_…", secret = true) {
                    onSettingsChange(settings.copy(groqKey = it))
                }
                TextFieldRow("Модель Groq", settings.groqModel, "openai/gpt-oss-120b") {
                    onSettingsChange(settings.copy(groqModel = it))
                }
                CloudTestRow(settings)
                Text(
                    "Приватність: назовні йде лише питання, кілька останніх реплік і публічні уривки знайдених сторінок. " +
                        "Контакти, нотатки, повідомлення й текст екрана не надсилаються. На безкоштовному рівні Google може " +
                        "використовувати запити для покращення продуктів — не питайте там особистого.",
                    color = AppTheme.colors.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
}
