package com.dynamicai.launcher.suggestion

import android.content.Context
import android.content.pm.LauncherApps
import org.json.JSONArray
import org.json.JSONObject

/**
 * Локальний індекс чатів Telegram для пошуку.
 * Джерела: ярлики Telegram у LauncherApps (потрібна роль домашнього додатка) та заголовки сповіщень.
 * Відкриття: startShortcut(pkg, shortcutId); якщо ярлика нема — відкривається сам Telegram.
 */
object TelegramIndex {
    data class Chat(val name: String, val pkg: String, val shortcutId: String, val ts: Long)

    private const val PREFS = "telegram_index"
    private const val KEY = "chats"
    private const val MAX = 300
    private val KNOWN = listOf(
        "org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.plus",
        "org.thunderdog.challegram", "nekox.messenger", "tw.nekomimi.nekogram", "app.nicegram"
    )

    fun isTelegram(pkg: String) = pkg in KNOWN

    @Volatile private var cache: List<Chat>? = null
    @Volatile private var shortcutsLoadedAt = 0L

    private fun read(ctx: Context): MutableList<Chat> {
        cache?.let { return it.toMutableList() }
        val out = mutableListOf<Chat>()
        try {
            val arr = JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Chat(o.getString("n"), o.getString("p"), o.optString("s"), o.optLong("t")))
            }
        } catch (_: Throwable) {}
        cache = out
        return out.toMutableList()
    }

    private fun write(ctx: Context, list: List<Chat>) {
        val trimmed = list.sortedByDescending { it.ts }.take(MAX)
        cache = trimmed
        val arr = JSONArray()
        trimmed.forEach { arr.put(JSONObject().put("n", it.name).put("p", it.pkg).put("s", it.shortcutId).put("t", it.ts)) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    @Synchronized
    fun record(ctx: Context, name: String, pkg: String, shortcutId: String?) {
        val n = name.trim()
        if (n.isEmpty() || n.length > 80) return
        val list = read(ctx)
        val old = list.firstOrNull { it.pkg == pkg && it.name.equals(n, true) }
        val sid = shortcutId?.takeIf { it.isNotBlank() } ?: old?.shortcutId.orEmpty()
        list.removeAll { it.pkg == pkg && it.name.equals(n, true) }
        list.add(Chat(n, pkg, sid, System.currentTimeMillis()))
        write(ctx, list)
    }

    /** Підтягує ярлики Telegram (не частіше за раз на 5 хв). */
    @Synchronized
    fun refreshShortcuts(ctx: Context) {
        val now = System.currentTimeMillis()
        if (now - shortcutsLoadedAt < 5 * 60_000L) return
        shortcutsLoadedAt = now
        try {
            val la = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            if (!la.hasShortcutHostPermission()) return
            val list = read(ctx)
            var changed = false
            for (pkg in KNOWN) {
                val q = LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
                val shortcuts = try { la.getShortcuts(q, android.os.Process.myUserHandle()) } catch (_: Throwable) { null } ?: continue
                for (s in shortcuts) {
                    if (!s.isEnabled) continue
                    val name = (s.shortLabel ?: s.longLabel ?: "").toString().trim()
                    if (name.isEmpty() || name.length > 80) continue
                    val existing = list.indexOfFirst { it.pkg == pkg && it.name.equals(name, true) }
                    if (existing >= 0) {
                        if (list[existing].shortcutId != s.id) { list[existing] = list[existing].copy(shortcutId = s.id); changed = true }
                    } else {
                        list.add(Chat(name, pkg, s.id, 0L)); changed = true
                    }
                }
            }
            if (changed) write(ctx, list)
        } catch (_: Throwable) {}
    }

    fun search(ctx: Context, query: String): List<Pair<Chat, Int>> {
        refreshShortcuts(ctx)
        return read(ctx).mapNotNull { c ->
            val sc = com.dynamicai.launcher.command.FuzzyMatcher.score(c.name, query)
            if (sc >= 60) c to sc else null
        }.sortedByDescending { it.second }.take(5)
    }

    fun open(ctx: Context, pkg: String, shortcutId: String) {
        val app = ctx.applicationContext
        if (shortcutId.isNotBlank()) {
            try {
                (app.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps)
                    .startShortcut(pkg, shortcutId, null, null, android.os.Process.myUserHandle())
                return
            } catch (_: Throwable) {}
        }
        try {
            app.packageManager.getLaunchIntentForPackage(pkg)?.let {
                app.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (_: Throwable) {}
    }
}
