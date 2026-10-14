package com.dynamicai.launcher.llm

import com.dynamicai.launcher.command.Command
import org.json.JSONObject

class LlmAgent(private val engine: LlmEngine) {

    private val systemPrompt = """
Ти — локальний AI-агент у лаунчері Android. Перетворюй запити у виклики функцій.

ВАЖЛИВО: відповідай ЛИШЕ у форматі JSON:
{"tool": "назва", "args": {"параметр": "значення"}}

Функції:
- open_app(name): відкрити застосунок
- web_search(query): пошук Google
- call(name): подзвонити
- set_alarm(time, label): будильник
- set_timer(duration): таймер
- toggle_flashlight(): ліхтарик
- set_volume(level): гучність ('up', 'down', 'max', 'mute')
- set_brightness(level): яскравість ('up', 'down', 'max', 'auto_on', 'auto_off')
- set_toggle(target, enabled): увімкнути/вимкнути
  target: 'wifi' | 'bluetooth' | 'mobile_data' | 'airplane' | 'location' | 'nfc' | 'dnd' | 'auto_rotate' | 'battery_saver'
  enabled: true | false
- create_note(text): нотатка
- media_play(): відтворити музику
- media_pause(): пауза
- media_next(): наступний трек
- media_prev(): попередній трек

СКЛАДНІ ЗАПИТИ (кілька дій) — повертай масив "steps":
{"steps": [{"tool": "...", "args": {...}}, {"tool": "...", "args": {...}}]}

Приклади:
"відкрий телеграм" → {"tool": "open_app", "args": {"name": "telegram"}}
"увімкни ліхтарик" → {"tool": "toggle_flashlight", "args": {}}
"вимкни wifi" → {"tool": "set_toggle", "args": {"target": "wifi", "enabled": false}}
"яскравіше" → {"tool": "set_brightness", "args": {"level": "up"}}
"відкрий spotify і включи музику" → {"steps": [{"tool": "open_app", "args": {"name": "spotify"}}, {"tool": "media_play", "args": {}}]}
"увімкни wifi і блютуз" → {"steps": [{"tool": "set_toggle", "args": {"target": "wifi", "enabled": true}}, {"tool": "set_toggle", "args": {"target": "bluetooth", "enabled": true}}]}

Якщо не розумієш — {"tool": "unknown", "args": {}}.
""".trimIndent()

    suspend fun resolve(userText: String): Command? {
        if (!engine.isReady) return null

        val prompt = "$systemPrompt\n\nЗапит: \"$userText\"\nВідповідь:"
        val raw = engine.complete(prompt) ?: return null
        val cleaned = raw.replace("```json", "").replace("```", "").trim()
        return parseCompound(cleaned, userText) ?: parseSingleTool(cleaned, userText)
    }

    private fun parseCompound(raw: String, original: String): Command? {
        val json = extractJsonObject(raw) ?: return null
        return try {
            val obj = JSONObject(json)
            val steps = obj.optJSONArray("steps") ?: return null
            val cmds = mutableListOf<Command>()
            for (i in 0 until steps.length()) {
                val stepJson = steps.optJSONObject(i)?.toString() ?: continue
                parseSingleTool(stepJson, original)?.let { cmds.add(it) }
            }
            when {
                cmds.isEmpty() -> null
                cmds.size == 1 -> cmds.first()
                else -> Command.Compound(cmds, delayMs = 1000L)
            }
        } catch (_: Throwable) { null }
    }

    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inStr = false
        var esc = false
        for (i in start until raw.length) {
            val c = raw[i]
            if (inStr) {
                if (esc) esc = false
                else if (c == '\\') esc = true
                else if (c == '"') inStr = false
            } else when (c) {
                '"' -> inStr = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return raw.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun parseSingleTool(json: String, original: String): Command? {
        val toolRe = Regex("\"tool\"\\s*:\\s*\"([^\"]+)\"")
        val tool = toolRe.find(json)?.groupValues?.get(1) ?: return null

        fun arg(name: String): String? =
            Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)

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
            "open_settings" -> Command.OpenSettings(arg("section"))
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
            "set_brightness" -> {
                val lvl = arg("level") ?: return null
                when (lvl.lowercase()) {
                    "up" -> Command.SetBrightness(-1)
                    "down" -> Command.SetBrightness(-2)
                    "max" -> Command.SetBrightness(255)
                    "auto_on" -> Command.SetToggle("auto_brightness", true)
                    "auto_off" -> Command.SetToggle("auto_brightness", false)
                    else -> Command.SetBrightness(lvl.toIntOrNull() ?: 128)
                }
            }
            "set_toggle" -> {
                val target = arg("target") ?: return null
                val enabledStr = Regex("\"enabled\"\\s*:\\s*(true|false)").find(json)?.groupValues?.get(1) ?: "false"
                Command.SetToggle(target, enabledStr == "true")
            }
            "create_note" -> arg("text")?.let { Command.Note(it) }
            "media_play" -> Command.MediaControl("play")
            "media_pause" -> Command.MediaControl("pause")
            "media_next" -> Command.MediaControl("next")
            "media_prev" -> Command.MediaControl("prev")
            "unknown" -> Command.Raw(original)
            else -> null
        }
    }
}
