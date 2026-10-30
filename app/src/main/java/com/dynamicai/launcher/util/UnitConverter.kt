package com.dynamicai.launcher.util

import java.util.Locale

/** Мінімальний конвертер для пошукового рядка: «5 миль в км», «100 f to c», «15% від 2400». Валюти не підтримуються (потрібна мережа). */
object UnitConverter {
    private class U(val names: List<String>, val factor: Double)  // factor = у базових одиницях групи
    private val groups: List<List<U>> = listOf(
        // довжина (м)
        listOf(U(listOf("мм", "mm"), 0.001), U(listOf("см", "cm"), 0.01), U(listOf("м", "m", "метр", "метрів"), 1.0),
            U(listOf("км", "km"), 1000.0), U(listOf("миля", "миль", "милі", "mi", "mile", "miles"), 1609.344),
            U(listOf("фут", "футів", "ft", "feet"), 0.3048), U(listOf("дюйм", "дюймів", "in", "inch"), 0.0254),
            U(listOf("ярд", "ярдів", "yd"), 0.9144)),
        // маса (кг)
        listOf(U(listOf("г", "g", "грам"), 0.001), U(listOf("кг", "kg"), 1.0), U(listOf("т", "тонна", "тонн"), 1000.0),
            U(listOf("фунт", "фунтів", "lb", "lbs"), 0.45359237), U(listOf("унція", "унцій", "oz"), 0.0283495)),
        // об'єм (л)
        listOf(U(listOf("мл", "ml"), 0.001), U(listOf("л", "l", "літр", "літрів"), 1.0), U(listOf("галон", "галонів", "gal"), 3.78541)),
        // дані (байти)
        listOf(U(listOf("б", "b", "byte"), 1.0), U(listOf("кб", "kb"), 1024.0), U(listOf("мб", "mb"), 1048576.0),
            U(listOf("гб", "gb"), 1073741824.0), U(listOf("тб", "tb"), 1099511627776.0)),
        // швидкість (м/с)
        listOf(U(listOf("м/с", "m/s"), 1.0), U(listOf("км/г", "kmh", "km/h", "кмг"), 0.277778), U(listOf("mph", "миль/г"), 0.44704))
    )
    private val temps = mapOf("c" to "c", "°c" to "c", "цельсій" to "c", "f" to "f", "°f" to "f", "фаренгейт" to "f", "k" to "k", "кельвін" to "k")

    private val rx = Regex("""^\s*(-?\d+(?:[.,]\d+)?)\s*([^\d\s]+(?:/[^\d\s]+)?)\s+(?:в|у|to|in|->|=|на)\s+([^\d\s]+(?:/[^\d\s]+)?)\s*$""", RegexOption.IGNORE_CASE)
    private val pct = Regex("""^\s*(-?\d+(?:[.,]\d+)?)\s*%\s*(?:від|of|з)\s*(-?\d+(?:[.,]\d+)?)\s*$""", RegexOption.IGNORE_CASE)

    private fun fmt(v: Double): String =
        if (kotlin.math.abs(v) >= 1e9 || (v != 0.0 && kotlin.math.abs(v) < 1e-4)) String.format(Locale.US, "%.4g", v)
        else String.format(Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')

    /** Повертає «результат» або null, якщо запит не схожий на конвертацію. */
    fun convert(input: String): String? {
        pct.matchEntire(input)?.let { m ->
            val p = m.groupValues[1].replace(',', '.').toDouble()
            val x = m.groupValues[2].replace(',', '.').toDouble()
            return fmt(p / 100.0 * x)
        }
        val m = rx.matchEntire(input) ?: return null
        val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val a = m.groupValues[2].lowercase(); val b = m.groupValues[3].lowercase()
        val ta = temps[a]; val tb = temps[b]
        if (ta != null && tb != null) {
            val c = when (ta) { "c" -> v; "f" -> (v - 32) * 5 / 9; else -> v - 273.15 }
            val r = when (tb) { "c" -> c; "f" -> c * 9 / 5 + 32; else -> c + 273.15 }
            return fmt(r) + " " + (if (tb == "k") "K" else "°" + tb.uppercase())
        }
        for (g in groups) {
            val ua = g.firstOrNull { a in it.names } ?: continue
            val ub = g.firstOrNull { b in it.names } ?: continue
            return fmt(v * ua.factor / ub.factor) + " " + ub.names.first()
        }
        return null
    }
}
