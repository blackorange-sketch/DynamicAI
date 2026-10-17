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
        private const val BATCH_SIZE = 10
    }

    private val screenshot = ScreenshotController(context)
    private val ocr = OcrEngine(context)
    private val mlkit = MlKitTranslator(context)

    private val langName = mapOf(
        "en" to "англійську",
        "uk" to "українську",
        "pl" to "польську",
        "de" to "німецьку",
        "ru" to "російську"
    )

    suspend fun captureAndTranslate(
        ocrLangs: String = "ukr+eng",
        targetLang: String = "uk",
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
            val bmp = screenshot.takeScreenshot()
            if (bmp == null) {
                Log.e(TAG, "Screenshot failed: ${screenshot.lastError}")
                return@withContext emptyList()
            }

            onProgress("Розпізнаю текст…")
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

            // ML Kit: завантажуємо модель якщо треба
            onProgress("Готую модель перекладу…")
            mlkit.downloadModel("en", targetLang)
            if (targetLang != "uk") mlkit.downloadModel("uk", targetLang)

            val translations = mutableListOf<String>()
            for ((i, block) in valid.withIndex()) {
                if (i % 5 == 0) onProgress("Перекладено ${i + 1} / ${valid.size}")
                // Визначаємо мову джерела грубо (uk якщо є кирилиця, інакше en)
                val hasCyr = block.text.any { it.code in 0x0400..0x04FF }
                val fromLang = if (hasCyr) "uk" else "en"
                // Якщо source == target — не перекладаємо, але показуємо як є
                if (fromLang == targetLang) {
                    translations.add("")
                    continue
                }
                val tr = mlkit.translate(block.text, fromLang, targetLang) ?: ""
                translations.add(tr)
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
        val re = Regex("^\\s*(\\d+)\\s*[.):\\]\\-]?\\s*(.+)$")
        val map = mutableMapOf<Int, String>()
        text.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@forEach
            val m = re.find(trimmed)
            if (m != null) {
                val idx = m.groupValues[1].toIntOrNull() ?: return@forEach
                val value = m.groupValues[2].trim()
                if (value.isNotBlank()) map[idx] = value
            }
        }
        if (map.isEmpty()) {
            // Fallback: LLM не дав номерів — беремо просто непорожні рядки
            return text.lines().map { it.trim() }.filter { it.isNotBlank() }
        }
        val max = map.keys.maxOrNull() ?: 0
        return (1..max).map { map[it] ?: "" }
    }
}
