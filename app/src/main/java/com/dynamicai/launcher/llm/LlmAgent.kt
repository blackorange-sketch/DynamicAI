package com.dynamicai.launcher.llm

import com.dynamicai.launcher.command.Command

class LlmAgent(private val engine: LlmEngine) {

    private val systemPrompt = """
Ти — локальний AI-агент у лаунчері Android. Твоє завдання — перетворювати
запити користувача у виклики функцій (tool calls).

ВАЖЛИВО: відповідай ЛИШЕ у форматі JSON, без жодного тексту навколо:
{"tool": "назва", "args": {"параметр": "значення"}}

Доступні функції:
- open_app(name): відкрити застосунок. name — назва (telegram, youtube, camera)
- web_search(query): пошук в інтернеті
- call(name): подзвонити контакту
- set_alarm(time, label): будильник
- set_timer(duration): таймер
- toggle_flashlight(): ліхтарик
- open_settings(section): налаштування
- set_volume(level): гучність ('up', 'down', 'max', 'mute')

Приклади:

Запит: "відкрий телеграм"
Відповідь: {"tool": "open_app", "args": {"name": "telegram"}}

Запит: "увімкни ліхтарик"
Відповідь: {"tool": "toggle_flashlight", "args": {}}

Запит: "постав будильник на 7:30"
Відповідь: {"tool": "set_alarm", "args": {"time": "7:30", "label": ""}}

Запит: "гучніше"
Відповідь: {"tool": "set_volume", "args": {"level": "up"}}

Якщо запит не підходить до жодної функції — відповідай {"tool": "unknown", "args": {}}.
.trimIndent()

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
