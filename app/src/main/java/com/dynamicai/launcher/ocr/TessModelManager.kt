package com.dynamicai.launcher.ocr

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Завантаження traineddata для Tesseract з офіційного репозиторію.
 * Файли зберігаються в filesDir/tessdata.
 */
class TessModelManager(private val context: Context) {

    companion object {
        private const val TAG = "TessModel"
        private const val BASE_URL = "https://github.com/tesseract-ocr/tessdata_fast/raw/main"

        // Доступні мови з приблизним розміром (МБ)
        val LANGUAGES = mapOf(
            "ukr" to "Українська",
            "eng" to "English",
            "pol" to "Polski",
            "rus" to "Русский",
            "deu" to "Deutsch",
            "fra" to "Français",
            "spa" to "Español"
        )
    }

    private val tessdataDir: File
        get() = File(context.filesDir, "tessdata").apply { if (!exists()) mkdirs() }

    fun getTessdataPath(): String = tessdataDir.parentFile?.absolutePath ?: context.filesDir.absolutePath

    fun isDownloaded(lang: String): Boolean {
        val f = File(tessdataDir, "$lang.traineddata")
        return f.exists() && f.length() > 100_000
    }

    fun downloadedLanguages(): List<String> = LANGUAGES.keys.filter { isDownloaded(it) }

    fun deleteLanguage(lang: String): Boolean {
        val f = File(tessdataDir, "$lang.traineddata")
        return if (f.exists()) f.delete() else true
    }

    suspend fun download(
        lang: String,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val target = File(tessdataDir, "$lang.traineddata")
            if (target.exists() && target.length() > 100_000) return@withContext true

            val url = "$BASE_URL/$lang.traineddata"
            Log.d(TAG, "Downloading $url")

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "DynamicAI/1.0")
            }
            conn.connect()
            val total = conn.contentLengthLong
            val temp = File(tessdataDir, "$lang.traineddata.part")

            conn.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var read: Int
                    while (input.read(buf).also { read = it } > 0) {
                        output.write(buf, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
            conn.disconnect()

            if (temp.length() < 100_000) {
                temp.delete()
                Log.e(TAG, "Downloaded file too small: ${temp.length()}")
                return@withContext false
            }

            if (target.exists()) target.delete()
            val ok = temp.renameTo(target)
            Log.d(TAG, "Downloaded $lang: ok=$ok size=${target.length()}")
            ok
        } catch (t: Throwable) {
            Log.e(TAG, "download($lang) failed: ${t.message}", t)
            false
        }
    }
}
