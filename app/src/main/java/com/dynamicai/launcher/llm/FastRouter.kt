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
        val media = Regex("(наступн|попередн|минул)\\p{L}*\\s+(трек|пісн|композиц)|^\\s*(пауза|далі)\\s*$").containsMatchIn(t)
        return toggle || media
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
