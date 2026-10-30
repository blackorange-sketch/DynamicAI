package com.dynamicai.launcher.macro

import android.content.Context
import com.dynamicai.launcher.model.AppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class FocusMode(
    val id: String,
    val name: String,
    val icon: String,
    val steps: List<String>,
    val hideCategories: Set<String> = emptySet()
)

/** Режими фокусу: одним перемикачем виконують набір налаштувань і ховають відволікаючі додатки (ігри, соцмережі). */
object FocusModes {
    val presets = listOf(
        FocusMode("work", "Робота", "💼",
            listOf("setRingerMode(mode=\"vibrate\")", "setDnd(state=\"on\")"), setOf("games", "social")),
        FocusMode("sleep", "Сон", "🌙",
            listOf("setDnd(state=\"on\")", "setDarkMode(state=\"on\")", "setBrightness(percent=\"10\")", "setNightLight(state=\"on\")"), setOf("games", "social")),
        FocusMode("car", "Авто", "🚗", listOf("carMode()"), setOf("games")),
        FocusMode("free", "Вільний час", "🎮",
            listOf("setDnd(state=\"off\")", "setRingerMode(mode=\"normal\")", "setBrightness(percent=\"60\")"))
    )
    private val OFF_STEPS = listOf("setDnd(state=\"off\")", "setRingerMode(mode=\"normal\")", "setNightLight(state=\"off\")")

    private val _active = MutableStateFlow("")
    val active: StateFlow<String> = _active
    private var loaded = false

    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        _active.value = ctx.applicationContext.getSharedPreferences("focus_modes", Context.MODE_PRIVATE).getString("active", "") ?: ""
    }

    fun find(id: String): FocusMode? = presets.firstOrNull { it.id == id }

    private fun save(ctx: Context, id: String) {
        ctx.applicationContext.getSharedPreferences("focus_modes", Context.MODE_PRIVATE).edit().putString("active", id).apply()
        _active.value = id
    }

    /** Додатки, які приховує активний режим. */
    fun hiddenFor(id: String, apps: List<AppInfo>): Set<String> {
        val m = find(id) ?: return emptySet()
        if (m.hideCategories.isEmpty()) return emptySet()
        return apps.filter { it.category in m.hideCategories }.map { it.packageName }.toSet()
    }

    fun activate(ctx: Context, id: String): String {
        init(ctx)
        val m = find(id) ?: return "Невідомий режим фокусу"
        save(ctx, id)
        MacroLauncher.run(ctx, Macro(id = "focus:" + m.id, name = "Фокус: ${m.name}", icon = m.icon, steps = m.steps, delayMs = 400))
        return "Режим «${m.name}» увімкнено"
    }

    fun deactivate(ctx: Context): String {
        init(ctx)
        save(ctx, "")
        MacroLauncher.run(ctx, Macro(id = "focus:off", name = "Фокус вимкнено", steps = OFF_STEPS, delayMs = 400))
        return "Режим фокусу вимкнено"
    }

    /** Розпізнає назву режиму у фразі («сну», «роботи», «авто», «вільний час»). */
    fun idFromPhrase(t: String): String? = when {
        Regex("сн[уо]|спати|ніч").containsMatchIn(t) -> "sleep"
        Regex("робот").containsMatchIn(t) -> "work"
        Regex("авто|машин").containsMatchIn(t) -> "car"
        Regex("вільн|відпочин|гра").containsMatchIn(t) -> "free"
        else -> null
    }
}
