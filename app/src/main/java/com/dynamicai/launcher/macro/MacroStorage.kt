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
                    trigger = o.optString("trigger", ""),
                    steps = steps,
                    delayMs = o.optLong("delayMs", 900L)
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

    fun findByName(name: String): Macro? {
        val n = name.trim().lowercase()
        return get().firstOrNull {
            it.name.lowercase() == n || (it.trigger.isNotBlank() && it.trigger.lowercase() == n)
        }
    }

    private fun save(list: List<Macro>) {
        val arr = JSONArray()
        list.forEach { m ->
            val o = JSONObject()
            o.put("id", m.id)
            o.put("name", m.name)
            o.put("trigger", m.trigger)
            o.put("delayMs", m.delayMs)
            val stepsArr = JSONArray()
            m.steps.forEach { stepsArr.put(it) }
            o.put("steps", stepsArr)
            arr.put(o)
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }
}
