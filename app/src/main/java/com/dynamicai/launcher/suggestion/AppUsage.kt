package com.dynamicai.launcher.suggestion

import android.content.Context
import kotlin.math.ln

/** Лічильник запусків додатків: підвищує у пошуку те, чим користуєшся найчастіше. */
object AppUsage {
    private const val PREFS = "app_usage"
    @Volatile private var counts: MutableMap<String, Int>? = null

    private fun load(ctx: Context): MutableMap<String, Int> = counts ?: synchronized(this) {
        counts ?: run {
            val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            p.all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap().toMutableMap().also { counts = it }
        }
    }

    fun bump(ctx: Context, pkg: String) {
        val m = load(ctx)
        val n = synchronized(m) { (m[pkg] ?: 0) + 1 }.also { synchronized(m) { m[pkg] = it } }
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(pkg, n).apply()
    }

    fun count(ctx: Context, pkg: String): Int = load(ctx).let { m -> synchronized(m) { m[pkg] ?: 0 } }

    /** Бонус до рейтингу пошуку: 0..20, росте логарифмічно. */
    fun boost(ctx: Context, pkg: String): Int = minOf(20, (ln(1.0 + count(ctx, pkg)) * 5).toInt())

    fun top(ctx: Context, n: Int): List<String> =
        load(ctx).let { m -> synchronized(m) { m.entries.sortedByDescending { it.value }.take(n).map { it.key } } }
}

/** Останні пошукові запити (до 8). */
object SearchHistory {
    private const val PREFS = "search_history"
    fun get(ctx: Context): List<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("q", "")!!
            .split('\n').filter { it.isNotBlank() }

    fun add(ctx: Context, q: String) {
        val t = q.trim()
        if (t.length < 2) return
        val list = (listOf(t) + get(ctx).filter { !it.equals(t, true) }).take(8)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("q", list.joinToString("\n")).apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("q").apply()
    }
}
