package com.dynamicai.launcher.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class OcrBlock(
    val text: String,
    val bounds: Rect,
    val confidence: Int
)

class OcrEngine(private val context: Context) {

    companion object {
        private const val TAG = "OcrEngine"
    }

    private val modelManager = TessModelManager(context)
    private var tess: TessBaseAPI? = null
    private var currentLangs: String = ""
    @Volatile var lastUsedAt: Long = 0L
        private set

    /** Розпізнає текст. langs — "ukr" або "ukr+eng". */
    suspend fun recognize(bitmap: Bitmap, langs: String): List<OcrBlock> = withContext(Dispatchers.Default) {
        lastUsedAt = System.currentTimeMillis()
        try {
            val api = ensureTess(langs) ?: return@withContext emptyList()

            api.setImage(bitmap)
            // ВАЖЛИВО: спочатку utF8Text — він активує resultIterator
            api.utF8Text

            val result = mutableListOf<OcrBlock>()
            val iter = api.resultIterator
            if (iter == null) {
                Log.w(TAG, "resultIterator null")
                return@withContext result
            }

            do {
                val level = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
                val wordText = iter.getUTF8Text(level)?.trim() ?: continue
                val box = iter.getBoundingBox(level)
                if (box == null || box.size < 4) continue

                val left = box[0]
                val top = box[1]
                val right = box[2]
                val bottom = box[3]
                val conf = iter.confidence(level).toInt()

                if (wordText.isNotBlank() && (right - left) > 0 && (bottom - top) > 0) {
                    result.add(
                        OcrBlock(
                            text = wordText,
                            bounds = Rect(left, top, right, bottom),
                            confidence = conf
                        )
                    )
                }
            } while (iter.next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE))

            Log.d(TAG, "Recognized ${result.size} lines")
            result
        } catch (t: Throwable) {
            Log.e(TAG, "recognize failed: ${t.message}", t)
            emptyList()
        }
    }

    private fun ensureTess(langs: String): TessBaseAPI? {
        if (tess != null && currentLangs == langs) return tess

        try {
            // Перевірити що всі мови завантажені
            val langList = langs.split("+")
            for (l in langList) {
                if (!modelManager.isDownloaded(l)) {
                    Log.e(TAG, "Language not downloaded: $l")
                    return null
                }
            }

            tess?.recycle()
            val api = TessBaseAPI()
            val dataPath = modelManager.getTessdataPath()
            val ok = api.init(dataPath, langs)
            if (!ok) {
                Log.e(TAG, "Tess init failed")
                return null
            }
            tess = api
            currentLangs = langs
            return api
        } catch (t: Throwable) {
            Log.e(TAG, "ensureTess failed: ${t.message}", t)
            return null
        }
    }

    fun recycle() {
        try { tess?.recycle() } catch (_: Throwable) {}
        tess = null
        currentLangs = ""
    }
}
