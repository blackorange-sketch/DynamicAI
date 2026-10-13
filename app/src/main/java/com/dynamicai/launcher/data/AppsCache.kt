package com.dynamicai.launcher.data

import android.content.Context
import com.dynamicai.launcher.model.AppInfo
import org.json.JSONArray
import org.json.JSONObject

class AppsCache(context: Context) {
    private val prefs = context.getSharedPreferences("dynamicai_apps_cache", Context.MODE_PRIVATE)

    data class CachedApp(val label: String, val packageName: String, val isSystem: Boolean)

    fun save(apps: List<AppInfo>) {
        try {
            val arr = JSONArray()
            apps.forEach { a ->
                arr.put(JSONObject().apply {
                    put("label", a.label)
                    put("pkg", a.packageName)
                    put("sys", a.isSystem)
                })
            }
            prefs.edit().putString("items", arr.toString())
                .putLong("savedAt", System.currentTimeMillis())
                .commit()
        } catch (_: Exception) {}
    }

    fun load(): List<CachedApp> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CachedApp(
                    label = o.getString("label"),
                    packageName = o.getString("pkg"),
                    isSystem = o.optBoolean("sys", false)
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun isEmpty(): Boolean = prefs.getString("items", null).isNullOrBlank()
    fun lastSavedAt(): Long = prefs.getLong("savedAt", 0L)
}
