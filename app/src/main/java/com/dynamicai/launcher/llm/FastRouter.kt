package com.dynamicai.launcher.llm

import android.util.Log

/**
 * Швидкий роутер: розпізнає прості команди телефону правилами (без LLM) і виконує їх через AgentEngine.
 * Повертає текст відповіді або null, якщо фраза не схожа на просту команду (тоді працює модель).
 * Виконання миттєве: не потрібно ні завантажувати модель, ні чекати токени.
 */
class FastRouter(private val runTool: suspend (String) -> String) {

    private companion object {
        const val TAG = "FastRouter"
        val ON = Regex("""(?<!\p{L})(увімкни|включи|ввімкни|запусти|активуй|вруби)(?!\p{L})""")
        val OFF = Regex("""(?<!\p{L})(вимкни|виключи|вимкнути|погаси|відключи|заглуши)(?!\p{L})""")
    }

    /** Системні перемикачі (потрібен Shizuku): шаблон фрази → інструмент зі станом. */
    private val SYS_TOGGLES: List<Pair<Regex, String>> = listOf(
        Regex("nfc|нфс|енфс|нфц") to "toggleNfc",
        Regex("геолокац|місцезнаходж|gps|джіпіес") to "toggleLocation",
        Regex("темн\\p{L}*\\s+(тем|режим)|dark\\s*mode") to "setDarkMode",
        Regex("економ\\p{L}*\\s+(заряд|батаре|енерг)|режим\\s+економ") to "setBatterySaver",
        Regex("автоповорот|автообертання|автоматичн\\p{L}*\\s+поворот") to "setAutoRotate",
        Regex("автояскравість|автоматичн\\p{L}*\\s+яскравість") to "setAutoBrightness",
        Regex("нічн\\p{L}*\\s+(світло|режим екрана)") to "setNightLight"
    )

    /** Фіксовані фрази без стану. */
    private val SYS_PHRASES: List<Pair<Regex, String>> = listOf(
        Regex("^(зроби\\s+)?(скріншот|знімок екрана|скрін)$") to """screenshot()""",
        Regex("^(вимкни|погаси|заблокуй)\\s+(екран|телефон)$|^заблокуй$") to """screenOff()""",
        Regex("^(увімкни|розбуди|засвіти)\\s+екран$") to """screenOn()""",
        Regex("^(відкрий|покажи|опусти)\\s+шторку(\\s+сповіщень)?$|^покажи сповіщення$") to """expandNotifications()""",
        Regex("^(відкрий|покажи)\\s+швидкі\\s+налаштування$") to """expandQuickSettings()""",
        Regex("^закрий\\s+шторку$") to """collapsePanels()""",
        Regex("^(закрий|вбий)\\s+(всі|усі)\\s+(фонові\\s+)?(додатки|програми)$|^очисти\\s+оперативку$|^очисти\\s+пам.ять$") to """killBackground()""",
        Regex("^очисти\\s+кеш$") to """clearCache()""",
        Regex("^(назад|тапни назад)$") to """pressKey(key="back")""",
        Regex("^(додому|на головний екран|на головну)$") to """pressKey(key="home")""",
        Regex("^(останні додатки|недавні додатки|відкрий недавні)$") to """pressKey(key="recents")"""
    )

    private fun q(s: String) = s.replace("\"", "").trim()

    private val CURRENCIES = listOf(
        "долар" to "USD", "usd" to "USD", "євро" to "EUR", "eur" to "EUR", "злот" to "PLN", "pln" to "PLN",
        "гривн" to "UAH", "грн" to "UAH", "uah" to "UAH", "фунт" to "GBP", "gbp" to "GBP", "франк" to "CHF"
    )

    /** Знаходить у фразі валюти (у порядку появи) і число; одна валюта = у гривні. */
    private fun currencyCall(t: String): String? {
        val found = CURRENCIES.mapNotNull { (key, code) -> t.indexOf(key).takeIf { it >= 0 }?.let { it to code } }
            .sortedBy { it.first }.map { it.second }.distinct()
        if (found.isEmpty()) return null
        val from = found[0]
        val to = if (found.size >= 2) found[1] else if (from == "UAH") "USD" else "UAH"
        if (from == to) return null
        val amount = Regex("(\\d+(?:[.,]\\d+)?)").find(t)?.value ?: "1"
        return """convertCurrency(amount="$amount", from="$from", to="$to")"""
    }

