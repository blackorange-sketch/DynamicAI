package com.dynamicai.launcher.widget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class WidgetsStorage(context: Context) {

    private val prefs = context.getSharedPreferences("dynamicai_widgets", Context.MODE_PRIVATE)

    fun get(): List<PlacedWidget> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PlacedWidget(
                    id = o.getString("id"),
                    type = o.getString("type"),
                    page = o.optInt("page", 0),
                    size = o.optString("size", "medium"),
                    group = o.optString("group", "")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun add(widget: PlacedWidget) {
        val list = get().toMutableList()
        list.add(widget)
        save(list)
    }

    fun add(type: String, page: Int = 0): PlacedWidget {
        val w = PlacedWidget(
            id = UUID.randomUUID().toString(),
            type = type,
            page = page
        )
        add(w)
        return w
    }

    fun remove(id: String) {
        save(get().filterNot { it.id == id })
    }

    fun updateSize(id: String, newSize: String) {
        save(get().map { if (it.id == id) it.copy(size = newSize) else it })
    }

    fun moveToPage(id: String, newPage: Int) {
        save(get().map { if (it.id == id) it.copy(page = newPage, group = "") else it })
    }

    /** Додає віджет у стек із попереднім віджетом тієї ж сторінки. */
    fun stackWithPrevious(id: String) {
        val list = get()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        val w = list[idx]
        val prev = list.subList(0, idx).lastOrNull { it.page == w.page && it.size != "full" } ?: return
        val g = prev.group.ifBlank { UUID.randomUUID().toString() }
        save(list.map { if (it.id == id || it.id == prev.id) it.copy(group = g) else it })
    }

    fun unstack(id: String) {
        val list = get()
        val g = list.firstOrNull { it.id == id }?.group.orEmpty()
        val left = list.map { if (it.id == id) it.copy(group = "") else it }
        // якщо у стеку лишився один віджет — розформовуємо
        val remaining = left.count { g.isNotBlank() && it.group == g }
        save(if (remaining <= 1) left.map { if (g.isNotBlank() && it.group == g) it.copy(group = "") else it } else left)
    }

    /** При першому запуску — створюємо дефолтні віджети. */
    fun ensureDefaults(): List<PlacedWidget> {
        val existing = get()
        if (existing.isNotEmpty()) return existing

        val defaults = listOf(
            PlacedWidget(UUID.randomUUID().toString(), "weather_clock", 0),
            PlacedWidget(UUID.randomUUID().toString(), "notes", 0)
        )
        save(defaults)
        return defaults
    }

    /** Скільки сторінок потрібно (максимальний page + 1). */
    fun pageCount(): Int = (get().maxOfOrNull { it.page } ?: 0) + 1

    private fun save(list: List<PlacedWidget>) {
        val arr = JSONArray()
        list.forEach { w ->
            arr.put(JSONObject().apply {
                put("id", w.id)
                put("type", w.type)
                put("page", w.page)
                put("size", w.size)
                put("group", w.group)
            })
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }
}
