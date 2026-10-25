package com.dynamicai.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class WildRiftRepository(private val context: Context) {

    private var items: List<JSONObject> = emptyList()
    private var heroes: List<JSONObject> = emptyList()

    init {
        loadData()
    }

    private fun loadData() {
        try {
            val itemsJson = context.assets.open("wildrift/items.json").bufferedReader().use { it.readText() }
            val itemsArray = JSONArray(itemsJson)
            items = (0 until itemsArray.length()).map { itemsArray.getJSONObject(it) }

            val heroesJson = context.assets.open("wildrift/heroes.json").bufferedReader().use { it.readText() }
            val heroesArray = JSONArray(heroesJson)
            heroes = (0 until heroesArray.length()).map { heroesArray.getJSONObject(it) }
        } catch (e: Exception) {
            // Логування помилок
        }
    }

    /** Пошук предметів, які контр-ять конкретного чемпіона. */
    fun findItemsAgainstChampion(championName: String): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        for (item in items) {
            val counters = item.optJSONArray("counters") ?: continue
            for (i in 0 until counters.length()) {
                if (counters.getString(i).contains(championName, ignoreCase = true)) {
                    result.add(item)
                    break
                }
            }
        }
        return result
    }

    /** Пошук предметів за ключовим словом (наприклад, "anti-heal"). */
    fun findItemsByMechanic(mechanic: String): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        for (item in items) {
            val mechanics = item.optJSONArray("mechanics") ?: continue
            for (i in 0 until mechanics.length()) {
                if (mechanics.getString(i).contains(mechanic, ignoreCase = true)) {
                    result.add(item)
                    break
                }
            }
        }
        return result
    }

    /** Формує текстовий контекст для LLM. */
    fun buildContextForQuery(query: String): String {
        val lowerQuery = query.lowercase()
        val matchedItems = mutableListOf<JSONObject>()

        // Пошук за назвою чемпіона
        for (hero in heroes) {
            val id = hero.optString("id", "")
            if (lowerQuery.contains(id.lowercase())) {
                matchedItems.addAll(findItemsAgainstChampion(id))
            }
        }

        // Пошук за механікою
        if (lowerQuery.contains("anti-heal") || lowerQuery.contains("heal")) {
            matchedItems.addAll(findItemsByMechanic("anti-heal"))
        }
        if (lowerQuery.contains("armor")) {
            matchedItems.addAll(findItemsByMechanic("armor"))
        }
        if (lowerQuery.contains("magic resist") || lowerQuery.contains("mr")) {
            matchedItems.addAll(findItemsByMechanic("magic resist"))
        }

        val distinctItems = matchedItems.distinctBy { it.optString("id") }

        val sb = StringBuilder()
        sb.append("Актуальні дані Wild Rift (патч 7.3c):\n")
        for (item in distinctItems.take(5)) {
            val name = item.optJSONObject("name")?.optString("en") ?: item.optString("id")
            val desc = item.optJSONObject("description")?.optString("en") ?: ""
            sb.append("- **$name**: $desc\n")
        }
        return sb.toString()
    }
}
