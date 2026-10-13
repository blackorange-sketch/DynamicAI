package com.dynamicai.launcher.llm

import com.google.ai.edge.litertlm.tool
import com.google.ai.edge.litertlm.ToolSet
import com.google.ai.edge.litertlm.Tool

/**
 * Набір інструментів, які LLM може викликати.
 * Повертає JSON-подібний виклик, який ми потім конвертуємо в Command.
 */
class LauncherToolSet(private val callback: (String, Map<String, String>) -> Unit) : ToolSet {

    @Tool(description = "Open an application by name. Examples: telegram, youtube, camera")
    fun open_app(name: String) {
        callback("open_app", mapOf("name" to name))
    }

    @Tool(description = "Search the web with a query")
    fun web_search(query: String) {
        callback("web_search", mapOf("query" to query))
    }

    @Tool(description = "Call a contact or phone number")
    fun call(name: String) {
        callback("call", mapOf("name" to name))
    }

    @Tool(description = "Set an alarm at a specific time. Time format: HH:MM or just hour number")
    fun set_alarm(time: String, label: String = "") {
        callback("set_alarm", mapOf("time" to time, "label" to label))
    }

    @Tool(description = "Set a timer for a duration. Examples: 5 minutes, 1 hour, 30 seconds")
    fun set_timer(duration: String) {
        callback("set_timer", mapOf("duration" to duration))
    }

    @Tool(description = "Toggle the flashlight on or off")
    fun toggle_flashlight() {
        callback("toggle_flashlight", emptyMap())
    }

    @Tool(description = "Open system settings")
    fun open_settings() {
        callback("open_settings", emptyMap())
    }

    @Tool(description = "Change media volume. Level: 'up', 'down', 'max', or 'mute'")
    fun set_volume(level: String) {
        callback("set_volume", mapOf("level" to level))
    }
}
