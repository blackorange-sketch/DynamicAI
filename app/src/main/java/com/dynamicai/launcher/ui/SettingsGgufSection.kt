package com.dynamicai.launcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dynamicai.launcher.AppListViewModel
import com.dynamicai.launcher.data.LauncherSettings
import com.dynamicai.launcher.llm.LlamaCppNative
import com.dynamicai.launcher.llm.LlmModel
import com.dynamicai.launcher.llm.ModelDownloader
import com.dynamicai.launcher.llm.ModelRepository
import com.dynamicai.launcher.ui.theme.AppTheme

/** Налаштування → Моделі → GGUF (llama.cpp): готові моделі, імпорт власного файлу, потоки й контекст. */
@Composable
internal fun GgufModelsCard(
    vm: AppListViewModel,
    settings: LauncherSettings,
    onSettingsChange: (LauncherSettings) -> Unit,
    downloadedIds: Set<String>,
    onDelete: (LlmModel) -> Unit
) {
    val progress = vm.downloadProgress.collectAsStateWithLifecycle().value
    val importState by vm.ggufImport.collectAsStateWithLifecycle()
    val llmReady by vm.llmReady.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importGguf(uri)
    }
    // downloadedIds змінюється після завантаження/імпорту/видалення — тоді ж перечитуємо список
    val models = remember(downloadedIds) { ModelRepository.AVAILABLE_MODELS.filter { it.engine == "gguf" } }
    val cpuOk = remember { LlamaCppNative.cpuSupported }

    GlassCard {
        Column(Modifier.padding(16.dp)) {
            Text("GGUF моделі (llama.cpp)", color = AppTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "Будь-яка модель з Hugging Face у форматі .gguf. Рахує на CPU.",
                color = AppTheme.colors.textSecondary, fontSize = 11.sp
            )
            if (!cpuOk) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Процесор цього пристрою не підтримує потрібні інструкції (i8mm) — llama.cpp не запуститься.",
                    color = AppTheme.colors.danger, fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(8.dp))

            models.forEach { m ->
                val isDownloaded = m.id in downloadedIds
                val isCurrent = vm.currentModelId == m.id
                val isDownloading = vm.downloadingModelId == m.id && progress is ModelDownloader.Progress.Downloading
                val imported = m.url.isEmpty()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isDownloading && (isDownloaded || vm.downloadingModelId == null)) {
                            if (isDownloaded) { if (!isCurrent) vm.setCurrentModel(m.id) } else vm.downloadModel(m)
                        }
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name, color = AppTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        when {
                            isDownloaded -> Text(
                                (if (isCurrent) "✓ Активна" else "✓ ${if (imported) "Імпортовано" else "Завантажено"} • Обрати") +
                                    " • ${formatModelSize(m.sizeBytes)}",
                                color = if (isCurrent) AppTheme.colors.success else AppTheme.colors.accent, fontSize = 11.sp
                            )
                            isDownloading -> {
                                val p = progress as ModelDownloader.Progress.Downloading
                                LinearProgressIndicator(
                                    progress = { p.percent / 100f },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 6.dp),
                                    color = AppTheme.colors.accent
                                )
                                Text("${p.percent}% (${p.downloadedMb} / ${p.totalMb} МБ)", color = AppTheme.colors.accent, fontSize = 10.sp)
                            }
                            else -> Text("${formatModelSize(m.sizeBytes)} • Натисніть для завантаження", color = AppTheme.colors.textSecondary, fontSize = 11.sp)
                        }
                        if (vm.downloadingModelId == m.id && progress is ModelDownloader.Progress.Error) {
                            Text("Помилка: ${progress.message}", color = AppTheme.colors.danger, fontSize = 11.sp)
                            TextButton(onClick = { vm.clearDownloadError() }) {
                                Text("Сховати", color = AppTheme.colors.textMuted, fontSize = 11.sp)
                            }
                        }
                    }
                    if (isDownloaded) {
                        IconButton(onClick = { onDelete(m) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Видалити", tint = AppTheme.colors.danger)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                enabled = importState?.startsWith("Копіювання") != true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = AppTheme.colors.accent)
                Spacer(Modifier.width(6.dp))
                Text("Імпортувати свій .gguf", color = AppTheme.colors.accent)
            }
            importState?.let { st ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        st, fontSize = 11.sp, modifier = Modifier.weight(1f).padding(top = 4.dp),
                        color = when {
                            st.startsWith("✗") -> AppTheme.colors.danger
                            st.startsWith("✓") -> AppTheme.colors.success
                            else -> AppTheme.colors.textSecondary
                        }
                    )
                    if (!st.startsWith("Копіювання")) {
                        TextButton(onClick = { vm.clearGgufImport() }) {
                            Text("OK", color = AppTheme.colors.textMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
            Text(
                "Порада: Q4_K_M або Q4_0, до 4B параметрів. Моделі з «thinking» (Qwen3) працюють у режимі /no_think.",
                color = AppTheme.colors.textMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(12.dp))
            Text("Потоки CPU", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(2, 4, 6, 8).forEach { n ->
                    LlmBackendChip("$n", n.toString(), settings.llamaThreads.toString()) {
                        onSettingsChange(settings.copy(llamaThreads = n))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Контекст (токенів)", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(2048 to "2K", 4096 to "4K", 8192 to "8K").forEach { (n, label) ->
                    LlmBackendChip(label, n.toString(), settings.llamaContext.toString()) {
                        onSettingsChange(settings.copy(llamaContext = n))
                    }
                }
            }
            Text(
                "Зміни застосуються після перезапуску рушія. 4 потоки — оптимально для Snapdragon 8 Gen 2.",
                color = AppTheme.colors.textMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp)
            )

            // Діагностика активної GGUF-моделі
            if (llmReady && vm.currentModelId.startsWith("gguf-")) {
                val info = remember(vm.currentModelId) { vm.llm.llamaInfo() }
                if (info.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(info.trim(), color = AppTheme.colors.textMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
