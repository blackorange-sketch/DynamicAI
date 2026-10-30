package com.dynamicai.launcher.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Резервна копія налаштувань, макросів, нотаток, віджетів, папок, дока, розмов — одним JSON-файлом. */
object BackupManager {
    private val PREFS = listOf(
        "dynamicai_settings", "dynamicai_macros", "dynamicai_notes", "dynamicai_widgets", "dynamicai_folders",
        "dynamicai_dock", "dynamicai_hidden", "dynamicai_aliases", "dynamicai_custom", "chat_archive",
        "chat_history", "focus_modes", "macro_usage"
    )

    fun export(ctx: Context): String {
        val root = JSONObject().put("app", "DynamicAI").put("version", 1).put("created", System.currentTimeMillis())
        val all = JSONObject()
        for (name in PREFS) {
            val p = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
            val o = JSONObject()
            for ((k, v) in p.all) {
                val e = JSONObject()
                when (v) {
                    is Boolean -> e.put("t", "b").put("v", v)
                    is Int -> e.put("t", "i").put("v", v)
                    is Long -> e.put("t", "l").put("v", v)
                    is Float -> e.put("t", "f").put("v", v.toDouble())
                    is String -> e.put("t", "s").put("v", v)
                    is Set<*> -> e.put("t", "S").put("v", JSONArray(v.map { it.toString() }))
                    else -> continue
                }
                o.put(k, e)
            }
            all.put(name, o)
        }
        return root.put("prefs", all).toString(1)
    }

    /** Відновлює дані; повертає кількість відновлених розділів. */
    fun import(ctx: Context, json: String): Int {
        val root = JSONObject(json)
        require(root.optString("app") == "DynamicAI") { "Це не резервна копія DynamicAI" }
        val all = root.getJSONObject("prefs")
        var n = 0
        for (name in PREFS) {
            val o = all.optJSONObject(name) ?: continue
            val ed = ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            for (k in o.keys()) {
                val e = o.getJSONObject(k)
                when (e.getString("t")) {
                    "b" -> ed.putBoolean(k, e.getBoolean("v"))
                    "i" -> ed.putInt(k, e.getInt("v"))
                    "l" -> ed.putLong(k, e.getLong("v"))
                    "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                    "s" -> ed.putString(k, e.getString("v"))
                    "S" -> {
                        val a = e.getJSONArray("v")
                        ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                    }
                }
            }
            ed.commit()
            n++
        }
        return n
    }
}
