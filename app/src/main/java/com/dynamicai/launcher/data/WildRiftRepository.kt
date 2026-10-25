package com.dynamicai.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class WildRiftRepository(private val context: Context) {

    private var items: List<JSONObject> = emptyList()
    private var counters: Map<String, List<String>> = emptyMap()

    init {
        loadData()
    }

    private fun loadData() {
        try {
            val itemsJson = context.assets.open("wildrift/items.json").bufferedReader().use { it.readText() }
            val itemsArray = JSONArray(itemsJson)
            items = (0 until itemsArray.length()).map { itemsArray.getJSONObject(it) }

            val countersJson = context.assets.open("wildrift/counters.json").bufferedReader().use { it.readText() }
            val countersObj = JSONObject(countersJson)
            val map = mutableMapOf<String, List<String>>()
            countersObj.keys().forEach { key ->
                val arr = countersObj.getJSONArray(key)
                map[key] = (0 until arr.length()).map { arr.getString(it) }
            }
            counters = map
        } catch (e: Exception) {
            // Логування помилок
        }
    }

    /** Пошук предметів за назвою (частковий збіг). */
    fun findItemsByName(names: List<String>): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        for (item in items) {
            val name = item.optJSONObject("name")?.optString("en") ?: item.optString("id")
            if (names.any { name.contains(it, ignoreCase = true) }) {
                result.add(item)
            }
        }
        return result
    }

    /** Формує текстовий контекст для LLM на основі запиту. */
    fun buildContextForQuery(query: String): String {
        val lowerQuery = query.lowercase()
        val matchedChampions = counters.keys.filter { lowerQuery.contains(it.lowercase()) }

        if (matchedChampions.isEmpty()) {
            return ""
        }

        val sb = StringBuilder()
        sb.append("Актуальні контр-предмети для чемпіонів (Wild Rift патч 7.3c):\n")
        for (champ in matchedChampions) {
            val itemNames = counters[champ] ?: continue
            sb.append("\n**$champ**:\n")
            val matchedItems = findItemsByName(itemNames)
            for (item in matchedItems) {
                val name = item.optJSONObject("name")?.optString("en") ?: item.optString("id")
                val desc = item.optJSONObject("description")?.optString("en") ?: ""
                sb.append("- $name — ${desc.take(120)}\n")
            }
        }
        return sb.toString()
    }
}
