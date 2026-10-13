package com.dynamicai.launcher.command

class CommandParser {

    private val openApp = Regex("^(відкрий|відкрити|запусти|увімкни|включи|open|launch|start)\\s+(.+)")
    private val call = Regex("^(подзвони|зателефонуй|набери|дзвони|call|dial)\\s+(.+)")
    private val search = Regex("^(пошук|знайди|загугли|search|google)\\s+(.+)")
    private val alarm = Regex("^(будильник|постав будильник|встанови будильник|alarm)\\s+(на\\s+)?(.+)")
    private val timer = Regex("^(таймер|постав таймер|timer)\\s+(на\\s+)?(.+)")
    private val settings = Regex("^(налаштування|настройки|settings|відкрий налаштування|відкрий настройки)$")
    private val volumeUp = Regex("^(гучніше|збільш гучність|volume up|громче)$")
    private val volumeDown = Regex("^(тихіше|зменш гучність|volume down|тише)$")
    private val volumeMax = Regex("^(максимальна гучність|на всю гучність|max volume)$")
    private val volumeMute = Regex("^(без звуку|вимкни звук|mute|тишина)$")

    private val settingsDeep = Regex("^(налаштування|настройки|settings|відкрий налаштування|відкрий настройки|open settings)\\s+(.+)")
    private val telegramDeep = Regex("^(телеграм|telegram|tg)\\s+(@?[a-zA-Z0-9_+\\d]+)$")
    private val whatsappDeep = Regex("^(whatsapp|ватсап|вотсап|whats app|wa)\\s+(\\+?\\d[\\d\\s\\-()]*)$")
    private val instagramDeep = Regex("^(інстаграм|instagram|insta|інста)\\s+(@?[a-zA-Z0-9_.]+)$")
    private val viberDeep = Regex("^(viber|вайбер)\\s+(\\+?\\d[\\d\\s\\-()]*)$")
    private val signalDeep = Regex("^(signal|сигнал)\\s+(@?\\+?[a-zA-Z0-9_+\\d]+)$")
    private val youtubeSearch = Regex("^(youtube|ютуб|ютьюб)\\s+(.+)")
    private val mapsSearch = Regex("^(карти|maps|навігація|навигация)\\s+(.+)")
    private val spotifySearch = Regex("^(spotify|спотифай|спотіфай)\\s+(.+)")

    private val flashlight = setOf(
        "ліхтарик", "ліхтарик вкл", "ліхтарик викл",
        "увімкни ліхтарик", "вимкни ліхтарик", "ввімкни ліхтарик",
        "flashlight", "torch"
    )

    fun parse(input: String): Command {
        val s = input.trim().lowercase()
        if (s.isEmpty()) return Command.Unknown

        if (s in flashlight) return Command.ToggleFlashlight
        if (settings.matches(s)) return Command.OpenSettings(null)
        if (volumeUp.matches(s)) return Command.SetVolume(1)
        if (volumeDown.matches(s)) return Command.SetVolume(-1)
        if (volumeMax.matches(s)) return Command.SetVolume(100)
        if (volumeMute.matches(s)) return Command.SetVolume(0)

        settingsDeep.matchEntire(s)?.let { return Command.OpenSettings(it.groupValues[2].trim()) }
        telegramDeep.matchEntire(s)?.let { return Command.DeepLink("telegram", it.groupValues[2]) }
        whatsappDeep.matchEntire(s)?.let { return Command.DeepLink("whatsapp", it.groupValues[2]) }
        instagramDeep.matchEntire(s)?.let { return Command.DeepLink("instagram", it.groupValues[2]) }
        viberDeep.matchEntire(s)?.let { return Command.DeepLink("viber", it.groupValues[2]) }
        signalDeep.matchEntire(s)?.let { return Command.DeepLink("signal", it.groupValues[2]) }
        youtubeSearch.matchEntire(s)?.let { return Command.DeepLink("youtube", it.groupValues[2]) }
        mapsSearch.matchEntire(s)?.let { return Command.DeepLink("maps", it.groupValues[2]) }
        spotifySearch.matchEntire(s)?.let { return Command.DeepLink("spotify", it.groupValues[2]) }

        openApp.matchEntire(s)?.let { return Command.OpenApp(it.groupValues[2].trim()) }
        call.matchEntire(s)?.let { return Command.Call(it.groupValues[2].trim()) }
        search.matchEntire(s)?.let { return Command.WebSearch(it.groupValues[2].trim()) }

        alarm.matchEntire(s)?.let {
            val body = it.groupValues[3].trim()
            return Command.SetAlarm(body, null)
        }
        timer.matchEntire(s)?.let {
            val body = it.groupValues[3].trim()
            return Command.SetTimer(body)
        }

        return Command.Raw(input)
    }
}
