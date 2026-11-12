package com.dynamicai.launcher.util

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.json.JSONObject

/** Миттєві відповіді в рядку пошуку: валюти (за кешованим курсом), час у місті, дні до дати. */
object QuickAnswers {

    // ===== Валюти =====
    private const val PREFS = "dynamicai_rates"
    private const val MAX_AGE_MS = 6 * 60 * 60 * 1000L
    @Volatile private var rates: Map<String, Double>? = null
    @Volatile private var loadedAt = 0L
    @Volatile private var refreshing = false

    private val currencyWords = linkedMapOf(
        "USD" to listOf("usd", "$", "долар", "дол", "бакс"),
        "EUR" to listOf("eur", "€", "євро", "евро"),
        "PLN" to listOf("pln", "zł", "zl", "злот"),
        "UAH" to listOf("uah", "₴", "грн", "грив"),
        "GBP" to listOf("gbp", "£", "фунт"),
        "CHF" to listOf("chf", "франк"),
        "CZK" to listOf("czk", "крон")
    )

    private fun currencyOf(word: String): String? {
        val w = word.trim().lowercase()
        if (w.isEmpty()) return null
        return currencyWords.entries.firstOrNull { (_, keys) -> keys.any { w == it || (it.length >= 3 && w.startsWith(it)) } }?.key
    }

    private val CUR = Regex("^(\\d+(?:[.,]\\d+)?)\\s*([^\\d\\s]+)(?:\\s+(?:в|у|до|to|in)\\s+|\\s*(?:=|->)\\s*|\\s+)?([^\\d\\s]+)?$")

    /** «100 usd в pln», «50 євро», «20$» → рядок результату або null. Курс береться з кешу й оновлюється у фоні. */
    fun currency(ctx: Context, q: String): String? {
        val m = CUR.find(q.trim().lowercase()) ?: return null
        val amount = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val from = currencyOf(m.groupValues[2]) ?: return null
        val toRaw = m.groupValues[3]
        val to = if (toRaw.isBlank()) (if (from == "UAH") "PLN" else "UAH") else currencyOf(toRaw) ?: return null
        if (from == to) return null
        val r = loadRates(ctx) ?: return null
        val rf = r[from] ?: return null
        val rt = r[to] ?: return null
        val value = amount / rf * rt
        return "${fmt(amount)} $from = ${fmt(value)} $to"
    }

    private fun fmt(v: Double): String =
        if (v >= 100 || v == Math.floor(v)) String.format(Locale.US, "%,.0f", v).replace(',', ' ')
        else String.format(Locale.US, "%.2f", v)

    private fun loadRates(ctx: Context): Map<String, Double>? {
        val app = ctx.applicationContext
        if (rates == null) {
            try {
                val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val raw = p.getString("rates", null)
                if (raw != null) {
                    val o = JSONObject(raw)
                    rates = o.keys().asSequence().associateWith { o.getDouble(it) }
                    loadedAt = p.getLong("ts", 0L)
                }
            } catch (_: Throwable) {}
        }
        if (System.currentTimeMillis() - loadedAt > MAX_AGE_MS && !refreshing) {
            refreshing = true
            Thread {
                try {
                    val conn = (URL("https://open.er-api.com/v6/latest/USD").openConnection() as HttpURLConnection).apply {
                        connectTimeout = 6000; readTimeout = 6000
                    }
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    val all = JSONObject(body).optJSONObject("rates")
                    if (all != null) {
                        val keep = JSONObject()
                        currencyWords.keys.forEach { c -> all.optDouble(c).takeIf { !it.isNaN() && it > 0 }?.let { keep.put(c, it) } }
                        if (keep.length() > 0) {
                            rates = keep.keys().asSequence().associateWith { keep.getDouble(it) }
                            loadedAt = System.currentTimeMillis()
                            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                                .putString("rates", keep.toString()).putLong("ts", loadedAt).apply()
                        }
                    }
                } catch (_: Throwable) {
                } finally { refreshing = false }
            }.start()
        }
        return rates
    }

    // ===== Час у місті =====
    private val cities = listOf(
        Triple("київ", "Київ", "Europe/Kyiv"), Triple("киев", "Київ", "Europe/Kyiv"), Triple("kyiv", "Київ", "Europe/Kyiv"),
        Triple("львів", "Львів", "Europe/Kyiv"), Triple("варшав", "Варшава", "Europe/Warsaw"), Triple("warsaw", "Варшава", "Europe/Warsaw"),
        Triple("краків", "Краків", "Europe/Warsaw"), Triple("кракові", "Краків", "Europe/Warsaw"),
        Triple("берлін", "Берлін", "Europe/Berlin"), Triple("berlin", "Берлін", "Europe/Berlin"),
        Triple("лондон", "Лондон", "Europe/London"), Triple("london", "Лондон", "Europe/London"),
        Triple("париж", "Париж", "Europe/Paris"), Triple("paris", "Париж", "Europe/Paris"),
        Triple("мадрид", "Мадрид", "Europe/Madrid"), Triple("рим", "Рим", "Europe/Rome"),
        Triple("прага", "Прага", "Europe/Prague"), Triple("празі", "Прага", "Europe/Prague"),
        Triple("відень", "Відень", "Europe/Vienna"), Triple("відні", "Відень", "Europe/Vienna"),
        Triple("стамбул", "Стамбул", "Europe/Istanbul"), Triple("дубай", "Дубай", "Asia/Dubai"), Triple("дубаї", "Дубай", "Asia/Dubai"),
        Triple("нью-йорк", "Нью-Йорк", "America/New_York"), Triple("new york", "Нью-Йорк", "America/New_York"),
        Triple("чикаго", "Чикаго", "America/Chicago"), Triple("лос-анджелес", "Лос-Анджелес", "America/Los_Angeles"),
        Triple("торонто", "Торонто", "America/Toronto"), Triple("токіо", "Токіо", "Asia/Tokyo"), Triple("tokyo", "Токіо", "Asia/Tokyo"),
        Triple("пекін", "Пекін", "Asia/Shanghai"), Triple("шанхай", "Шанхай", "Asia/Shanghai"), Triple("сеул", "Сеул", "Asia/Seoul"),
        Triple("делі", "Делі", "Asia/Kolkata"), Triple("сінгапур", "Сінгапур", "Asia/Singapore"),
        Triple("сідней", "Сідней", "Australia/Sydney"), Triple("сіднеї", "Сідней", "Australia/Sydney")
    )
    private val TIME_ASK = Regex("(?:час|година|годин|time)\\b.*?\\s(?:в|у|in)\\s+(.+)$")

