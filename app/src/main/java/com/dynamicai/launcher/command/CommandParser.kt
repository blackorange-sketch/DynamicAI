package com.dynamicai.launcher.command

class CommandParser {

    private val openAppRegex = Regex("^(відкрий|відкрити|запусти|open|launch)\\s+(.+)")
    private val callRegex = Regex("^(подзвони|зателефонуй|набери|call|dial)\\s+(.+)")
    private val searchRegex = Regex("^(пошук|знайди|загугли|search|google)\\s+(.+)")
    private val flashlightWords = setOf(
        "ліхтарик", "ліхтарик вкл", "ліхтарик викл",
        "увімкни ліхтарик", "вимкни ліхтарик", "ввімкни ліхтарик",
        "flashlight", "torch", "toggle flashlight"
    )

    fun parse(input: String): Command {
        val s = input.trim().lowercase()
        if (s.isEmpty()) return Command.Unknown(input)

        if (s in flashlightWords) return Command.ToggleFlashlight

        openAppRegex.matchEntire(s)?.let {
            return Command.OpenApp(it.groupValues[2].trim())
        }
        callRegex.matchEntire(s)?.let {
            return Command.Call(it.groupValues[2].trim())
        }
        searchRegex.matchEntire(s)?.let {
            return Command.WebSearch(it.groupValues[2].trim())
        }

        return Command.Unknown(input)
    }
}
