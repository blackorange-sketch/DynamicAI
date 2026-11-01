package com.dynamicai.launcher.data

import android.content.Context

/**
 * Док із збереженим порядком. Склад дока лежить у наборі "apps" (сумісність зі старими версіями
 * та резервними копіями), порядок — у рядку "order" (id через \n). Id без запису в order
 * дописуються в кінець.
 */
class DockStorage(context: Context) {

    private val prefs = context.getSharedPreferences("dynamicai_dock", Context.MODE_PRIVATE)

    fun getDockApps(): List<String> {
        val set = prefs.getStringSet("apps", emptySet()) ?: emptySet()
        val order = (prefs.getString("order", "") ?: "").split('\n').filter { it.isNotBlank() }
        val ordered = order.filter { it in set }.distinct()
        return ordered + set.filter { it !in ordered }
    }

    private fun save(list: List<String>) {
        prefs.edit()
            .putStringSet("apps", list.toSet())
            .putString("order", list.joinToString("\n"))
            .commit()
    }

    fun addToDock(pkg: String) {
        val current = getDockApps()
        if (pkg !in current) save(current + pkg)
    }

    fun removeFromDock(pkg: String) {
        save(getDockApps().filter { it != pkg })
    }

    fun toggle(pkg: String) {
        if (pkg in getDockApps()) removeFromDock(pkg) else addToDock(pkg)
    }

    /** Зсуває елемент на delta позицій (−1 = ліворуч/вгору, +1 = праворуч/вниз). */
    fun move(pkg: String, delta: Int) {
        val list = getDockApps().toMutableList()
        val from = list.indexOf(pkg)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.size - 1)
        if (to == from) return
        list.removeAt(from)
        list.add(to, pkg)
        save(list)
    }
}
