package com.dynamicai.launcher.overlay

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/**
 * @param role "user" | "assistant" | "status" | "error"
 * @param action для role="error": "restart" (перезапустити рушій) | "retry" (повторити запит)
 */
data class ChatMessage(val role: String, val content: String, val action: String? = null)

object ChatHistoryManager {
    val history = mutableStateListOf<ChatMessage>()
    private var prefs: SharedPreferences? = null

    /** Підключає збереження історії (викликати один раз; повторні виклики ігноруються). */
    fun init(context: Context) {
        if (prefs != null) return
        ChatArchive.init(context)
        val p = context.applicationContext.getSharedPreferences("chat_history", Context.MODE_PRIVATE)
        prefs = p
        if (history.isEmpty()) {
            try {
                val arr = JSONArray(p.getString("items", "[]") ?: "[]")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    history.add(ChatMessage(o.getString("r"), o.getString("c")))
                }
            } catch (_: Throwable) {}
        }
    }

    fun addMessage(role: String, content: String, action: String? = null) {
        history.add(ChatMessage(role, content, action))
        // Обмежуємо історію 50 повідомленнями, щоб не жерти пам'ять
        if (history.size > 50) history.removeAt(0)
        persist()
    }

    /** Прибирає тимчасові «Думаю…». */
    fun removeStatus() {
        history.removeAll { it.role == "status" }
    }

    /** Нова розмова: поточна зберігається в архів. */
    fun clear() {
        ChatArchive.archive(history.toList())
        history.clear()
        persist()
    }

    /** Відкриває розмову з архіву; поточна спершу зберігається. */
    fun restore(id: String) {
        val chat = ChatArchive.get(id) ?: return
        if (ChatArchive.currentId != id) ChatArchive.archive(history.toList())
        history.clear()
        history.addAll(chat.messages)
        ChatArchive.currentId = id
        persist()
    }

    private fun persist() {
        val p = prefs ?: return
        try {
            val arr = JSONArray()
            history.filter { it.role == "user" || it.role == "assistant" }.forEach {
                arr.put(JSONObject().put("r", it.role).put("c", it.content))
            }
            p.edit().putString("items", arr.toString()).apply()
        } catch (_: Throwable) {}
    }
}
