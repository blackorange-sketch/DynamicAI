package com.dynamicai.launcher.data

import android.content.Context
import com.dynamicai.launcher.model.AppInfo
import org.json.JSONArray
import org.json.JSONObject

class AppsCache(context: Context) {
    private val prefs = context.getSharedPreferences("dynamicai_apps_cache", Context.MODE_PRIVATE)

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

    fun loadPackageNames(): List<String> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it).getString("pkg") }
        } catch (_: Exception) { emptyList() }
    }

    fun isEmpty(): Boolean = prefs.getString("items", null).isNullOrBlank()
}
