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
        val jokes = listOf(
            "Програміст ставить на ніч два склянки: одну з водою (якщо захоче пити), а другу порожню (якщо не захоче).",
            "— Як називається людина, яка знає, де знаходиться кінець світу? — Шукач пригод. — А як називається людина, яка знає, де знаходиться початок кінця світу? — Програміст, який знайшов баг у чужому коді.",
            "Зустрічаються два програмісти. — Ти знаєш, що таке рекурсія? — Щоб зрозуміти, що таке рекурсія, спочатку треба зрозуміти, що таке рекурсія.",
            "— Чому програмісти плутають Хелловін і Різдво? — Тому що Oct 31 == Dec 25.",
            "Засновник Apple помер і потрапив до раю. Бог каже: — Стива, я чув ти дуже любив яблука. Ось тобі золоте. Стив бере його, крутить у руках і питає: — А де ти взяв таку круту шліфовку?",
            "Найкращий спосіб приховати від дружини покупку нового iPhone — покласти його в коробку від пилососа. Вона туди ніколи не загляне.",
            "— Докторе, я постійно бачу як у мене на телефоні з'являється 1% заряду. Я вмираю? — Так, через 1 хвилину.",
            "Купив кінь копита. Тепер ходить щасливий і цокає.",
            "— Чому у кави завжди поганий настрій? — Бо її постійно заварюють!",
            "Йде людина по пустелі, бачить — лежить на піску нова сим-карта. Взяв він її, вставив у телефон, а там SMS: «Вітаємо з тим, що ви приєдналися до оператора «Сахара-Телеком». Щоб активувати номер, наберіть 1000 доларів і закопайте в пісок»."
        )
        val randomJoke = jokes.random()
        lastError = "Local Joke OK"
        SearchResult("Анекдот: " + randomJoke, "", emptyList())
    }

    private suspend fun searchWikipedia(query: String): SearchResult = withContext(Dispatchers.IO) {
        // Спочатку українська Вікіпедія, якщо порожньо — англійська
        for (lang in listOf("uk", "en")) {
            val r = wikipediaExtracts(lang, query)
            if (r.abstract.isNotBlank() || r.relatedTopics.isNotEmpty()) return@withContext r
        }
        SearchResult("", "", emptyList())
    }

    /** Пошук + вступ (extract) найкращих сторінок: це дає факти (дати, імена), а не лише фрагменти пошуку. */
    private fun wikipediaExtracts(lang: String, query: String): SearchResult {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://$lang.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrlimit=3" +
                "&prop=extracts&exintro=1&explaintext=1&exchars=700&utf8=1&format=json"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6_000
                readTimeout = 6_000
                setRequestProperty("User-Agent", "DynamicAI/1.0")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages")
                ?: return SearchResult("", "", emptyList())
            // Сторінки приходять як об'єкт; упорядковуємо за релевантністю (поле index)
            val items = mutableListOf<Triple<Int, String, String>>()
            val keys = pages.keys()
            while (keys.hasNext()) {
                val p = pages.getJSONObject(keys.next())
                items.add(Triple(p.optInt("index", 99), p.optString("title"), p.optString("extract").trim()))
            }
            items.sortBy { it.first }
            val best = items.firstOrNull { it.third.isNotBlank() }
            val abstract = best?.let { "${it.second}: ${it.third}" } ?: ""
            val related = items.filter { it !== best && it.third.isNotBlank() }.map { "${it.second}: ${it.third.take(250)}" }
            lastError = "Wikipedia[$lang] OK: ${items.size} pages"
            SearchResult("", abstract, related)
        } catch (t: Throwable) {
            lastError = "Wikipedia[$lang] failed: ${t.message}"
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
