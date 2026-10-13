package com.dynamicai.launcher.llm

import com.dynamicai.launcher.command.Command

class LlmAgent(private val engine: LlmEngine) {

    private val systemPrompt = """
Ти — локальний AI-асистент у лаунчері Android. Твоє завдання — перетворювати
природну мову користувача на виклики функцій. Відповідай ЛИШЕ викликом функції,
без зайвого тексту. Якщо не впевнений — обери найближчу за змістом функцію.

Доступні функції:
- open_app(name): відкрити застосунок
- web_search(query): пошук в інтернеті
- call(name): подзвонити
- set_alarm(time, label): будильник (time у форматі HH:MM або година)
- set_timer(duration): таймер ("5 minutes", "1 hour")
- toggle_flashlight(): ліхтарик
- open_settings(): налаштування
- set_volume(level): гучність ('up', 'down', 'max', 'mute')

Відповідай JSON у форматі:
{"tool": "назва_функції", "args": {"параметр": "значення"}}
""".trimIndent()

    suspend fun resolve(userText: String): Command? {
        if (!engine.isReady) return null
        val prompt = "$systemPrompt\n\nЗапит: \"$userText\"\nВідповідь:"
        val raw = engine.complete(prompt) ?: return null
        return parseToolCall(raw, userText)
    }

    private fun parseToolCall(raw: String, original: String): Command? {
        val toolRe = Regex("\"tool\"\\s*:\\s*\"([^\"]+)\"")
        val tool = toolRe.find(raw)?.groupValues?.get(1) ?: return null

        fun arg(name: String): String? =
            Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(raw)?.groupValues?.get(1)

        return when (tool) {
            "open_app" -> arg("name")?.let { Command.OpenApp(it) }
            "web_search" -> arg("query")?.let { Command.WebSearch(it) }
            "call" -> arg("name")?.let { Command.Call(it) }
            "set_alarm" -> {
                val time = arg("time") ?: return null
                Command.SetAlarm(time, arg("label")?.takeIf { it.isNotBlank() })
            }
            "set_timer" -> arg("duration")?.let { Command.SetTimer(it) }
            "toggle_flashlight" -> Command.ToggleFlashlight
            "open_settings" -> Command.OpenSettings(null)
            "set_volume" -> {
                val lvl = arg("level") ?: return null
                Command.SetVolume(
                    when (lvl.lowercase()) {
                        "up" -> 1
                        "down" -> -1
                        "max" -> 100
                        "mute" -> 0
                        else -> 1
                    }
                )
            }
            else -> null
        }
    }
}
