package com.dynamicai.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Champion(
    val id: String,
    val key: Int,
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
    private var ruChampionsByKey: Map<Int, String> = emptyMap()
    private var ruItemsByName: Map<String, String> = emptyMap()
    private var categories: Map<String, List<String>> = emptyMap()
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
                        key = obj.optInt("key", 0),
                        roles = roles,
                        positions = positions,
                        difficult = obj.optInt("difficult", 2)
                    )
                }
                .sortedBy { it.id }

            // Російські назви чемпіонів
            try {
                val file = java.io.File(context.filesDir, "wildrift/ru_champions_raw.json")
                val ruChampionsJson = if (file.exists()) file.bufferedReader().use { it.readText() } else "[]"
                val ruArr = JSONArray(ruChampionsJson)
                val champMap = mutableMapOf<Int, String>()
                for (i in 0 until ruArr.length()) {
                    val o = ruArr.getJSONObject(i)
                    if (o.has("id") && o.has("name")) {
                        champMap[o.getInt("id")] = o.getString("name")
                    }
                }
                ruChampionsByKey = champMap
            } catch (_: Exception) { }

            // Російські назви предметів
            try {
                val file = java.io.File(context.filesDir, "wildrift/item_names_ru.json")
                val ruItemsJson = if (file.exists()) file.bufferedReader().use { it.readText() } else "{}"
                val itemsObj = JSONObject(ruItemsJson)
                val itemMap = mutableMapOf<String, String>()
                itemsObj.keys().forEach { key -> itemMap[key] = itemsObj.getString(key) }
                ruItemsByName = itemMap
            } catch (_: Exception) { }

            val file = java.io.File(context.filesDir, "wildrift/counters.json")
                val countersJson = if (file.exists()) file.bufferedReader().use { it.readText() } else "{}"
            val countersObj = JSONObject(countersJson)
            val map = mutableMapOf<String, List<String>>()
            val catMap = mutableMapOf<String, List<String>>()
            countersObj.keys().forEach { key ->
                if (key == "_categories") {
                    val catsObj = countersObj.getJSONObject(key)
                    catsObj.keys().forEach { catKey ->
                        val arr = catsObj.getJSONArray(catKey)
                        catMap[catKey] = (0 until arr.length()).map { arr.getString(it) }
                    }
                } else {
                    val arr = countersObj.getJSONArray(key)
                    map[key] = (0 until arr.length()).map { arr.getString(it) }
                }
            }
            counters = map
            categories = catMap
        } catch (_: Exception) { }
    }


    /** Отримати російську назву чемпіона (fallback — англ). */
    fun localizedChampionName(champion: Champion): String {
        ensureLoaded()
        return ruChampionsByKey[champion.key] ?: champion.id
    }

    /** Отримати російську назву предмета (fallback — англ). */
    fun localizedItemName(englishName: String): String {
        ensureLoaded()
        return ruItemsByName[englishName] ?: englishName
    }

    /** Формат: "Мореллономикон (Morellonomicon)" — обидві назви. */
    fun localizedItemWithEnglish(englishName: String): String {
        val ru = ruItemsByName[englishName]
        return if (ru != null && ru != englishName) "$ru ($englishName)" else englishName
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

    /** Формує RAG контекст для LLM з локалізованими назвами. */
    fun buildAdviceContext(myChampion: Champion, enemies: List<Champion>): String {
        ensureLoaded()
        val sb = StringBuilder()
        val myName = localizedChampionName(myChampion)
        sb.append("Игрок играет за: $myName (${myChampion.displayRoles})\n")
        sb.append("Вражеская команда:\n")
        for (enemy in enemies) {
            sb.append("- ${localizedChampionName(enemy)} (${enemy.displayRoles})\n")
        }
        sb.append("\nКонтр-предметы (используй АНГЛИЙСКИЕ названия):\n")
        for (enemy in enemies) {
            val counterList = counters[enemy.id] ?: fallbackCounters(enemy)
            if (counterList.isNotEmpty()) {
                sb.append("- Против ${localizedChampionName(enemy)}: ${counterList.joinToString(", ")}\n")
            }
        }
        return sb.toString()
    }

    /** Повертає список предметів-іконок для рекомендації. */
        /** Повертає список item_id для іконок. */
    fun getRecommendedItems(enemies: List<Champion>): List<String> {
        ensureLoaded()
        val result = mutableSetOf<String>()
        for (enemy in enemies) {
            val counterList = counters[enemy.id] ?: fallbackCounters(enemy)
            for (name in counterList) {
                itemIdByEnglishName(name)?.let { result.add(it) }
            }
        }
        return result.toList().take(8)
    }

    /** Мапа: англ назва → item_id (для іконок). */
    fun itemIdByEnglishName(englishName: String): String? {
        ensureLoaded()
        return items.firstOrNull { it.optJSONObject("name")?.optString("en") == englishName }
            ?.optString("id")
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
