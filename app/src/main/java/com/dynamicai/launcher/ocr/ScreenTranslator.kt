package com.dynamicai.launcher.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import com.dynamicai.launcher.llm.LlmEngine
import com.dynamicai.launcher.system.ScreenshotController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext


object OcrUsageTracker {
    @Volatile var lastOcrAt: Long = 0L
    @Volatile var lastMlkitAt: Long = 0L
    fun recordOcr() { lastOcrAt = System.currentTimeMillis() }
    fun recordMlkit() { lastMlkitAt = System.currentTimeMillis() }
}


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

    /** Груба, але швидка і безкоштовна (без ML-моделі) детекція мови тексту. */
    private fun detectLang(text: String): String {
        val letters = text.count { it.isLetter() }.coerceAtLeast(1)
        val cyr = text.count { it.code in 0x0400..0x04FF }
        if (cyr.toFloat() / letters > 0.5f) {
            val lower = text.lowercase()
            val ru = lower.count { it in "ыэъё" }
            val ukr = lower.count { it in "іїєґ" }
            return if (ru > ukr) "ru" else "uk"
        }
        val lower = text.lowercase()
        if (lower.any { it in "ąćęłńóśźż" }) return "pl"
        if (lower.any { it in "äöüß" }) return "de"
        // Без діакритики — за поширеними службовими словами
        val words = lower.split(Regex("[^\\p{L}]+")).toSet()
        if (words.any { it in setOf("nie", "jest", "się", "na", "do", "że", "dla", "jak") } && !words.any { it in setOf("the", "and", "is", "to") }) return "pl"
        if (words.any { it in setOf("der", "die", "das", "und", "nicht", "ist", "mit") }) return "de"
        return "en"
    }

    suspend fun captureAndTranslate(
        ocrLangs: String = "ukr+eng+pol",
        targetLang: String = "uk",
        onProgress: (stage: String) -> Unit = {}
    ): List<TranslatedBlock> = withContext(Dispatchers.IO) {
        // Просто делегуємо на region-версію з повним екраном
        val bmp = screenshot.takeScreenshot()
        if (bmp == null) {
            Log.e(TAG, "Screenshot failed: ${screenshot.lastError}")
            return@withContext emptyList()
        }
        val fullRect = android.graphics.Rect(0, 0, bmp.width, bmp.height)
        bmp.recycle()
        captureRegionAndTranslate(
            rect = fullRect,
            ocrLangs = ocrLangs,
            targetLang = targetLang,
            onProgress = onProgress
        )
    }

    /**
     * Скріншот + crop до rect + OCR + переклад.
     * rect — координати в пікселях екрана.
     */
    suspend fun captureRegionAndTranslate(
        rect: android.graphics.Rect,
        ocrLangs: String = "ukr+eng+pol",
        targetLang: String = "uk",
        onProgress: (stage: String) -> Unit = {}
    ): List<TranslatedBlock> = withContext(Dispatchers.IO) {
        try {
            Log.e(TAG, "captureRegion START rect=$rect target=$targetLang")
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
            val full = screenshot.takeScreenshot()
            Log.e(TAG, "Screenshot: ${full?.width}x${full?.height}")
            if (full == null) {
                Log.e(TAG, "Screenshot NULL")
                return@withContext emptyList()
            }

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
            OcrUsageTracker.recordOcr()
            cropped.recycle()
            Log.e(TAG, "OCR region: ${blocks.size} blocks (crop $w x $h)")
            if (blocks.isEmpty()) return@withContext emptyList()

            val valid = blocks.filter { b ->
                val t = b.text.trim()
                if (t.length < 3) return@filter false
                val letters = t.count { it.isLetter() }
                if (letters < 2) return@filter false
                if (b.confidence != 0 && b.confidence <= 40) return@filter false
                // Сміття: багато спецсимволів
                val specials = t.count { !it.isLetterOrDigit() && !it.isWhitespace() && it != '.' && it != ',' && it != '!' && it != '?' }
                if (specials > letters) return@filter false
                // Сміття: змішані кирилиця + латиниця (OCR-помилка)
                val hasCyr = t.any { it.code in 0x0400..0x04FF }
                val hasLat = t.any { it in 'a'..'z' || it in 'A'..'Z' }
                if (hasCyr && hasLat) {
                    val latCount = t.count { it in 'a'..'z' || it in 'A'..'Z' }
                    if (latCount.toFloat() / letters > 0.2f) return@filter false
                }
                // Сміття: немає жодного слова >= 2 літер
                val hasRealWord = t.split(Regex("[\\s\\W]+")).any { it.length >= 2 }
                if (!hasRealWord) return@filter false
                true
            }

            // Групуємо рядки в абзаци для контекстного перекладу
            val merged = mergeBlocks(valid)
            Log.e(TAG, "Merged: ${merged.size} groups (was ${valid.size} lines)")
            Log.e(TAG, "Valid: ${valid.size} lines / ${blocks.size}")
            if (valid.isEmpty()) return@withContext emptyList()

            onProgress("Перекладаю ${merged.size} блоків…")

            // Мова кожного блоку визначається за алфавітом/діакритикою; моделі пар качаються лише за потреби
            val pairs = merged.map { detectLang(it.text) }.filter { it != targetLang }.distinct()
            for (from in pairs) mlkit.downloadModel(from, targetLang)

            val translated: List<String?> = kotlinx.coroutines.coroutineScope {
                val cache = java.util.concurrent.ConcurrentHashMap<String, String?>()
                merged.map { block ->
                    async {
                        val from = detectLang(block.text)
                        if (from == targetLang) null
                        else cache.getOrPut(block.text) { mlkit.translate(block.text, from, targetLang) }
                    }
                }.awaitAll()
            }

            val result = mutableListOf<TranslatedBlock>()
            for ((i, block) in merged.withIndex()) {
                val tr = translated[i]
                if (tr.isNullOrBlank() || tr.equals(block.text, ignoreCase = true)) continue
                val shifted = android.graphics.Rect(
                    block.bounds.left + left,
                    block.bounds.top + top,
                    block.bounds.right + left,
                    block.bounds.bottom + top
                )
                result.add(TranslatedBlock(block.text, tr, shifted, block.confidence))
            }
            Log.e(TAG, "Region translated ${result.size} / ${merged.size}")
            onProgress("Готово")
            result
        } catch (t: Throwable) {
            Log.e(TAG, "captureRegionAndTranslate failed: ${t.message}", t)
            emptyList()
        }
    }


    /**
     * Групує OCR-рядки в абзаци/речення для контекстного перекладу.
     * Правила:
     *  - Сортує по Y
     *  - Об'єднує суміжні рядки (вертикальна відстань < ~1.6 висоти)
     *  - Новий блок починається після . ! ? або при великому відступі
     *  - Bounds = union (об'єднання) всіх прямокутників групи
     */
    private fun mergeBlocks(blocks: List<OcrBlock>): List<OcrBlock> {
        if (blocks.isEmpty()) return emptyList()
        val sorted = blocks.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
        val groups = mutableListOf<MutableList<OcrBlock>>()
        var cur = mutableListOf<OcrBlock>()
        var lastBottom = -1
        var lastHeight = 0

        for (b in sorted) {
            val h = b.bounds.height().coerceAtLeast(1)
            val gap = if (lastBottom < 0) 0 else b.bounds.top - lastBottom
            val endsSentence = cur.lastOrNull()?.text?.trimEnd()?.lastOrNull() in setOf('.', '!', '?', ':')

            val closeEnough = lastBottom < 0 || gap < (lastHeight * 1.6f).toInt()
            val sameColumn = cur.lastOrNull()?.let {
                val dx = kotlin.math.abs(it.bounds.left - b.bounds.left).toFloat()
                val tolerance = (it.bounds.width() * 0.5f).coerceAtLeast(50f)
                dx < tolerance
            } ?: true

            val shouldStartNew = cur.isNotEmpty() && (endsSentence || !closeEnough || !sameColumn)

            if (shouldStartNew) {
                if (cur.isNotEmpty()) groups.add(cur)
                cur = mutableListOf()
            }
            cur.add(b)
            lastBottom = b.bounds.bottom
            lastHeight = h
        }
        if (cur.isNotEmpty()) groups.add(cur)

        return groups.map { group ->
            val text = group.joinToString(" ") { it.text.trim() }
                .replace(Regex("\\s+"), " ")
                .trim()
            val bounds = group.fold(android.graphics.Rect(
                group[0].bounds.left, group[0].bounds.top,
                group[0].bounds.right, group[0].bounds.bottom
            )) { acc, blk -> acc.apply {
                left = minOf(left, blk.bounds.left)
                top = minOf(top, blk.bounds.top)
                right = maxOf(right, blk.bounds.right)
                bottom = maxOf(bottom, blk.bounds.bottom)
            } }
            val avgConf = group.map { it.confidence }.average().toInt()
            OcrBlock(text = text, bounds = bounds, confidence = avgConf)
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
