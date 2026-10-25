package com.dynamicai.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Champion(
    val id: String,
    val roles: List<String>,
    val positions: List<String>,
    val difficult: Int
) {
    val displayRoles: String
        get() = roles.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
}

class WildRiftRepository(private val context: Context) {

    private var items: List<JSONObject> = emptyList()
    private var championsList: List<Champion> = emptyList()
    private var counters: Map<String, List<String>> = emptyMap()
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            val itemsJson = context.assets.open("wildrift/items.json").bufferedReader().use { it.readText() }
            val itemsArray = JSONArray(itemsJson)
            items = (0 until itemsArray.length()).map { itemsArray.getJSONObject(it) }

            val heroesJson = context.assets.open("wildrift/heroes.json").bufferedReader().use { it.readText() }
            val heroesArray = JSONArray(heroesJson)
            championsList = (0 until heroesArray.length())
                .map { heroesArray.getJSONObject(it) }
                .filter { it.optBoolean("is_wr", false) }
                .map { obj ->
                    val roles = mutableListOf<String>()
                    if (obj.optBoolean("is_fighter")) roles.add("fighter")
                    if (obj.optBoolean("is_mage")) roles.add("mage")
                    if (obj.optBoolean("is_assassin")) roles.add("assassin")
                    if (obj.optBoolean("is_marksman")) roles.add("marksman")
                    if (obj.optBoolean("is_support")) roles.add("support")
                    if (obj.optBoolean("is_tank")) roles.add("tank")

                    val positions = mutableListOf<String>()
                    if (obj.optBoolean("is_top")) positions.add("top")
                    if (obj.optBoolean("is_jg")) positions.add("jg")
                    if (obj.optBoolean("is_mid")) positions.add("mid")
                    if (obj.optBoolean("is_sup")) positions.add("sup")
                    if (obj.optBoolean("is_ad")) positions.add("ad")

                    Champion(
                        id = obj.optString("id"),
                        roles = roles,
                        positions = positions,
                        difficult = obj.optInt("difficult", 2)
                    )
                }
                .sortedBy { it.id }

            val countersJson = context.assets.open("wildrift/counters.json").bufferedReader().use { it.readText() }
            val countersObj = JSONObject(countersJson)
            val map = mutableMapOf<String, List<String>>()
            countersObj.keys().forEach { key ->
                val arr = countersObj.getJSONArray(key)
                map[key] = (0 until arr.length()).map { arr.getString(it) }
            }
            counters = map
        } catch (_: Exception) { }
    }

    fun getChampions(): List<Champion> {
        ensureLoaded()
        return championsList
    }

    fun findChampion(name: String): Champion? {
        ensureLoaded()
        return championsList.firstOrNull { it.id.equals(name, ignoreCase = true) }
    }

    /** Fallback по ролях — якщо немає точного counters.json для чемпіона. */
    private fun fallbackCounters(champ: Champion): List<String> {
        val result = mutableListOf<String>()
        if ("assassin" in champ.roles || "mage" in champ.roles) {
            result.add("Zhonya's Hourglass")
            result.add("Banshee's Veil")
        }
        if ("tank" in champ.roles || "fighter" in champ.roles) {
            result.add("Black Cleaver")
        }
        if ("marksman" in champ.roles) {
            result.add("Randuin's Omen")
            result.add("Thornmail")
        }
        if ("support" in champ.roles) {
            result.add("Mikael's Blessing")
            result.add("Redemption")
        }
        return result.distinct()
    }

    /** Формує RAG контекст для LLM. */
    fun buildAdviceContext(myChampion: Champion, enemies: List<Champion>): String {
        ensureLoaded()
        val sb = StringBuilder()
        sb.append("Гравець грає за: ${myChampion.id} (${myChampion.displayRoles})\n")
        sb.append("Ворожа команда:\n")
        for (enemy in enemies) {
            sb.append("- ${enemy.id} (${enemy.displayRoles})\n")
        }
        sb.append("\nКонтр-предмети проти ворогів:\n")
        for (enemy in enemies) {
            val specific = counters[enemy.id]
            val counterList = specific ?: fallbackCounters(enemy)
            if (counterList.isNotEmpty()) {
                sb.append("- Проти ${enemy.id}: ${counterList.joinToString(", ")}\n")
            }
        }
        return sb.toString()
    }

    fun buildContextForQuery(query: String): String {
        val lower = query.lowercase()
        ensureLoaded()
        val matched = counters.keys.filter { lower.contains(it.lowercase()) }
        if (matched.isEmpty()) return ""
        val sb = StringBuilder()
        for (champ in matched) {
            sb.append("$champ → ${counters[champ]?.joinToString(", ") ?: ""}\n")
        }
        return sb.toString()
    }
}
