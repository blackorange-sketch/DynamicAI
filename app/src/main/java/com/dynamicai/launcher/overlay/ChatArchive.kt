package com.dynamicai.launcher.overlay

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedChat(
    val id: String,
    val title: String,
    val ts: Long,
    val pinned: Boolean,
    val messages: List<ChatMessage>
)

/** Архів розмов: при «нова розмова» поточна зберігається; можна шукати, закріплювати, відкривати знову. */
object ChatArchive {
    private var prefs: SharedPreferences? = null
    private const val MAX = 100

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("chat_archive", Context.MODE_PRIVATE)
    }

    /** id розмови, відкритої з архіву (щоб при повторному збереженні оновити її, а не дублювати). */
    var currentId: String?
        get() = prefs?.getString("current", null)
        set(v) { prefs?.edit()?.apply { if (v == null) remove("current") else putString("current", v) }?.apply() }

    fun list(): List<SavedChat> {
        val raw = prefs?.getString("items", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ms = o.getJSONArray("m")
                SavedChat(
                    id = o.getString("id"), title = o.optString("t", "Розмова"), ts = o.optLong("ts", 0L),
                    pinned = o.optBoolean("p", false),
                    messages = (0 until ms.length()).map { j -> ms.getJSONArray(j).let { ChatMessage(it.getString(0), it.getString(1)) } }
                )
            }
        } catch (_: Throwable) { emptyList() }
    }

    /** Відсортовано: закріплені, потім нові. */
    fun sorted(): List<SavedChat> = list().sortedWith(compareByDescending<SavedChat> { it.pinned }.thenByDescending { it.ts })

    fun get(id: String): SavedChat? = list().firstOrNull { it.id == id }

    private fun save(list: List<SavedChat>) {
        val p = prefs ?: return
        try {
            val arr = JSONArray()
            list.forEach { c ->
                val ms = JSONArray()
                c.messages.forEach { ms.put(JSONArray().put(it.role).put(it.content)) }
                arr.put(JSONObject().put("id", c.id).put("t", c.title).put("ts", c.ts).put("p", c.pinned).put("m", ms))
            }
            p.edit().putString("items", arr.toString()).apply()
        } catch (_: Throwable) {}
    }

    /** Зберігає розмову в архів (порожні та без реплік користувача ігноруються). */
    fun archive(messages: List<ChatMessage>) {
        val real = messages.filter { it.role == "user" || it.role == "assistant" }
        val first = real.firstOrNull { it.role == "user" }
        if (first == null) { currentId = null; return }
        val all = list().toMutableList()
        val id = currentId
        val old = all.firstOrNull { it.id == id }
        all.removeAll { it.id == id }
        val title = first.content.replace("\n", " ").trim().take(48)
        all.add(SavedChat(id ?: UUID.randomUUID().toString(), title, System.currentTimeMillis(), old?.pinned ?: false, real))
        // Обмеження: прибираємо найстаріші незакріплені
        while (all.size > MAX) {
            val victim = all.filter { !it.pinned }.minByOrNull { it.ts } ?: break
            all.remove(victim)
        }
        save(all)
        currentId = null
    }

    fun delete(id: String) { save(list().filterNot { it.id == id }) }

    fun togglePin(id: String) { save(list().map { if (it.id == id) it.copy(pinned = !it.pinned) else it }) }
}
