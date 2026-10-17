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

    /**
     * Скріншот + crop до rect + OCR + переклад.
     * rect — координати в пікселях екрана.
     */
    suspend fun captureRegionAndTranslate(
        rect: android.graphics.Rect,
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
                    if (!ok) return@withContext emptyList()
                }
            }

            onProgress("Роблю скріншот…")
            val full = screenshot.takeScreenshot() ?: return@withContext emptyList()

            // Crop до rect (захист від виходу за межі)
            val left = rect.left.coerceIn(0, full.width - 1)
            val top = rect.top.coerceIn(0, full.height - 1)
            val right = rect.right.coerceIn(left + 1, full.width)
            val bottom = rect.bottom.coerceIn(top + 1, full.height)
            val w = right - left
            val h = bottom - top
            if (w < 30 || h < 30) {
                full.recycle()
                return@withContext emptyList()
            }

            val cropped = android.graphics.Bitmap.createBitmap(full, left, top, w, h)
            full.recycle()

            onProgress("Розпізнаю текст…")
            val blocks = ocr.recognize(cropped, ocrLangs)
            cropped.recycle()
            Log.d(TAG, "OCR region: ${blocks.size} blocks")
            if (blocks.isEmpty()) return@withContext emptyList()

            val valid = blocks.filter { b ->
                val t = b.text.trim()
                if (t.length < 3) return@filter false
                val letters = t.count { it.isLetter() }
                if (letters < 2) return@filter false
                if (b.confidence != 0 && b.confidence <= 40) return@filter false
                val specials = t.count { !it.isLetterOrDigit() && !it.isWhitespace() && it != '.' && it != ',' && it != '!' && it != '?' }
                if (specials > letters) return@filter false
                true
            }
            Log.d(TAG, "Valid: ${valid.size} / ${blocks.size}")
            if (valid.isEmpty()) return@withContext emptyList()

            onProgress("Перекладаю ${valid.size} блоків…")
            mlkit.downloadModel("en", targetLang)
            if (targetLang != "uk") mlkit.downloadModel("uk", targetLang)

            val result = mutableListOf<TranslatedBlock>()
            for ((i, block) in valid.withIndex()) {
                if (i % 5 == 0) onProgress("Перекладено ${i + 1} / ${valid.size}")
                val hasCyr = block.text.any { it.code in 0x0400..0x04FF }
                val fromLang = if (hasCyr) "uk" else "en"
                if (fromLang == targetLang) continue
                val tr = mlkit.translate(block.text, fromLang, targetLang) ?: continue
                if (tr.isBlank()) continue
                // Координати зсуваємо назад у повний екран
                val shifted = android.graphics.Rect(
                    block.bounds.left + left,
                    block.bounds.top + top,
                    block.bounds.right + left,
                    block.bounds.bottom + top
                )
                result.add(
                    TranslatedBlock(
                        originalText = block.text,
                        translatedText = tr,
                        bounds = shifted,
                        confidence = block.confidence
                    )
                )
            }
            Log.d(TAG, "Region translated ${result.size} / ${valid.size}")
            onProgress("Готово")
            result
        } catch (t: Throwable) {
            Log.e(TAG, "captureRegionAndTranslate failed: ${t.message}", t)
            emptyList()
        }
    }

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
