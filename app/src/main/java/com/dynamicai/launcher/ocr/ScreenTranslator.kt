package com.dynamicai.launcher.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import com.dynamicai.launcher.llm.TranslationRepository
import com.dynamicai.launcher.system.ScreenshotController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TranslatedBlock(
    val originalText: String,
    val translatedText: String,
    val bounds: Rect,
    val confidence: Int
)

class ScreenTranslator(private val context: Context) {

    companion object {
        private const val TAG = "ScreenTranslator"
    }

    private val screenshot = ScreenshotController(context)
    private val ocr = OcrEngine(context)
    private val translation = TranslationRepository()

    /**
     * Скріншот → OCR → переклад.
     * @param ocrLangs "ukr" або "ukr+eng"
     * @param targetLang "en", "pl"
     */
    suspend fun captureAndTranslate(
        ocrLangs: String = "ukr+eng",
        targetLang: String = "en",
        onProgress: (stage: String) -> Unit = {}
    ): List<TranslatedBlock> = withContext(Dispatchers.IO) {
        try {
            // Автозавантаження OCR-мов якщо немає
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
            if (blocks.isEmpty()) {
                bmp.recycle()
                return@withContext emptyList()
            }

            onProgress("Перекладаю ${blocks.size} блоків…")
            val result = mutableListOf<TranslatedBlock>()
            for ((i, block) in blocks.withIndex()) {
                // Пропускаємо блоки з дуже низькою впевненістю
                if (block.confidence in 1..40) continue
                // Пропускаємо дуже короткі (1-2 символи)
                if (block.text.trim().length < 2) continue

                val translated = try {
                    translation.translate(block.text, from = "auto", to = targetLang)
                } catch (_: Throwable) { null }

                if (translated?.success == true && translated.text.isNotBlank()) {
                    result.add(
                        TranslatedBlock(
                            originalText = block.text,
                            translatedText = translated.text,
                            bounds = block.bounds,
                            confidence = block.confidence
                        )
                    )
                }

                if (i % 5 == 0) onProgress("Перекладено ${i + 1} / ${blocks.size}")
            }

            bmp.recycle()
            Log.d(TAG, "Translated ${result.size} / ${blocks.size} blocks")
            onProgress("Готово")
            result
        } catch (t: Throwable) {
            Log.e(TAG, "captureAndTranslate failed: ${t.message}", t)
            emptyList()
        }
    }
}
