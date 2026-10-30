package com.dynamicai.launcher.suggestion

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Зв'язок з браузером OperaFast (org.blackorange.operafast):
 *  - відкриття посилань і пошуку в ньому;
 *  - закладки й історія браузера в пошуку лаунчера (потрібен дозвіл READ_PAGES);
 *  - підказки пошукової системи (мережа, вмикається в налаштуваннях).
 */
object BrowserBridge {
    const val PKG = "org.blackorange.operafast"
    const val PERM = "org.blackorange.operafast.permission.READ_PAGES"
    private const val PAGES_URI = "content://org.blackorange.operafast.search/pages"

    data class Page(val kind: String, val title: String, val url: String)

    fun installed(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(PKG, 0); true
    } catch (_: Throwable) { false }

    fun hasAccess(ctx: Context): Boolean =
        installed(ctx) && ctx.checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED

    /** Закладки та історія браузера за запитом (швидко, локально). Порожній список, якщо немає дозволу. */
    fun pages(ctx: Context, query: String, limit: Int = 4): List<Page> {
        if (query.isBlank() || !hasAccess(ctx)) return emptyList()
        val out = ArrayList<Page>()
        try {
            val uri = Uri.parse(PAGES_URI).buildUpon()
                .appendQueryParameter("q", query.trim())
                .appendQueryParameter("limit", limit.toString())
                .build()
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val iKind = c.getColumnIndex("kind")
                val iTitle = c.getColumnIndex("title")
                val iUrl = c.getColumnIndex("url")
                while (c.moveToNext()) {
                    val url = c.getString(iUrl) ?: continue
                    out.add(Page(c.getString(iKind) ?: "history", c.getString(iTitle) ?: "", url))
                }
            }
        } catch (_: Throwable) {}
        return out
    }

    /**
     * Відкриває адресу в браузері: той, що вибрано в налаштуваннях «Браузер»,
     * інакше OperaFast (якщо встановлено). false — нічого не підійшло, викликач відкриває як раніше.
     */
    fun open(ctx: Context, url: String): Boolean {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val preferred = try {
            com.dynamicai.launcher.data.SettingsStorage(ctx).load().defaultBrowser
        } catch (_: Throwable) { "" }
        val pkg = when {
            preferred.isNotBlank() -> preferred
            installed(ctx) -> PKG
            else -> return false
        }
        return try {
            ctx.startActivity(Intent(view).setPackage(pkg)); true
        } catch (_: Throwable) { false }
    }

    /** Підказки Google для запиту (блокуюче — викликати з IO). Порожній список при будь-якій помилці. */
    fun webSuggestions(query: String): List<String> {
        val out = ArrayList<String>()
        var conn: HttpURLConnection? = null
        try {
            val q = URLEncoder.encode(query, "UTF-8")
            conn = (URL("https://suggestqueries.google.com/complete/search?client=firefox&hl=uk&q=$q")
                .openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
                setRequestProperty("Accept-Language", "uk,en;q=0.7")
            }
            if (conn.responseCode !in 200..299) return out
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(text).getJSONArray(1)
            for (i in 0 until minOf(arr.length(), 5)) {
                val s = arr.optString(i, "")
                if (s.isNotEmpty() && !s.equals(query, ignoreCase = true)) out.add(s)
            }
        } catch (_: Throwable) {
            out.clear()
        } finally {
            conn?.disconnect()
        }
        return out
    }

    fun searchUrl(text: String): String =
        "https://www.google.com/search?q=" + Uri.encode(text)
}