    /** «час у токіо», «котра година в лондоні» → «Токіо: 07:12 (+8 год)». */
    fun worldTime(q: String): String? {
        val place = TIME_ASK.find(q.trim().lowercase())?.groupValues?.get(1)?.trim() ?: return null
        val c = cities.firstOrNull { place.startsWith(it.first) } ?: return null
        return try {
            val there = ZonedDateTime.now(ZoneId.of(c.third))
            val diffMin = (there.offset.totalSeconds - ZonedDateTime.now().offset.totalSeconds) / 60
            val diff = when {
                diffMin == 0 -> "як у вас"
                diffMin % 60 == 0 -> (if (diffMin > 0) "+" else "−") + kotlin.math.abs(diffMin / 60) + " год"
                else -> (if (diffMin > 0) "+" else "−") + String.format(Locale.US, "%.1f", kotlin.math.abs(diffMin) / 60f) + " год"
            }
            "${c.second}: ${there.format(DateTimeFormatter.ofPattern("HH:mm"))} ($diff)"
        } catch (_: Throwable) { null }
    }

    // ===== Дні до дати =====
    private val months = listOf("січ", "лют", "бер", "квіт", "трав", "черв", "лип", "серп", "вер", "жовт", "лист", "груд")
    private val UNTIL = Regex("(?:днів|дні|скільки)\\s.*?до\\s+(.+)$")
    private val D_NUM = Regex("^(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{4}))?$")
    private val D_WORD = Regex("^(\\d{1,2})\\s+([а-яіїє]+)$")

    /** «днів до 25 грудня», «скільки до 01.03», «днів до нового року» → «До 25 грудня — 42 дні». */
    fun daysUntil(q: String): String? {
        val what = UNTIL.find(q.trim().lowercase())?.groupValues?.get(1)?.trim()?.trimEnd('?') ?: return null
        val today = LocalDate.now()
        val target: LocalDate = try {
            when {
                what.startsWith("нового року") -> LocalDate.of(today.year + 1, 1, 1)
                what.startsWith("різдва") -> LocalDate.of(today.year, 12, 25).let { if (it.isBefore(today)) it.plusYears(1) else it }
                D_NUM.matches(what) -> {
                    val g = D_NUM.find(what)!!.groupValues
                    val y = g[3].toIntOrNull()
                    val d = LocalDate.of(y ?: today.year, g[2].toInt(), g[1].toInt())
                    if (y == null && d.isBefore(today)) d.plusYears(1) else d
                }
                D_WORD.matches(what) -> {
                    val g = D_WORD.find(what)!!.groupValues
                    val mi = months.indexOfFirst { g[2].startsWith(it) }
                    if (mi < 0) return null
                    val d = LocalDate.of(today.year, mi + 1, g[1].toInt())
                    if (d.isBefore(today)) d.plusYears(1) else d
                }
                else -> return null
            }
        } catch (_: Throwable) { return null }
        val n = ChronoUnit.DAYS.between(today, target)
        if (n < 0) return null
        val word = when {
            n % 10 == 1L && n % 100 != 11L -> "день"
            n % 10 in 2..4 && n % 100 !in 12..14 -> "дні"
            else -> "днів"
        }
        val label = target.format(DateTimeFormatter.ofPattern("d MMMM", Locale("uk", "UA")))
        return if (n == 0L) "$label — сьогодні" else "До $label — $n $word"
    }

    // ===== Сайт і номер =====
    private val DOMAIN = Regex("^(?:https?://)?[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.[a-z]{2,}(?:/\\S*)?$")
    fun asUrl(q: String): String? {
        val t = q.trim().lowercase()
        if (!DOMAIN.matches(t)) return null
        return if (t.startsWith("http")) t else "https://$t"
    }

    private val PHONE = Regex("^\\+?[\\d\\s()-]{7,}$")
    fun asPhone(q: String): String? {
        val t = q.trim()
        if (!PHONE.matches(t)) return null
        val digits = t.filter { it.isDigit() }
        if (digits.length !in 7..15) return null
        // «12 - 5» чи «(3-1)» — це вираз, а не номер
        if (t.contains(" - ") || t.count { it == '-' } > 4) return null
        return (if (t.startsWith("+")) "+" else "") + digits
    }
}
