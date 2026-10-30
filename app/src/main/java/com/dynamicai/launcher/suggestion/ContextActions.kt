package com.dynamicai.launcher.suggestion

import android.content.Context
import com.dynamicai.launcher.data.SettingsStorage
import com.dynamicai.launcher.macro.MacroLauncher
import com.dynamicai.launcher.macro.MacroRunner
import com.dynamicai.launcher.macro.MacroStorage
import java.util.Calendar

/** Дія-пропозиція для головного екрана: або інструмент, або макрос, або службова дія («voice»). */
data class SmartAction(
    val key: String,
    val icon: String,
    val label: String,
    val tool: String = "",
    val macroId: String = "",
    val special: String = ""
)

/** Підбирає дії за контекстом: час доби, зарядка, Bluetooth авто, музика, заряд, найчастіші макроси. */
object ContextActions {

    fun greeting(hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): String = when (hour) {
        in 5..10 -> "Доброго ранку"
        in 11..16 -> "Добрий день"
        in 17..21 -> "Добрий вечір"
        else -> "Доброї ночі"
    }

    fun build(ctx: Context): List<SmartAction> {
        val settings = SettingsStorage(ctx).load()
        val check = MacroRunner(ctx, { "" })
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val out = mutableListOf<SmartAction>()
        fun add(a: SmartAction) { if (out.none { it.key == a.key }) out.add(a) }

        val inCar = settings.carBtName.isNotBlank() && check.check("bt_connected")
        val music = check.check("music")
        val lowBattery = check.check("battery<20")
        val charging = check.check("charging")

        if (inCar) {
            add(SmartAction("car", "🚗", "Музика в авто", tool = "carMode()"))
            add(SmartAction("nav", "🧭", "Маршрут", tool = "navigateTo(destination=\"додому\")"))
        }
        if (music) {
            add(SmartAction("next", "⏭", "Наступний", tool = "mediaKey(key=\"next\")"))
            add(SmartAction("pause", "⏸", "Пауза", tool = "mediaKey(key=\"pause\")"))
        }
        if (lowBattery && !charging) {
            add(SmartAction("saver", "🪫", "Економія заряду", tool = "setBatterySaver(state=\"on\")"))
        }
        when (hour) {
            in 5..10 -> {
                add(SmartAction("weather", "🌤", "Погода", tool = "getWeather(city=\"\")"))
                add(SmartAction("news", "📰", "Новини", tool = "getNews()"))
                if (settings.vacuumIp.isNotBlank()) add(SmartAction("vac", "🧹", "Пилосос", tool = "vacuum(action=\"start\")"))
            }
            in 11..16 -> {
                add(SmartAction("weather", "🌤", "Погода", tool = "getWeather(city=\"\")"))
                if (settings.vacuumIp.isNotBlank()) add(SmartAction("vac", "🧹", "Пилосос", tool = "vacuum(action=\"start\")"))
            }
            in 17..21 -> {
                add(SmartAction("dnd", "🌙", "Не турбувати", tool = "setDnd(state=\"on\")"))
            }
            else -> {
                add(SmartAction("dnd", "🌙", "Не турбувати", tool = "setDnd(state=\"on\")"))
                add(SmartAction("dark", "🕶", "Темна тема", tool = "setDarkMode(state=\"on\")"))
                add(SmartAction("alarm", "⏰", "Будильник", tool = "openSettings(page=\"date\")"))
            }
        }

        // Найчастіше запускані макроси
        val usage = MacroLauncher.usage(ctx)
        MacroStorage(ctx).get().filter { it.enabled }
            .sortedByDescending { usage["macro:" + it.id] ?: 0 }
            .take(3)
            .forEach { add(SmartAction("m:" + it.id, it.icon, it.name, macroId = it.id)) }

        add(SmartAction("voice", "🎙", "Асистент", special = "voice"))
        return out.take(8)
    }
}
