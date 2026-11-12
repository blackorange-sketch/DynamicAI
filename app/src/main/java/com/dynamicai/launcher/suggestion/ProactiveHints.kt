package com.dynamicai.launcher.suggestion

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.CalendarContract
import com.dynamicai.launcher.data.WeatherState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Підказка, яку лаунчер сам показує в слушний момент. */
data class Hint(
    val id: String,
    val icon: String,
    val text: String,
    val actionLabel: String,
    val tool: String = "",        // інструмент агента, напр. setDnd(state="on")
    val special: String = "",     // "calendar:<мс початку>" | "storage"
    val snoozeHours: Int = 6
)

/**
 * Проактивні підказки: найближча подія календаря, низький заряд, дощ уранці, пізня година без «Не турбувати»,
 * заповнене сховище. Усе рахується на пристрої, без мережі. Показується щонайбільше одна підказка;
 * закрита або виконана не з'являється знову кілька годин.
 */
object ProactiveHints {
    private const val PREFS = "dynamicai_hints"

    fun snooze(ctx: Context, hint: Hint) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(hint.id, System.currentTimeMillis() + hint.snoozeHours * 3_600_000L).apply()
    }

    private fun snoozed(ctx: Context, id: String): Boolean =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(id, 0L) > System.currentTimeMillis()

    fun current(ctx: Context, weather: WeatherState?): Hint? {
        val candidates = listOf<() -> Hint?>(
            { calendarHint(ctx) },
            { batteryHint(ctx) },
            { weatherHint(weather) },
            { nightHint(ctx) },
            { storageHint() }
        )
        for (c in candidates) {
            val h = try { c() } catch (_: Throwable) { null } ?: continue
            if (!snoozed(ctx, h.id)) return h
        }
        return null
    }

    /** Подія, що почнеться протягом години. */
    private fun calendarHint(ctx: Context): Hint? {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return null
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString()).appendPath((now + 60 * 60_000L).toString()).build()
        ctx.contentResolver.query(
            uri,
            arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
            null, null, CalendarContract.Instances.BEGIN + " ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val begin = c.getLong(2)
                if (c.getInt(3) != 0 || begin <= now) continue
                val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "Подія"
                val mins = ((begin - now) / 60_000L).toInt().coerceAtLeast(1)
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(begin))
                return Hint(
                    id = "cal:" + c.getLong(0) + ":" + begin,
                    icon = "📅",
                    text = "О $time — «${title.take(40)}». Через $mins хв.",
                    actionLabel = "Відкрити",
                    special = "calendar:$begin",
                    snoozeHours = 2
                )
            }
        }
        return null
    }

    private fun batteryHint(ctx: Context): Hint? {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (level !in 1..19 || bm.isCharging) return null
        return Hint("battery", "🪫", "Заряд $level%. Увімкнути економію?", "Увімкнути", tool = "setBatterySaver(state=\"on\")", snoozeHours = 3)
    }

    /** Уранці — чи знадобиться парасолька. */
    private fun weatherHint(weather: WeatherState?): Hint? {
        val w = weather as? WeatherState.Data ?: return null
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.HOUR_OF_DAY) !in 6..10) return null
        val d = w.description.lowercase()
        val advice = when {
            "гроз" in d -> "Очікується гроза — краще взяти парасольку."
            "дощ" in d || "злив" in d || "мряк" in d -> "Буде дощ — візьміть парасольку."
            "сніг" in d -> "Буде сніг — одягайтеся тепліше."
            else -> return null
        }
        val day = cal.get(Calendar.DAY_OF_YEAR)
        return Hint("weather:$day", "🌧", "Сьогодні ${w.tempMin}°…${w.tempMax}°. $advice", "Погода", tool = "getWeather(city=\"\")", snoozeHours = 12)
    }

    /** Пізня година, а «Не турбувати» вимкнено. */
    private fun nightHint(ctx: Context): Hint? {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour !in 23..23 && hour !in 0..4) return null
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return null
        return Hint("night", "🌙", "Уже пізно. Увімкнути «Не турбувати»?", "Увімкнути", tool = "setDnd(state=\"on\")", snoozeHours = 10)
    }

    private fun storageHint(): Hint? {
        val sf = StatFs(Environment.getDataDirectory().path)
        val total = sf.blockCountLong * sf.blockSizeLong
        val free = sf.availableBlocksLong * sf.blockSizeLong
        if (total <= 0 || free.toDouble() / total > 0.05) return null
        val gb = String.format(Locale.US, "%.1f", free / 1e9)
        return Hint("storage", "💾", "Сховище майже заповнене: вільно $gb ГБ.", "Відкрити", special = "storage", snoozeHours = 48)
    }
}
