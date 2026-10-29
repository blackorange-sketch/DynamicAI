package com.dynamicai.launcher.macro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MacroStorage(context: Context) {

    private val prefs = context.getSharedPreferences("dynamicai_macros", Context.MODE_PRIVATE)

    fun get(): List<Macro> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val stepsArr = o.optJSONArray("steps") ?: JSONArray()
                val steps = (0 until stepsArr.length()).map { stepsArr.getString(it) }
                Macro(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    icon = o.optString("icon", "⚡"),
                    colorHex = o.optLong("colorHex", 0xFF2196F3),
                    trigger = o.optString("trigger", ""),
                    steps = steps,
                    delayMs = o.optLong("delayMs", 900L),
                    stopOnError = o.optBoolean("stopOnError", false),
                    triggers = o.optJSONArray("triggers")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                    constraints = o.optString("constraints", ""),
                    enabled = o.optBoolean("enabled", true)
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun add(m: Macro) {
        val list = get().toMutableList()
        list.removeAll { it.id == m.id }
        list.add(m)
        save(list)
    }

    fun create(name: String, steps: List<String>, delayMs: Long = 900L): Macro {
        val m = Macro(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Макрос" },
            steps = steps.map { it.trim() }.filter { it.isNotBlank() },
            delayMs = delayMs
        )
        add(m)
        return m
    }

    fun remove(id: String) = save(get().filterNot { it.id == id })

    /** Глобальний вимикач автозапуску макросів за подіями. */
    fun autoEnabled(): Boolean = prefs.getBoolean("auto_enabled", true)
    fun setAutoEnabled(on: Boolean) { prefs.edit().putBoolean("auto_enabled", on).apply() }

    private fun phrases(m: Macro): List<String> =
        m.trigger.split(',', ';').map { it.trim().lowercase() }.filter { it.isNotBlank() }

    fun findByName(name: String): Macro? {
        val n = name.trim().lowercase()
        return get().firstOrNull { it.name.lowercase() == n || n in phrases(it) }
    }

    /** Фраза користувача → макрос: точний збіг назви/тригера, або тригер з 2+ слів міститься у фразі. */
    fun matchUtterance(text: String): Macro? {
        val t = text.trim().lowercase().trimEnd('.', '!', '?', ',', ' ')
        findByName(t)?.let { return it }
        return get().firstOrNull { m -> phrases(m).any { it.split(' ').size >= 2 && t.contains(it) } }
    }

    private fun save(list: List<Macro>) {
        val arr = JSONArray()
        list.forEach { m ->
            val o = JSONObject()
            o.put("id", m.id)
            o.put("name", m.name)
            o.put("icon", m.icon)
            o.put("colorHex", m.colorHex)
            o.put("trigger", m.trigger)
            o.put("delayMs", m.delayMs)
            o.put("stopOnError", m.stopOnError)
            o.put("constraints", m.constraints)
            o.put("enabled", m.enabled)
            o.put("triggers", JSONArray().also { a -> m.triggers.forEach { a.put(it) } })
            val stepsArr = JSONArray()
            m.steps.forEach { stepsArr.put(it) }
            o.put("steps", stepsArr)
            arr.put(o)
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    fun delete(id: String) {
        val list = get().toMutableList()
        list.removeAll { it.id == id }
        save(list)
    }
}
