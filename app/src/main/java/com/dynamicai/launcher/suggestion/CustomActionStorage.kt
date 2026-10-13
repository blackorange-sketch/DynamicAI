package com.dynamicai.launcher.suggestion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CustomAction(
    val id: String,
    val trigger: String,
    val label: String,
    val commandType: String,
    val commandArg: String
)

class CustomActionStorage(context: Context) {
    private val prefs = context.getSharedPreferences("dynamicai_custom", Context.MODE_PRIVATE)

    fun get(): List<CustomAction> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CustomAction(
                    id = o.getString("id"),
                    trigger = o.getString("trigger"),
                    label = o.getString("label"),
                    commandType = o.getString("type"),
                    commandArg = o.optString("arg", "")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun add(action: CustomAction) {
        val list = get().toMutableList()
        list.removeAll { it.id == action.id }
        list.add(action)
        save(list)
    }

    fun remove(id: String) = save(get().filterNot { it.id == id })

    private fun save(list: List<CustomAction>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id)
                put("trigger", a.trigger)
                put("label", a.label)
                put("type", a.commandType)
                put("arg", a.commandArg)
            })
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }
}
