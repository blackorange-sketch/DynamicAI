package com.dynamicai.launcher.voice

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessage(val role: String, val text: String, val timestamp: Long = System.currentTimeMillis())

class VoiceChatStorage(context: Context) {
    private val prefs = context.getSharedPreferences("dynamicai_voice_chat", Context.MODE_PRIVATE)
    private val maxMessages = 50

    fun load(): List<ChatMessage> {
        val raw = prefs.getString("history", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ChatMessage(
                    role = o.getString("role"),
                    text = o.getString("text"),
                    timestamp = o.optLong("ts", 0L)
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun save(messages: List<ChatMessage>) {
        val arr = JSONArray()
        messages.takeLast(maxMessages).forEach { m ->
            arr.put(JSONObject().apply {
                put("role", m.role)
                put("text", m.text)
                put("ts", m.timestamp)
            })
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    fun clear() { prefs.edit().remove("history").apply() }
}
