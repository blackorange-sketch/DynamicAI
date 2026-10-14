package com.dynamicai.launcher.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * Новини через безкоштовні RSS-фіди (без API-ключа).
 */
class NewsRepository {

    companion object {
        private const val TAG = "NewsRepository"

        data class Feed(val name: String, val url: String)

        // Перевірені публічні RSS
        val DEFAULT_FEEDS = listOf(
            Feed("Українська правда", "https://www.pravda.com.ua/rss/"),
            Feed("BBC News", "https://feeds.bbci.co.uk/news/world/rss.xml"),
            Feed("УНІАН", "https://rss.unian.net/site/news_ukr.rss"),
            Feed("РБК-Україна", "https://www.rbc.ua/static/rss/newsline.ukr.rss.xml"),
            Feed("Радіо Свобода", "https://www.radiosvoboda.org/api/zrqiteuuir")
        )

        // Категорії
        val TECH_FEEDS = listOf(
            Feed("Hacker News", "https://news.ycombinator.com/rss"),
            Feed("The Verge", "https://www.theverge.com/rss/index.xml"),
            Feed("TechCrunch", "https://techcrunch.com/feed/")
        )
    }

    data class NewsItem(
        val title: String,
        val description: String,
        val source: String
    )

    /**
     * Отримати новини. Якщо category не вказано — топ-5 з усіх фідів.
     */
    suspend fun getNews(category: String = "", limit: Int = 5): List<NewsItem> = withContext(Dispatchers.IO) {
        val feeds = when {
            category.contains("технолог", ignoreCase = true) ||
            category.contains("tech", ignoreCase = true) -> TECH_FEEDS
            else -> DEFAULT_FEEDS
        }

        val result = mutableListOf<NewsItem>()

        for (feed in feeds) {
            if (result.size >= limit) break
            try {
                val items = fetchFeed(feed, limit - result.size)
                result.addAll(items)
            } catch (t: Throwable) {
                Log.d(TAG, "Feed ${feed.name} failed: ${t.message}")
            }
        }

        result.take(limit)
    }

    private fun fetchFeed(feed: Feed, maxItems: Int): List<NewsItem> {
        val url = URL(feed.url)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 DynamicAI")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val items = mutableListOf<NewsItem>()

        // Парсимо <item> або <entry>
        val itemPattern = Pattern.compile(
            "<item[^>]*>([\\s\\S]*?)</item>|<entry[^>]*>([\\s\\S]*?)</entry>",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = itemPattern.matcher(body)

        while (matcher.find() && items.size < maxItems) {
            val content = matcher.group(1) ?: matcher.group(2) ?: continue
            val title = extractTag(content, "title")
            val desc = extractTag(content, "description")
                ?: extractTag(content, "summary")
                ?: ""

            if (title.isNotBlank()) {
                items.add(
                    NewsItem(
                        title = cleanHtml(title),
                        description = cleanHtml(desc).take(200),
                        source = feed.name
                    )
                )
            }
        }

        Log.d(TAG, "${feed.name}: ${items.size} items")
        return items
    }

    private fun extractTag(xml: String, tag: String): String? {
        val pattern = Pattern.compile(
            "<$tag[^>]*>([\\s\\S]*?)</$tag>",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(xml)
        return if (matcher.find()) {
            matcher.group(1)
                ?.replace("<![CDATA[", "")
                ?.replace("]]>", "")
                ?.trim()
        } else null
    }

    private fun cleanHtml(s: String): String {
        return s.replace(Regex("<[^>]+>"), "")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&#39;", "'")
            .trim()
    }

    fun toContext(items: List<NewsItem>): String {
        if (items.isEmpty()) return ""
        return items.joinToString("\n") { item ->
            "• [${item.source}] ${item.title}"
        }
    }
}
