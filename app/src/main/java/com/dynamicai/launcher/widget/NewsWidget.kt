package com.dynamicai.launcher.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

data class NewsItem(val title: String, val source: String, val link: String)

/** Новини з RSS Google News (мова/країна — за мовою пристрою), кеш 20 хв. */
object NewsFeed {
    private const val PREFS = "dynamicai_news"
    private const val TTL = 20 * 60_000L

    private fun cached(ctx: Context): Pair<Long, List<NewsItem>> {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val out = mutableListOf<NewsItem>()
        try {
            val a = JSONArray(p.getString("items", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(NewsItem(o.getString("t"), o.optString("s"), o.getString("l")))
            }
        } catch (_: Throwable) {}
        return p.getLong("ts", 0L) to out
    }

    fun cachedItems(ctx: Context): List<NewsItem> = cached(ctx).second

    suspend fun load(ctx: Context, force: Boolean = false): List<NewsItem> = withContext(Dispatchers.IO) {
        val (ts, old) = cached(ctx)
        if (!force && old.isNotEmpty() && System.currentTimeMillis() - ts < TTL) return@withContext old
        try {
            val lang = Locale.getDefault().language.ifBlank { "uk" }
            val country = Locale.getDefault().country.ifBlank { if (lang == "uk") "UA" else "US" }
            val url = "https://news.google.com/rss?hl=$lang&gl=$country&ceid=$country:$lang"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            val doc = conn.inputStream.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
            val nodes = doc.getElementsByTagName("item")
            val items = mutableListOf<NewsItem>()
            for (i in 0 until minOf(nodes.length, 15)) {
                val e = nodes.item(i) as Element
                fun tag(n: String) = e.getElementsByTagName(n).item(0)?.textContent.orEmpty().trim()
                val src = tag("source")
                var title = tag("title")
                if (src.isNotEmpty() && title.endsWith(" - $src")) title = title.removeSuffix(" - $src")
                val link = tag("link")
                if (title.isNotEmpty() && link.isNotEmpty()) items.add(NewsItem(title, src, link))
            }
            if (items.isNotEmpty()) {
                val arr = JSONArray()
                items.forEach { arr.put(JSONObject().put("t", it.title).put("s", it.source).put("l", it.link)) }
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("items", arr.toString()).putLong("ts", System.currentTimeMillis()).apply()
                items
            } else old
        } catch (_: Throwable) { old }
    }
}

@Composable
fun NewsWidget(size: String, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val rows = when (size) { "small" -> 2; "large" -> 7; "full" -> 12; else -> 4 }
    var items by remember { mutableStateOf(NewsFeed.cachedItems(ctx)) }
    var loading by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    LaunchedEffect(refreshTick) {
        loading = true
        items = NewsFeed.load(ctx, force = refreshTick > 0)
        loading = false
    }
    Column(
        modifier = modifier.widgetCard()
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp)) {
            Text("📰 Новини", color = AppTheme.colors.textSecondary, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (loading) "оновлення…" else "↻", color = AppTheme.colors.textSecondary, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            if (!loading) refreshTick++
                        })
                    })
        }
        if (loading && items.isEmpty()) {
            Text("Завантаження…", color = AppTheme.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(4.dp))
        } else if (items.isEmpty()) {
            Text("Немає з'єднання або новин", color = AppTheme.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(4.dp))
        }
        items.take(rows).forEach { n ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .pointerInput(n.link) {
                        detectTapGestures(onTap = {
                            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
                        }, onLongPress = { onLongPress() })
                    }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Text(n.title, color = AppTheme.colors.textPrimary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (n.source.isNotEmpty())
                    Text(n.source, color = AppTheme.colors.textSecondary, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}
