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
        val ON = Regex("""\b(увімкни|включи|ввімкни|запусти|активуй|вруби)\b""")
        val OFF = Regex("""\b(вимкни|виключи|вимкнути|погаси|відключи|заглуши)\b""")
    }

    private fun q(s: String) = s.replace("\"", "").trim()

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

            // Погода: «погода», «погода у Львові»
            Regex("^(яка\\s+)?погода").containsMatchIn(t) && words.size <= 5 -> {
                val city = Regex("(?:в|у|для)\\s+([\\p{L}-]+)").find(t)?.groupValues?.get(1)
                // Місцевий відмінок («Львові») → звертаємось до погодного сервісу як є; невідоме місто лишаємо моделі
                if (city == null) """getWeather(city="")""" else null
            }

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
