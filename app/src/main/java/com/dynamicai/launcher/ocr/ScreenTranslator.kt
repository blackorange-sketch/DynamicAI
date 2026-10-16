package com.dynamicai.launcher.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import com.dynamicai.launcher.llm.LlmEngine
import com.dynamicai.launcher.system.ScreenshotController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TranslatedBlock(
    val originalText: String,
    val translatedText: String,
    val bounds: Rect,
    val confidence: Int
)

class ScreenTranslator(
    private val context: Context,
    private val llm: LlmEngine?
) {

    companion object {
        private const val TAG = "ScreenTranslator"
        private const val BATCH_SIZE = 25
    }

    private val screenshot = ScreenshotController(context)
    private val ocr = OcrEngine(context)

    private val langName = mapOf(
        "en" to "англійську",
        "uk" to "українську",
        "pl" to "польську",
        "de" to "німецьку",
        "ru" to "російську"
    )

    suspend fun captureAndTranslate(
        ocrLangs: String = "ukr+eng",
        targetLang: String = "en",
        onProgress: (stage: String) -> Unit = {}
    ): List<TranslatedBlock> = withContext(Dispatchers.IO) {
        try {
            // Автозавантаження OCR-мов
            val modelMgr = TessModelManager(context)
            for (lang in ocrLangs.split("+")) {
                if (!modelMgr.isDownloaded(lang)) {
                    onProgress("Завантажую OCR $lang…")
                    val ok = modelMgr.download(lang) { d, t ->
                        if (t > 0) onProgress("Завантажую $lang: ${d * 100 / t}%")
                    }
                    if (!ok) {
                        Log.e(TAG, "Failed to download $lang")
                        return@withContext emptyList()
                    }
                }
            }

            onProgress("Роблю скріншот…")
            val file = screenshot.takeScreenshot()
            if (file == null) {
                Log.e(TAG, "Screenshot failed: ${screenshot.lastError}")
                return@withContext emptyList()
            }

            onProgress("Розпізнаю текст…")
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            file.delete()
            if (bmp == null) {
                Log.e(TAG, "Failed to decode screenshot")
                return@withContext emptyList()
            }

            val blocks = ocr.recognize(bmp, ocrLangs)
            Log.d(TAG, "OCR: ${blocks.size} blocks")
            bmp.recycle()
            if (blocks.isEmpty()) return@withContext emptyList()

            // Фільтруємо: довжина >= 2, conf > 40
            val valid = blocks.filter {
                it.text.trim().length >= 2 && (it.confidence == 0 || it.confidence > 40)
            }
            Log.d(TAG, "Valid: ${valid.size} / ${blocks.size}")
            if (valid.isEmpty()) return@withContext emptyList()

            // Перевірка LLM
            if (llm == null || !llm.isReady) {
                Log.e(TAG, "LLM not ready")
                return@withContext emptyList()
            }

            onProgress("Перекладаю ${valid.size} блоків…")
            val translations = mutableListOf<String>()

            // Батчинг: по 25 блоків
            for (batch in valid.chunked(BATCH_SIZE)) {
                val numbered = batch.mapIndexed { i, b -> "${i + 1}. ${b.text}" }.joinToString("\n")
                val targetName = langName[targetLang] ?: targetLang
                val prompt = "Переклади КОЖЕН рядок на $targetName. " +
                        "Зберігай нумерацію. Виведи ТІЛЬКИ переклад у форматі:\n" +
                        "1. <переклад>\n2. <переклад>\n...\n\nТекст:\n$numbered"

                val response = try {
                    llm.complete(prompt)?.trim()
                } catch (t: Throwable) {
                    Log.e(TAG, "LLM failed: ${t.message}")
                    null
                }

                if (response.isNullOrBlank()) {
                    Log.w(TAG, "Empty LLM response for batch")
                    translations.addAll(List(batch.size) { "" })
                    continue
                }

                // Парсимо відповідь LLM у список рядків
                val parsed = parseNumbered(response)
                translations.addAll(parsed)
            }

            // Складаємо результат
            val result = mutableListOf<TranslatedBlock>()
            for ((i, block) in valid.withIndex()) {
                val tr = translations.getOrNull(i)?.trim().orEmpty()
                if (tr.isBlank()) continue
                // Пропускаємо якщо переклад == оригінал (LLM не переклав)
                if (tr.equals(block.text, ignoreCase = true)) continue
                result.add(
                    TranslatedBlock(
                        originalText = block.text,
                        translatedText = tr,
                        bounds = block.bounds,
                        confidence = block.confidence
                    )
                )
            }

            Log.d(TAG, "Translated ${result.size} / ${valid.size}")
            onProgress("Готово")
            result
        } catch (t: Throwable) {
            Log.e(TAG, "captureAndTranslate failed: ${t.message}", t)
            emptyList()
        }
    }

    /** Парсить "1. Hello
2. World" → ["Hello", "World"] */
    private fun parseNumbered(text: String): List<String> {
        val re = Regex("^\\s*(\\d+)[.):\\]]\\s*(.+)$")
        val map = mutableMapOf<Int, String>()
        text.lines().forEach { line ->
            val m = re.find(line.trim())
            if (m != null) {
                val idx = m.groupValues[1].toIntOrNull() ?: return@forEach
                map[idx] = m.groupValues[2].trim()
            }
        }
        if (map.isEmpty()) return emptyList()
        val max = map.keys.maxOrNull() ?: 0
        return (1..max).map { map[it] ?: "" }
    }
}
