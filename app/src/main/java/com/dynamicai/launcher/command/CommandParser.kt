package com.dynamicai.launcher.command

class CommandParser {

    private val openApp = Regex("^(відкрий|відкрити|запусти|увімкни|включи|open|launch|start)\\s+(.+)")
    private val call = Regex("^(подзвони|зателефонуй|набери|дзвони|call|dial)\\s+(.+)")
    private val search = Regex("^(пошук|знайди|загугли|search|google)\\s+(.+)")
    private val alarm = Regex("^(будильник|постав будильник|встанови будильник|alarm)\\s+(на\\s+)?(.+)")
    private val timer = Regex("^(таймер|постав таймер|timer)\\s+(на\\s+)?(.+)")
    private val settings = Regex("^(налаштування|настройки|settings)$")
    private val noteRegex = Regex("^(нотатка|запиши|занотуй|note|запам'ятай)\\s+(.+)")

    // Питання
    private val questionStart = Regex("^(хто|що|де|коли|як|чому|скільки|яка|який|яке|чи |розкажи|поясни|who|what|where|when|how|why)\\b.*")
    private val questionEnd = Regex(".*\\?$")

    // Яскравість
    private val brightnessUp = Regex(".*(яскравіше|збільш яскравість|підвищ яскравість|brightness up|яскравість вгору).*")
    private val brightnessDown = Regex(".*(темніше|зменш яскравість|знизь яскравість|brightness down|яскравість вниз).*")
    private val brightnessMax = Regex(".*(максимальна яскравість|макс яскравість|brightness max|на повну яскравість).*")
    private val brightnessAutoOn = Regex(".*(увімкни авто ?яскравість|авто ?яскравість ?увімкни|auto ?brightness on).*")
    private val brightnessAutoOff = Regex(".*(вимкни авто ?яскравість|авто ?яскравість ?вимкни|auto ?brightness off).*")

    // Гучність
    private val volumeUp = Regex(".*(гучніше|збільш гучність|volume up|громче|додай гучності|гучність вгору).*")
    private val volumeDown = Regex(".*(тихіше|зменш гучність|volume down|тише|прибери гучність|гучність вниз).*")
    private val volumeMax = Regex(".*(максимальна гучність|на всю гучність|max volume|повна гучність).*")
    private val volumeMute = Regex(".*(без звуку|вимкни звук|mute|тишина|заглуши звук).*")

    // Toggle пристроїв
    private val toggleTargets = mapOf(
        "wifi" to listOf("wifi", "wi-fi", "вайфай", "вай-фай", "вай фай"),
        "bluetooth" to listOf("bluetooth", "блютуз", "блутуз", "зуб"),
        "mobile_data" to listOf("мобільн", "мобильн", "mobile data", "інтернет", "данные", "стільников"),
        "airplane" to listOf("авіарежим", "авиарежим", "airplane", "політ"),
        "location" to listOf("gps", "геолокація", "локація", "місцезнаходження", "location"),
        "nfc" to listOf("nfc", "нфс"),
        "dnd" to listOf("не турбувати", "dnd", "днд", "тихий режим", "без звуку режим"),
        "auto_rotate" to listOf("автоповорот", "авто поворот", "auto rotate", "поворот екрану"),
        "battery_saver" to listOf("економія батареї", "battery saver", "енергозбереження"),
    )

    private val onWords = listOf("увімкни", "включи", "вмикай", "активуй", "on", "enable", "включить", "увімкнути")
    private val offWords = listOf("вимкни", "виключи", "виключай", "деактивуй", "off", "disable", "виключить", "вимкнути")

    private val flashlightWords = setOf(
        "ліхтарик", "ліхтарик вкл", "ліхтарик викл",
        "увімкни ліхтарик", "вимкни ліхтарик", "ввімкни ліхтарик",
        "flashlight", "torch"
    )

    private val screenshotWords = setOf(
        "скріншот", "скрін", "зроби скріншот", "знімок екрану",
        "screenshot", "capture screen"
    )

    fun parse(input: String): Command {
        val s = input.trim().lowercase()
        if (s.isEmpty()) return Command.Unknown

        // ==== Точні фрази ====
        if (s in flashlightWords) return Command.ToggleFlashlight
        if (s in screenshotWords) return Command.Screenshot
        if (settings.matches(s)) return Command.OpenSettings(null)

        // ==== Яскравість ====
        if (brightnessAutoOn.matches(s)) return Command.SetToggle("auto_brightness", true)
        if (brightnessAutoOff.matches(s)) return Command.SetToggle("auto_brightness", false)
        if (brightnessMax.matches(s)) return Command.SetBrightness(255)
        if (brightnessUp.matches(s)) return Command.SetBrightness(-1)
        if (brightnessDown.matches(s)) return Command.SetBrightness(-2)

        // ==== Гучність ====
        if (volumeMax.matches(s)) return Command.SetVolume(100)
        if (volumeMute.matches(s)) return Command.SetVolume(0)
        if (volumeUp.matches(s)) return Command.SetVolume(1)
        if (volumeDown.matches(s)) return Command.SetVolume(-1)

        // ==== Toggle пристроїв ====
        val isOn = onWords.any { s.contains(it) }
        val isOff = offWords.any { s.contains(it) }
        if (isOn || isOff) {
            for ((target, keywords) in toggleTargets) {
                if (keywords.any { s.contains(it) }) {
                    return Command.SetToggle(target, isOn && !isOff)
                }
            }
        }

        // ==== Нотатки ====
        noteRegex.matchEntire(s)?.let { return Command.Note(it.groupValues[2].trim()) }

        // ==== Питання ====
        if (questionStart.matches(s) || questionEnd.matches(s)) {
            return Command.AskQuestion(input.trim())
        }

        // ==== Команди ====
        openApp.matchEntire(s)?.let { return Command.OpenApp(it.groupValues[2].trim()) }
        call.matchEntire(s)?.let { return Command.Call(it.groupValues[2].trim()) }
        search.matchEntire(s)?.let { return Command.WebSearch(it.groupValues[2].trim()) }
        alarm.matchEntire(s)?.let { return Command.SetAlarm(it.groupValues[3].trim(), null) }
        timer.matchEntire(s)?.let { return Command.SetTimer(it.groupValues[3].trim()) }

        return Command.Raw(input)
    }
}
