package com.dynamicai.launcher.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    private val MOBILE_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private fun fetch(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 7_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", MOBILE_UA)
            setRequestProperty("Accept-Language", "uk,en;q=0.8")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun cleanHtml(s: String): String =
        android.text.Html.fromHtml(s, android.text.Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()

    private fun formatResults(titles: List<String>, snippets: List<String>, limit: Int): List<String> =
        snippets.indices.take(limit).mapNotNull { i ->
            val sn = snippets[i].takeIf { it.length > 20 } ?: return@mapNotNull null
            val title = titles.getOrNull(i)
            val prefix = if (title != null) title + ": " else ""
            "• " + prefix + sn.take(300)
        }

    private fun ddg(q: String, limit: Int): List<String> {
        val html = fetch("https://html.duckduckgo.com/html/?q=$q&kl=ua-uk")
        val titles = Regex("class=\"result__a\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(html).map { cleanHtml(it.groupValues[1]) }.toList()
        val snippets = Regex("class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(html).map { cleanHtml(it.groupValues[1]) }.toList()
        return formatResults(titles, snippets, limit)
    }

    /** Bing у форматі RSS: працює без ключа і зазвичай не вимагає капчі. */
    private fun bing(q: String, limit: Int): List<String> {
        val xml = fetch("https://www.bing.com/search?q=$q&format=rss&setlang=uk&mkt=uk-UA")
        val items = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { it.groupValues[1] }.toList()
        val titles = items.map { cleanHtml(Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(it)?.groupValues?.get(1) ?: "") }
        val snippets = items.map { cleanHtml(Regex("<description>(.*?)</description>", RegexOption.DOT_MATCHES_ALL).find(it)?.groupValues?.get(1) ?: "") }
        return formatResults(titles, snippets, limit)
    }

    /** Google News RSS: свіжі заголовки з датою (концерти, події, новини). */
    private fun news(q: String, limit: Int): List<String> {
        val xml = fetch("https://news.google.com/rss/search?q=$q&hl=uk&gl=UA&ceid=UA:uk")
        val items = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { it.groupValues[1] }.toList()
        return items.take(limit).mapNotNull { it0 ->
            val title = cleanHtml(Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(it0)?.groupValues?.get(1) ?: "")
            val date = Regex("<pubDate>(.*?)</pubDate>", RegexOption.DOT_MATCHES_ALL).find(it0)?.groupValues?.get(1)?.take(16) ?: ""
            if (title.length < 10) null else "• " + title + (if (date.isNotBlank()) " ($date)" else "")
        }
    }

    /** Пошук у вебі: DuckDuckGo → Bing RSS → Google News RSS (перше джерело, що дало результат). */
    suspend fun searchWeb(query: String, limit: Int = 5): String = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        val sources = listOf<Pair<String, (String, Int) -> List<String>>>("ddg" to ::ddg, "bing" to ::bing, "news" to ::news)
        val errors = StringBuilder()
        for ((name, fn) in sources) {
            try {
                val r = fn(q, limit)
                if (r.isNotEmpty()) {
                    lastError = "Web[$name] OK: ${r.size} results"
                    return@withContext r.joinToString("\n")
                }
                errors.append("$name: empty; ")
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                errors.append("$name: ${t.message}; ")
            }
        }
        lastError = "Web failed: $errors"
        Log.e(TAG, "Web search failed: $errors")
        ""
    }

    data class WebHit(val title: String, val snippet: String, val url: String) {
        val domain: String get() = try { java.net.URI(url).host.orEmpty().removePrefix("www.") } catch (_: Exception) { url }
    }

    private fun unescapeAmp(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#x27;", "'")

    private fun ddgHits(q: String, limit: Int): List<WebHit> {
        val html = fetch("https://html.duckduckgo.com/html/?q=$q&kl=ua-uk")
        val anchors = Regex("<a[^>]*class=\"result__a\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(html).toList()
        val snippets = Regex("class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(html).map { cleanHtml(it.groupValues[1]) }.toList()
        val out = ArrayList<WebHit>()
        for ((i, m) in anchors.withIndex()) {
            if (out.size >= limit) break
            val href = Regex("href=\"([^\"]+)\"").find(m.value)?.groupValues?.get(1)?.let { unescapeAmp(it) } ?: continue
            var url = href
            if (url.contains("uddg=")) {
                url = java.net.URLDecoder.decode(url.substringAfter("uddg=").substringBefore("&"), "UTF-8")
            } else if (url.startsWith("//")) url = "https:$url"
            if (!url.startsWith("http")) continue
            if (url.contains("duckduckgo.com")) continue   // реклама й службові посилання
            out.add(WebHit(cleanHtml(m.groupValues[1]), snippets.getOrNull(i).orEmpty(), url))
        }
        return out
    }

    private fun bingHits(q: String, limit: Int): List<WebHit> {
        val xml = fetch("https://www.bing.com/search?q=$q&format=rss&setlang=uk&mkt=uk-UA")
        val items = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { it.groupValues[1] }.toList()
        return items.mapNotNull { it0 ->
            val title = cleanHtml(Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(it0)?.groupValues?.get(1) ?: "")
            val link = Regex("<link>(.*?)</link>", RegexOption.DOT_MATCHES_ALL).find(it0)?.groupValues?.get(1)?.trim() ?: return@mapNotNull null
            val desc = cleanHtml(Regex("<description>(.*?)</description>", RegexOption.DOT_MATCHES_ALL).find(it0)?.groupValues?.get(1) ?: "")
            if (!link.startsWith("http")) null else WebHit(title, desc, link)
        }.take(limit)
    }

    /** Результати вебпошуку з посиланнями (DuckDuckGo → Bing). */
    suspend fun searchHits(query: String, limit: Int = 6): List<WebHit> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        for ((name, fn) in listOf<Pair<String, (String, Int) -> List<WebHit>>>("ddg" to ::ddgHits, "bing" to ::bingHits)) {
            try {
                val r = fn(q, limit)
                if (r.isNotEmpty()) { lastError = "Hits[$name] OK: ${r.size}"; return@withContext r }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                lastError = "Hits[$name] failed: ${t.message}"
            }
        }
        emptyList()
    }

    private val BLOCK_TAGS = Regex("<(script|style|nav|header|footer|aside|noscript|form|svg)[^>]*>.*?</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    private fun fetchHtmlLimited(url: String, maxChars: Int = 400_000): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 6_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", MOBILE_UA)
            setRequestProperty("Accept-Language", "uk,en;q=0.8")
        }
        try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            val type = conn.contentType.orEmpty().lowercase()
            if (type.isNotEmpty() && !type.contains("html") && !type.contains("text")) throw java.io.IOException("not html: $type")
            val sb = StringBuilder()
            conn.inputStream.bufferedReader().use { r ->
                val buf = CharArray(8192)
                while (sb.length < maxChars) {
                    val n = r.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                }
            }
            return sb.toString()
        } finally { conn.disconnect() }
    }

    /** Основний текст сторінки: найрелевантніші до запиту абзаци (до maxChars). */
    private fun extractRelevant(html: String, query: String, maxChars: Int): String {
        var t = BLOCK_TAGS.replace(html, " ")
        t = t.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), " ")
        t = t.replace(Regex("</(p|div|li|h[1-6]|tr|section|article)>|<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        t = t.replace(Regex("<[^>]+>"), " ")
        val words = query.lowercase().split(Regex("[^\\p{L}\\d]+")).filter { it.length >= 4 }.map { it.take(5) }.toSet()
        val paras = t.split('\n').map { cleanHtml(it) }.filter { it.length >= 70 }
        if (paras.isEmpty()) return ""
        val scored = paras.mapIndexed { i, p ->
            val low = p.lowercase()
            val hit = words.count { low.contains(it) }
            Triple(i, p, hit * 10 + (if (i < 4) 3 else 0) + minOf(p.length, 400) / 100)
        }
        val chosen = scored.sortedByDescending { it.third }.take(8).sortedBy { it.first }
        val out = StringBuilder()
        for ((_, p, _) in chosen) {
            if (out.length + p.length > maxChars) { if (out.isEmpty()) out.append(p.take(maxChars)); break }
            out.append(p).append('\n')
        }
        return out.toString().trim()
    }

    /** Паралельно читає сторінки (кожна не довше ~7 с); повертає url → релевантний текст. */
    suspend fun readPages(urls: List<String>, query: String, maxCharsEach: Int = 1500): Map<String, String> =
        withContext(Dispatchers.IO) {
            kotlinx.coroutines.coroutineScope {
                urls.map { u ->
                    async {
                        u to (kotlinx.coroutines.withTimeoutOrNull(7_000) {
                            try { extractRelevant(fetchHtmlLimited(u), query, maxCharsEach) } catch (t: Throwable) {
                                if (t is kotlinx.coroutines.CancellationException) throw t
                                ""
                            }
                        } ?: "")
                    }
                }.awaitAll().filter { it.second.isNotBlank() }.toMap()
            }
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
