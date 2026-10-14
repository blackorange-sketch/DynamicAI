package com.dynamicai.launcher.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Пошук в інтернеті через DuckDuckGo Instant Answer API (безкоштовно, без ключа).
 */
class WebSearchRepository {

    companion object {
        private const val TAG = "WebSearch"
    }

    data class SearchResult(
        val answer: String,
        val abstract: String,
        val relatedTopics: List<String>
    )

    /**
     * Виконати пошук і повернути короткий контекст.
     */
    suspend fun search(query: String): SearchResult = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "DynamicAI/1.0")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)

            val answer = json.optString("Answer", "").trim()
            val abstract = json.optString("AbstractText", "").trim()

            val related = mutableListOf<String>()
            val relatedArr = json.optJSONArray("RelatedTopics")
            if (relatedArr != null) {
                for (i in 0 until minOf(relatedArr.length(), 5)) {
                    val item = relatedArr.optJSONObject(i) ?: continue
                    val text = item.optString("Text", "")
                    if (text.isNotBlank()) related.add(text)
                }
            }

            Log.d(TAG, "Search '$query': answer=${answer.length}, abstract=${abstract.length}, related=${related.size}")

            SearchResult(answer, abstract, related)
        } catch (t: Throwable) {
            Log.e(TAG, "search failed", t)
            SearchResult("", "", emptyList())
        }
    }

    /**
     * Форматує результат у контекст для LLM.
     */
    fun toContext(result: SearchResult): String {
        val sb = StringBuilder()
        if (result.answer.isNotBlank()) {
            sb.append("Коротка відповідь: ").append(result.answer).append("\n")
        }
        if (result.abstract.isNotBlank()) {
            sb.append("Деталі: ").append(result.abstract).append("\n")
        }
        if (result.relatedTopics.isNotEmpty()) {
            sb.append("Пов'язане:\n")
            result.relatedTopics.forEach { sb.append("• ").append(it).append("\n") }
        }
        return sb.toString().trim()
    }
}