    /** Перемикачі й плеєр — їх краще виконувати тут, бо парсер лише «перемикає» без урахування вкл/викл. */
    fun isToggleOrMedia(raw: String): Boolean {
        val t = raw.lowercase()
        val toggle = Regex("ліхтар|wi-?fi|вай-?фай|bluetooth|блютуз|блютус").containsMatchIn(t) &&
            (ON.containsMatchIn(t) || OFF.containsMatchIn(t))
        val sys = SYS_TOGGLES.any { it.first.containsMatchIn(t) } && (ON.containsMatchIn(t) || OFF.containsMatchIn(t)) ||
            SYS_PHRASES.any { it.first.containsMatchIn(t) }
        val vac = t.contains("пилосос") || Regex("^(почни|запусти)\\s+прибирання$|^прибери(\\s+вдома|\\s+в\\s+квартирі)?$").containsMatchIn(t)
        val media = Regex("(наступн|попередн|минул)\\p{L}*\\s+(трек|пісн|композиц)|^\\s*(пауза|далі)\\s*$").containsMatchIn(t)
        return toggle || media || sys || vac
    }

    suspend fun tryHandle(raw: String): String? {
        val t = raw.lowercase().trim().trimEnd('.', '!', '?', ',', ' ')
        if (t.isBlank()) return null
        val words = t.split(Regex("\\s+"))
        val on = ON.containsMatchIn(t)
        val off = OFF.containsMatchIn(t)

        val call: String? = when {
            // Ліхтарик / Wi-Fi / Bluetooth
            Regex("ліхтар").containsMatchIn(t) && (on || off) ->
                """toggleFlashlight(state="${if (off) "off" else "on"}")"""
            Regex("wi-?fi|вай-?фай").containsMatchIn(t) && (on || off) ->
                """toggleWifi(state="${if (off) "off" else "on"}")"""
            Regex("bluetooth|блютуз|блютус").containsMatchIn(t) && (on || off) ->
                """toggleBluetooth(state="${if (off) "off" else "on"}")"""

            // Власний макрос за назвою: «запусти макрос Доброї ночі»
            Regex("^(запусти|виконай|включи)\\s+макрос\\s+(.+)$").containsMatchIn(t) ->
                Regex("^(запусти|виконай|включи)\\s+макрос\\s+(.+)$").find(t)?.groupValues?.get(2)?.let { """runMacro(name="${q(it)}")""" }

            // Сценарій «музика в авто»
            Regex("(музик|плейліст|ютуб\\s*мюзік|youtube\\s*music).*\\s(в|у)\\s+(машин|авто)|режим\\s+авто|я\\s+(в|у)\\s+(машин|авто)|поїхали").containsMatchIn(t) -> "carMode()"

            // Робот-пилосос
            Regex("пилосос").containsMatchIn(t) -> when {
                Regex("де\\s+|знайди|сигнал|пискни").containsMatchIn(t) -> """vacuum(action="locate")"""
                Regex("додому|базу|базі|зарядк|док").containsMatchIn(t) -> """vacuum(action="home")"""
                Regex("статус|що\\s+робить|заряд|стан").containsMatchIn(t) -> """vacuum(action="status")"""
                Regex("пауз").containsMatchIn(t) -> """vacuum(action="pause")"""
                off || Regex("зупини|стоп|припини").containsMatchIn(t) -> """vacuum(action="stop")"""
                else -> """vacuum(action="start")"""
            }
            Regex("^(почни|запусти)\\s+прибирання$|^прибери(\\s+вдома|\\s+в\\s+квартирі)?$").containsMatchIn(t) -> """vacuum(action="start")"""

            // Системні дії через Shizuku
            SYS_PHRASES.any { it.first.containsMatchIn(t) } -> SYS_PHRASES.first { it.first.containsMatchIn(t) }.second
            (on || off) && SYS_TOGGLES.any { it.first.containsMatchIn(t) } ->
                """${SYS_TOGGLES.first { it.first.containsMatchIn(t) }.second}(state="${if (off) "off" else "on"}")"""
            Regex("(відкрий|покажи)\\s+налаштування\\s+(wi-?fi|вай-?фай|bluetooth|блютуз|екрана|батареї|додатків|nfc|геолокації|звуку|розробника)").containsMatchIn(t) ->
                Regex("налаштування\\s+(\\S+)").find(t)?.groupValues?.get(1)?.let { w ->
                    val page = when {
                        w.contains("wi") || w.contains("вай") -> "wifi"
                        w.contains("blue") || w.contains("блют") -> "bluetooth"
                        w.startsWith("екран") -> "display"
                        w.startsWith("батаре") -> "battery"
                        w.startsWith("додатк") -> "apps"
                        w.startsWith("nfc") -> "nfc"
                        w.startsWith("геоло") -> "location"
                        w.startsWith("звук") -> "sound"
                        else -> "developer"
                    }
                    """openSettings(page="$page")"""
                }

            // Режим польоту, «Не турбувати», мобільні дані, режим дзвінка
            Regex("режим\\s+польоту|авіарежим|літаковий").containsMatchIn(t) && (on || off) ->
                """setAirplane(state="${if (off) "off" else "on"}")"""
            Regex("не\\s+турбувати").containsMatchIn(t) && (on || off) ->
                """setDnd(state="${if (off) "off" else "on"}")"""
            Regex("мобільн\\p{L}*\\s+(інтернет|дані)|передач\\p{L}*\\s+даних").containsMatchIn(t) && (on || off) ->
                """setMobileData(state="${if (off) "off" else "on"}")"""
            Regex("^(тихий режим|беззвучний режим|без звуку|вимкни звук|режим тиші)$").containsMatchIn(t) -> """setRingerMode(mode="silent")"""
            Regex("^(режим вібрації|вібро|вібро режим|тільки вібрація)$").containsMatchIn(t) -> """setRingerMode(mode="vibrate")"""
            Regex("^(звичайний режим|увімкни звук|включи звук|нормальний режим)$").containsMatchIn(t) -> """setRingerMode(mode="normal")"""

            // Керування плеєром
            Regex("^(наступн\\p{L}*|далі|перемкни)\\s*(трек|пісню|композицію)?$|наступн\\p{L}*\\s+(трек|пісн\\p{L}*|композиц\\p{L}*)").containsMatchIn(t) -> """mediaKey(key="next")"""
            Regex("(попередн\\p{L}*|минул\\p{L}*)\\s+(трек|пісн\\p{L}*|композиц\\p{L}*)").containsMatchIn(t) -> """mediaKey(key="prev")"""
            Regex("^(пауза|постав на паузу|зупини музику|зупини відтворення|вимкни музику)$").containsMatchIn(t) -> """mediaKey(key="pause")"""
            Regex("^(продовж\\p{L}*|відтвор\\p{L}*|play|увімкни музику|включи музику|запусти музику)$").containsMatchIn(t) -> """mediaKey(key="play")"""

            // Яскравість / гучність з числом
            Regex("яскравість").containsMatchIn(t) ->
                Regex("(\\d{1,3})").find(t)?.let { """setBrightness(percent="${it.value.toInt().coerceIn(0, 100)}")""" }
            Regex("гучність|звук").containsMatchIn(t) && Regex("\\d").containsMatchIn(t) ->
                Regex("(\\d{1,3})").find(t)?.let { """setVolume(level="${it.value.toInt().coerceIn(0, 100)}")""" }

            // Будильник: «постав будильник на 7:30», «будильник на 7»
            Regex("будильник").containsMatchIn(t) ->
                Regex("(\\d{1,2})\\s*[:.\\s]\\s*(\\d{2})").find(t)?.let {
                    """setAlarm(hour="${it.groupValues[1]}", minute="${it.groupValues[2]}", message="Будильник")"""
                } ?: Regex("на\\s+(\\d{1,2})").find(t)?.let {
                    """setAlarm(hour="${it.groupValues[1]}", minute="0", message="Будильник")"""
                }

            // Час / дата / батарея
            Regex("котра\\s+(зараз\\s+)?година|який\\s+(зараз\\s+)?час|скільки\\s+(зараз\\s+)?часу|яка\\s+(сьогодні\\s+)?дата|яке\\s+(сьогодні\\s+)?число").containsMatchIn(t) ->
                "getTime()"
            Regex("заряд|батаре").containsMatchIn(t) && Regex("скільки|який|рівень|відсот").containsMatchIn(t) ->
                "getBattery()"

            // Погода: «погода», «яка погода у Львові»
            Regex("погода").containsMatchIn(t) && words.size <= 7 -> {
                val city = Regex("(?:в|у|для|по)\\s+([\\p{L}-]+)").find(t)?.groupValues?.get(1) ?: ""
                """getWeather(city="${q(city)}")"""
            }

            // Камера
            Regex("селфі").containsMatchIn(t) -> """openCamera(mode="selfie")"""
            Regex("запиши відео|зніми відео|відео з камери").containsMatchIn(t) -> """openCamera(mode="video")"""
            Regex("зроби фото|сфотографуй|зроби знімок|відкрий камеру|запусти камеру").containsMatchIn(t) -> """openCamera(mode="photo")"""

            // Валюта: «курс долара», «скільки 100 доларів у гривнях», «переведи 50 євро в злоті»
            Regex("курс|переведи|конвертуй|скільки.*(долар|євро|злот|гривн|фунт)").containsMatchIn(t) && currencyCall(t) != null ->
                currencyCall(t)

            // Навігація: «прокладі маршрут до Львова»
            Regex("^(прокладі|побудуй|веди|навігація|маршрут)").containsMatchIn(t) ->
                Regex("\\s(?:до|в|на)\\s+(.+)$").find(t)?.let { """navigateTo(destination="${q(it.groupValues[1])}")""" }

            // YouTube: «знайди на ютубі ...»
            Regex("ютуб|youtube").containsMatchIn(t) && Regex("знайди|включи|запусти|пошукай").containsMatchIn(t) ->
                Regex("(?:знайди|включи|запусти|пошукай)\\s+(.+?)\\s+на\\s+(?:ютуб\\w*|youtube)").find(t)
                    ?.let { """playYouTube(query="${q(it.groupValues[1])}")""" }

            // Нотатка: «запиши ...», «створи нотатку ...»
            Regex("^(запиши|створи нотатку|нотатка|додай нотатку)\\s+").containsMatchIn(t) ->
                """takeNote(text="${q(t.replace(Regex("^(запиши|створи нотатку|нотатка|додай нотатку)\\s+"), ""))}")"""

            // Системні дії та взаємодія з екраном (потрібен сервіс доступності)
            Regex("новини|головні новини|що нового в світі").containsMatchIn(t) && words.size <= 5 -> "getNews()"
            Regex("(відкрий|покажи)\\s+(останні|недавні)\\s+(додатки|програми)|недавні додатки").containsMatchIn(t) ->
                """systemAction(action="recents")"""
            Regex("(відкрий|опусти|покажи)\\s+(сповіщення|шторку)").containsMatchIn(t) -> """systemAction(action="notifications")"""
            Regex("заблокуй\\s+(екран|телефон)").containsMatchIn(t) -> """systemAction(action="lock")"""
            Regex("^(натисни|тапни|клікни)\\s+назад|^назад$|^поверни(сь)?\\s+назад").containsMatchIn(t) -> """systemAction(action="back")"""
            Regex("^прокрут\\p{L}*\\s+(вниз|далі)|^прокрут\\p{L}*$").containsMatchIn(t) -> """scrollScreen(direction="down")"""
            Regex("^прокрут\\p{L}*\\s+(вгору|вверх)").containsMatchIn(t) -> """scrollScreen(direction="up")"""
            Regex("^(натисни|тапни|клікни)\\s+(на\\s+)?(.+)$").containsMatchIn(t) ->
                Regex("^(натисни|тапни|клікни)\\s+(на\\s+)?(.+)$").find(t)?.groupValues?.get(3)
                    ?.trim()?.trim('«', '»')?.takeIf { it.isNotBlank() }?.let { """tapText(text="${q(it)}")""" }
            Regex("^(введи|надрукуй)\\s+(.+)$").containsMatchIn(t) ->
                Regex("^(введи|надрукуй)\\s+(.+)$").find(t)?.groupValues?.get(2)?.let { """typeText(text="${q(it)}")""" }

            // Додаток: «відкрий телеграм» (до 4 слів — довші фрази, імовірно, складніші)
            Regex("^(відкрий|відкрити|запусти|запустити|відкрити додаток)\\s+").containsMatchIn(t) && words.size <= 5 -> {
                val name = t.replace(Regex("^(відкрий|відкрити|запусти|запустити)\\s+(додаток\\s+|програму\\s+)?"), "")
                if (name.isBlank()) null else """openApp(name="${q(name)}")"""
            }
            Regex("^(закрий додаток|на головний екран|додому|згорни)").containsMatchIn(t) -> "closeApp()"
            else -> null
        }

        if (call == null) return null
        Log.e(TAG, "⚡ '$t' → $call")
        return try {
            val result = runTool(call)
            // Додаток/контакт не знайдено — нехай розбирається модель
            if (result.contains("не знайдено", ignoreCase = true)) null else result
        } catch (e: Exception) {
            Log.e(TAG, "route failed: ${e.message}")
            null
        }
    }
}
