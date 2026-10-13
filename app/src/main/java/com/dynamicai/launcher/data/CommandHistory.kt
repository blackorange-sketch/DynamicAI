package com.dynamicai.launcher.data

import android.content.Context

class CommandHistory(context: Context) {

    private val prefs = context.getSharedPreferences("dynamicai_history", Context.MODE_PRIVATE)
    private val maxSize = 10

    fun get(): List<String> =
        prefs.getString("items", null)
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val current = get().toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        while (current.size > maxSize) current.removeAt(current.size - 1)
        prefs.edit().putString("items", current.joinToString("\n")).apply()
    }

    fun clear() {
        prefs.edit().remove("items").apply()
    }
}
