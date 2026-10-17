package com.dynamicai.launcher.command

class CommandParser {

    private val openApp = Regex("^(відкрий|відкрити|запусти|увімкни|включи|open|launch|start)\\s+(.+)")
    private val call = Regex("^(подзвони|зателефонуй|набери|дзвони|call|dial)\\s+(.+)")
    private val search = Regex("^(пошук|знайди|загугли|search|google)\\s+(.+)")
    private val alarm = Regex("^(будильник|постав будильник|встанови будильник|alarm)\\s+(на\\s+)?(.+)")
    private val timer = Regex("^(таймер|постав таймер|timer)\\s+(на\\s+)?(.+)")
    private val settings = Regex("^(налаштування|настройки|settings)$")
    private val noteRegex = Regex("^(нотатка|запиши|занотуй|note|запам'ятай)\\s+(.+)")
    private val notesOpenRegex = Regex("^(мої нотатки|покажи нотатки|відкрий нотатки|нотатки|notes|open notes|my notes)$", RegexOption.IGNORE_CASE)
    private val reminderRegex1 = Regex("^нагадай\\s+через\\s+(.+?)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val reminderRegex2 = Regex("^нагадай\\s+(.+?)\\s+через\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val pomodoroRegex = Regex("^(помодоро|фокус|focus|pomodoro)(?:\\s+(\\d{1,3}))?$", RegexOption.IGNORE_CASE)
    private val translateScreenRegex = Regex("^(переклади? екран|переклад екрану|translate screen|переведи экран)(?:\\s+(?:на\\s+)?(англ|англійськ\\w*|en|польськ\\w*|pl|pol|укр\\w*|uk))?$", RegexOption.IGNORE_CASE)
    private val translateTextRegex = Regex("^переклади\\s+[\"']?(.+?)[\"']?\\s+на\\s+(англ\\w*|en|польськ\\w*|pl|pol|укр\\w*|uk|нім\\w*|de|рос\\w*|ru)$", RegexOption.IGNORE_CASE)

    private val questionStart = Regex("^(хто|що|де|коли|як|чому|скільки|яка|який|яке|чи |розкажи|поясни|who|what|where|when|how|why)\\b.*")
    private val questionEnd = Regex(".*\\?$")

    private val brightnessUp = Regex(".*(яскравіше|збільш яскравість|підвищ яскравість|brightness up|яскравість вгору).*")
    private val brightnessDown = Regex(".*(темніше|зменш яскравість|знизь яскравість|brightness down|яскравість вниз).*")
    private val brightnessMax = Regex(".*(максимальна яскравість|макс яскравість|brightness max|на повну яскравість).*")
    private val brightnessAutoOn = Regex(".*(увімкни авто ?яскравість|авто ?яскравість ?увімкни|auto ?brightness on).*")
    private val brightnessAutoOff = Regex(".*(вимкни авто ?яскравість|авто ?яскравість ?вимкни|auto ?brightness off).*")

    private val volumeUp = Regex(".*(гучніше|збільш гучність|volume up|громче|додай гучності|гучність вгору).*")
    private val volumeDown = Regex(".*(тихіше|зменш гучність|volume down|тише|прибери гучність|гучність вниз).*")
    private val volumeMax = Regex(".*(максимальна гучність|на всю гучність|max volume|повна гучність).*")
    private val volumeMute = Regex(".*(без звуку|вимкни звук|mute|тишина|заглуши звук).*")

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

    private val smsRegex = Regex("^(напиши|скинь|відправ|sms|sms to)\\s+(.+?)\\s+(.+)$")
    private val emailRegex = Regex("^(email|mail|пошта)\\s+(\\S+@\\S+)")

    private val mediaPlay = Regex("^(грай|увімкни музику|включи музику|play|resume|продовж)")
    private val mediaPause = Regex("^(пауза|зупини музику|pause|stop music)")
    private val mediaNext = Regex("^(наступний|далі|next track|skip)")
    private val mediaPrev = Regex("^(попередній|назад трек|prev|previous)")
    private val volumePercent = Regex("^(гучність|volume)\\s+(\\d+)\\s*%?$")

    private val macroRegex = Regex("^(?:(?:запусти|виконай|виконати|run|start)\\s+)?макрос\\s+(.+)", RegexOption.IGNORE_CASE)

    private val batteryRe = Regex("^(який заряд|батарея|скільки заряду|battery|battery status)$")

    private val calcRe = Regex("^(порахуй|скільки буде|calculate|calc)\\s+(.+)$")

    private val flashlightWords = setOf(
        "ліхтарик", "ліхтарик вкл", "ліхтарик викл",
        "увімкни ліхтарик", "вимкни ліхтарик", "ввімкни ліхтарик",
        "flashlight", "torch"
    )

    private val screenshotWords = setOf(
        "скріншот", "скрін", "зроби скріншот", "знімок екрану",
        "screenshot", "capture screen"
    )

    private val musicInApp = Regex(
        "^(увімкни|включи|грай|відкрий|запусти)\\s+музику\\s+(?:в|у|на)\\s+(.+)$",
        RegexOption.IGNORE_CASE
    )

    private val compoundSplit = Regex(
        "\\s+(?:і|та|потім|then|and|плюс)\\s+|\\s*[;&+]\\s*|\\s*,\\s+",
        RegexOption.IGNORE_CASE
    )

    private val inheritVerbs = listOf(
        "увімкни", "включи", "вимкни", "виключи",
        "відкрий", "відкрити", "запусти",
        "play", "грай", "pause", "пауза",
        "open", "launch"
    )

    fun parse(input: String): Command {
        val s = input.trim().lowercase()
        if (s.isEmpty()) return Command.Unknown

        val parts = s.split(compoundSplit).map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size > 1) {
            val orig = input.trim()
            val direct = parts.map { parseSingle(it, orig) }
            if (direct.none { it is Command.Raw }) {
                return compoundOf(direct)
            }
            val verb = inheritVerbs.firstOrNull { parts[0].startsWith(it) }
            if (verb != null) {
                val inherited = mutableListOf<Command>()
                var allOk = true
                for ((idx, p) in parts.withIndex()) {
                    val c = if (idx == 0) direct[0]
                            else if (direct[idx] is Command.Raw) parseSingle("$verb $p", orig)
                            else direct[idx]
                    if (c is Command.Raw) { allOk = false; break }
                    inherited.add(c)
                }
                if (allOk) return compoundOf(inherited)
            }
        }

        return parseSingle(s, input.trim())
    }

