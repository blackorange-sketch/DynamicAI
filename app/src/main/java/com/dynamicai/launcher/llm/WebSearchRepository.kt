package com.dynamicai.launcher.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Пошук в інтернеті: Wikipedia API (для фактів) та JokeAPI (для анекдотів).
 */
class WebSearchRepository {
    @Volatile var lastError: String = ""

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
        val lowerQuery = query.lowercase()
        if (lowerQuery.contains("анекдот") || lowerQuery.contains("жарт") || lowerQuery.contains("joke")) {
            return@withContext searchJoke()
        }
        return@withContext searchWikipedia(query)
    }

    private suspend fun searchJoke(): SearchResult = withContext(Dispatchers.IO) {
        try {
            val url = "https://v2.jokeapi.dev/joke/Any?lang=en&type=single,twopart"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", "DynamicAI/1.0")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val json = JSONObject(body)
            
            val jokeText = if (json.optString("type") == "single") {
                json.optString("joke", "")
            } else {
                val setup = json.optString("setup", "")
                val delivery = json.optString("delivery", "")
                "$setup\n$delivery"
            }
            
            lastError = "Joke fetched OK"
            SearchResult("Анекдот: $jokeText", "", emptyList())
        } catch (t: Throwable) {
            lastError = "JokeAPI failed: ${t.message}"
            Log.e(TAG, "Joke search failed", t)
            SearchResult("", "", emptyList())
        }
    }

    private suspend fun searchWikipedia(query: String): SearchResult = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://uk.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&utf8=1&format=json"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", "DynamicAI/1.0")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val json = JSONObject(body)
            
            val related = mutableListOf<String>()
            val searchArr = json.getJSONObject("query").getJSONArray("search")
            for (i in 0 until minOf(searchArr.length(), 3)) {
                val item = searchArr.optJSONObject(i) ?: continue
                // Прибираємо HTML теги зі снипету
                val snippet = item.optString("snippet", "").replace(Regex("<[^>]*>"), "")
                related.add(snippet)
            }
            
            lastError = "Wikipedia OK: related=${related.size}"
            SearchResult("", "", related)
        } catch (t: Throwable) {
            lastError = "Wikipedia failed: ${t.message}"
            Log.e(TAG, "Wikipedia search failed", t)
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
