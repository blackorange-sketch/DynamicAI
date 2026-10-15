package com.dynamicai.launcher.note

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class NotesStorage(context: Context) {

    private val prefs = context.getSharedPreferences("dynamicai_notes", Context.MODE_PRIVATE)

    fun get(): List<Note> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Note(
                    id = o.getString("id"),
                    text = o.getString("text"),
                    createdAt = o.optLong("createdAt", 0L),
                    done = o.optBoolean("done", false),
                    reminderAt = if (o.has("reminderAt")) o.optLong("reminderAt") else null
                )
            }.sortedByDescending { it.createdAt }
        } catch (_: Exception) { emptyList() }
    }

    fun add(text: String): Note {
        val n = Note(
            id = UUID.randomUUID().toString(),
            text = text.trim(),
            createdAt = System.currentTimeMillis()
        )
        val list = get().toMutableList()
        list.add(0, n)
        save(list)
        return n
    }

    fun toggle(id: String) {
        val list = get().map { if (it.id == id) it.copy(done = !it.done) else it }
        save(list)
    }

    fun remove(id: String) {
        save(get().filterNot { it.id == id })
    }

    fun update(note: Note) {
        val list = get().map { if (it.id == note.id) note else it }
        save(list)
    }

    fun count(): Int = get().count { !it.done }

    private fun save(list: List<Note>) {
        val arr = JSONArray()
        list.forEach { n ->
            arr.put(JSONObject().apply {
                put("id", n.id)
                put("text", n.text)
                put("createdAt", n.createdAt)
                put("done", n.done)
                n.reminderAt?.let { put("reminderAt", it) }
            })
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }
}