    private fun compoundOf(cmds: List<Command>): Command {
        val flat = cmds.flatMap { flatten(it) }
        return when {
            flat.isEmpty() -> Command.Unknown
            flat.size == 1 -> flat.first()
            else -> Command.Compound(flat)
        }
    }

    private fun flatten(c: Command): List<Command> = when (c) {
        is Command.Compound -> c.steps.flatMap { flatten(it) }
        else -> listOf(c)
    }

    private fun parseSingle(s: String, original: String = s): Command {
        musicInApp.matchEntire(s)?.let { m ->
            val app = m.groupValues[2].trim()
            if (app.isNotBlank()) {
                return Command.Compound(
                    listOf(Command.OpenApp(app), Command.MediaControl("play")),
                    delayMs = 2500L
                )
            }
        }

        if (s in flashlightWords) return Command.ToggleFlashlight
        if (s in screenshotWords) return Command.Screenshot
        if (settings.matches(s)) return Command.OpenSettings(null)

        if (brightnessAutoOn.matches(s)) return Command.SetToggle("auto_brightness", true)
        if (brightnessAutoOff.matches(s)) return Command.SetToggle("auto_brightness", false)
        if (brightnessMax.matches(s)) return Command.SetBrightness(255)
        if (brightnessUp.matches(s)) return Command.SetBrightness(-1)
        if (brightnessDown.matches(s)) return Command.SetBrightness(-2)

        if (volumeMax.matches(s)) return Command.SetVolume(100)
        if (volumeMute.matches(s)) return Command.SetVolume(0)
        if (volumeUp.matches(s)) return Command.SetVolume(1)
        if (volumeDown.matches(s)) return Command.SetVolume(-1)

        val isOn = onWords.any { s.contains(it) }
        val isOff = offWords.any { s.contains(it) }
        if (isOn || isOff) {
            for ((target, keywords) in toggleTargets) {
                if (keywords.any { s.contains(it) }) {
                    return Command.SetToggle(target, isOn && !isOff)
                }
            }
        }

        translateTextRegex.matchEntire(s)?.let {
            val text = it.groupValues[1].trim()
            val toRaw = it.groupValues[2].lowercase()
            val to = when {
                toRaw.startsWith("англ") || toRaw == "en" -> "en"
                toRaw.startsWith("поль") || toRaw == "pl" || toRaw == "pol" -> "pl"
                toRaw.startsWith("укр") || toRaw == "uk" -> "uk"
                toRaw.startsWith("нім") || toRaw == "de" -> "de"
                toRaw.startsWith("рос") || toRaw == "ru" -> "ru"
                else -> "en"
            }
            return Command.TranslateText(text, to)
        }
        translateScreenRegex.matchEntire(s)?.let {
            val langRaw = it.groupValues[2].lowercase()
            val target = when {
                langRaw.startsWith("поль") || langRaw == "pl" || langRaw == "pol" -> "pl"
                langRaw.startsWith("укр") || langRaw == "uk" -> "uk"
                else -> "uk"
            }
            return Command.TranslateScreen(target)
        }
        pomodoroRegex.matchEntire(s)?.let {
            val mins = it.groupValues[2].toIntOrNull() ?: 25
            return Command.Pomodoro(mins.coerceIn(1, 180))
        }
        reminderRegex1.matchEntire(s)?.let {
            return Command.SetReminder(it.groupValues[2].trim(), it.groupValues[1].trim())
        }
        reminderRegex2.matchEntire(s)?.let {
            return Command.SetReminder(it.groupValues[1].trim(), it.groupValues[2].trim())
        }
        notesOpenRegex.matchEntire(s)?.let { return Command.OpenNotes }
        noteRegex.matchEntire(s)?.let { return Command.Note(it.groupValues[2].trim()) }

        if (questionStart.matches(s) || questionEnd.matches(s)) {
            return Command.AskQuestion(original)
        }

        if (mediaPlay.matches(s)) return Command.MediaControl("play")
        if (mediaPause.matches(s)) return Command.MediaControl("pause")
        if (mediaNext.matches(s)) return Command.MediaControl("next")
        if (mediaPrev.matches(s)) return Command.MediaControl("prev")

        volumePercent.matchEntire(s)?.let {
            return Command.SetVolumeExact(it.groupValues[2].toIntOrNull() ?: 50)
        }

        if (batteryRe.matches(s)) return Command.BatteryStatus
        calcRe.matchEntire(s)?.let { return Command.Calculate(it.groupValues[2].trim()) }
        emailRegex.matchEntire(s)?.let { return Command.SendEmail(it.groupValues[2], null) }
        smsRegex.matchEntire(s)?.let {
            return Command.SendSms(it.groupValues[2].trim(), it.groupValues[3].trim())
        }

        macroRegex.matchEntire(s)?.let {
            val n = it.groupValues[1].trim()
            if (n.isNotBlank()) return Command.Macro(n)
        }

        openApp.matchEntire(s)?.let { return Command.OpenApp(it.groupValues[2].trim()) }
        call.matchEntire(s)?.let { return Command.Call(it.groupValues[2].trim()) }
        search.matchEntire(s)?.let { return Command.WebSearch(it.groupValues[2].trim()) }
        alarm.matchEntire(s)?.let { return Command.SetAlarm(it.groupValues[3].trim(), null) }
        timer.matchEntire(s)?.let { return Command.SetTimer(it.groupValues[3].trim()) }

        return Command.Raw(original)
    }
}
