package com.dynamicai.launcher.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Переклад через MyMemory API (безкоштовно, 1000 слів/день без ключа).
 */
class TranslationRepository {

    companion object {
        private const val TAG = "Translation"
    }

    data class Result(
        val text: String,
        val sourceLang: String,
        val targetLang: String,
        val success: Boolean
    )

    /**
     * Перекласти текст.
     * @param text — текст
     * @param from — код мови джерела ("uk", "en", "auto")
     * @param to — код мови цілі ("en", "uk", "pl", "de", "fr", "es", "it")
     */
    suspend fun translate(
        text: String,
        from: String = "auto",
        to: String = "en"
    ): Result = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val url = "https://api.mymemory.translated.net/get?q=$encoded&langpair=$from|$to"

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "DynamicAI/1.0")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            val responseData = json.optJSONObject("responseData")
            val translated = responseData?.optString("translatedText", "") ?: ""
            val status = json.optInt("responseStatus", 0)

            if (status == 200 && translated.isNotBlank()) {
                Log.d(TAG, "Translated: $translated")
                Result(translated, from, to, true)
            } else {
                Log.e(TAG, "Translation failed: $body")
                Result("", from, to, false)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "translate failed", t)
            Result("", from, to, false)
        }
    }

    /**
     * Розпізнати мову з питання.
     */
    fun detectTargetFromQuestion(question: String): String {
        val q = question.lowercase()
        return when {
            q.contains("на англ") || q.contains("англійськ") || q.contains("to english") -> "en"
            q.contains("на укр") || q.contains("українськ") || q.contains("to ukrainian") -> "uk"
            q.contains("на польськ") || q.contains("польськ") || q.contains("to polish") -> "pl"
            q.contains("на німець") || q.contains("німецьк") || q.contains("to german") -> "de"
            q.contains("на француз") || q.contains("французьк") || q.contains("to french") -> "fr"
            q.contains("на іспан") || q.contains("іспанськ") || q.contains("to spanish") -> "es"
            q.contains("на італ") || q.contains("італійськ") || q.contains("to italian") -> "it"
            else -> "en"
        }
    }
}
