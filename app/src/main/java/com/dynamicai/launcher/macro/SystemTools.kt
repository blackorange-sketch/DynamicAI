package com.dynamicai.launcher.macro

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.dynamicai.launcher.system.ShizukuManager

/**
 * Системні дії через Shizuku (shell-рівень): екран, клавіші, панелі, теми, економія, анімації, керування додатками.
 * Усі аргументи валідуються, у shell потрапляють лише числа, пакети та значення зі списків.
 */
class SystemTools(private val context: Context) {

    companion object {
        val NAMES = setOf(
            "screenOn", "screenOff", "screenshot", "pressKey", "expandNotifications", "expandQuickSettings", "collapsePanels",
            "tap", "swipe", "setDarkMode", "setBatterySaver", "setAutoBrightness", "setScreenTimeout", "setStayAwake",
            "setNightLight", "setAnimations", "setFontScale", "showTouches", "killBackground", "clearCache", "clearData",
            "disableApp", "enableApp", "setRotation", "openSettings"
        )
        private val KEYS = mapOf(
            "home" to 3, "back" to 4, "recents" to 187, "power" to 26, "volup" to 24, "voldown" to 25, "mute" to 164,
            "menu" to 82, "enter" to 66, "camera" to 27, "play" to 126, "pause" to 127, "next" to 87, "prev" to 88
        )
        private val PAGES = mapOf(
            "wifi" to Settings.ACTION_WIFI_SETTINGS, "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
            "display" to Settings.ACTION_DISPLAY_SETTINGS, "battery" to Intent.ACTION_POWER_USAGE_SUMMARY,
            "apps" to Settings.ACTION_APPLICATION_SETTINGS, "nfc" to Settings.ACTION_NFC_SETTINGS,
            "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS, "sound" to Settings.ACTION_SOUND_SETTINGS,
            "developer" to Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, "storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
            "date" to Settings.ACTION_DATE_SETTINGS, "language" to Settings.ACTION_LOCALE_SETTINGS,
            "accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS, "network" to Settings.ACTION_WIRELESS_SETTINGS,
            "privacy" to Settings.ACTION_PRIVACY_SETTINGS, "notifications" to Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
        )
        private val PKG = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
        private val NUM = Regex("^\\d{1,6}$")
        private val DEC = Regex("^\\d{1,2}(\\.\\d{1,2})?$")
    }

    private val shizuku by lazy { ShizukuManager(context) }
    private val ok: Boolean get() = shizuku.isShizukuAvailable() && shizuku.hasPermission()
    private fun bool(v: String) = v.equals("on", true) || v == "1" || v.equals("true", true)

    private suspend fun sh(vararg cmds: String) { cmds.forEach { shizuku.execShell(it) } }

    suspend fun run(name: String, arg: (String) -> String): String {
        if (name == "openSettings") {
            val page = arg("page").lowercase().trim()
            val action = PAGES[page] ?: Settings.ACTION_SETTINGS
            return try {
                context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Відкрив налаштування: ${if (PAGES.containsKey(page)) page else "головні"}"
            } catch (t: Throwable) { "Не вдалося відкрити налаштування: ${t.message}" }
        }
        if (!ok) return "Потрібен Shizuku (запустіть його і дайте дозвіл)"
        val state = arg("state")
        return try {
            when (name) {
                "screenOn" -> { sh("input keyevent 224"); "Екран увімкнено" }
                "screenOff" -> { sh("input keyevent 223"); "Екран вимкнено" }
                "screenshot" -> { sh("input keyevent 120"); "Знімок екрана зроблено" }
                "pressKey" -> {
                    val k = arg("key").lowercase().trim()
                    val code = KEYS[k] ?: k.takeIf { NUM.matches(it) }?.toInt() ?: return "Невідома клавіша: $k"
                    sh("input keyevent $code"); "Натиснуто: $k"
                }
                "expandNotifications" -> { sh("cmd statusbar expand-notifications"); "Шторку сповіщень відкрито" }
                "expandQuickSettings" -> { sh("cmd statusbar expand-settings"); "Швидкі налаштування відкрито" }
                "collapsePanels" -> { sh("cmd statusbar collapse"); "Панелі закрито" }
                "tap" -> {
                    val x = arg("x"); val y = arg("y")
                    if (!NUM.matches(x) || !NUM.matches(y)) return "Потрібні координати x і y"
                    sh("input tap $x $y"); "Тап ($x, $y)"
                }
                "swipe" -> {
                    val v = listOf("x", "y", "x2", "y2").map { arg(it) }
                    if (v.any { !NUM.matches(it) }) return "Потрібні координати x, y, x2, y2"
                    val ms = arg("ms").takeIf { NUM.matches(it) } ?: "300"
                    sh("input swipe ${v[0]} ${v[1]} ${v[2]} ${v[3]} $ms"); "Свайп"
                }
                "setDarkMode" -> {
                    val m = when (state.lowercase()) { "auto" -> "auto"; "off" -> "no"; else -> "yes" }
                    sh("cmd uimode night $m"); "Темна тема: ${state.ifBlank { "on" }}"
                }
                "setBatterySaver" -> { sh("settings put global low_power ${if (bool(state)) 1 else 0}"); "Економія заряду: ${if (bool(state)) "увімкнено" else "вимкнено"}" }
                "setAutoBrightness" -> { sh("settings put system screen_brightness_mode ${if (bool(state)) 1 else 0}"); "Автояскравість: ${if (bool(state)) "увімкнено" else "вимкнено"}" }
                "setScreenTimeout" -> {
                    val s = arg("seconds")
                    if (!NUM.matches(s)) return "Вкажіть секунди"
                    sh("settings put system screen_off_timeout ${s.toLong() * 1000}"); "Вимкнення екрана через $s с"
                }
                "setStayAwake" -> { sh("settings put global stay_on_while_plugged_in ${if (bool(state)) 7 else 0}"); "Не гасити екран на зарядці: ${if (bool(state)) "так" else "ні"}" }
                "setNightLight" -> { sh("settings put secure night_display_activated ${if (bool(state)) 1 else 0}"); "Нічний режим екрана: ${if (bool(state)) "увімкнено" else "вимкнено"}" }
                "setAnimations" -> {
                    val sc = arg("scale")
                    if (!DEC.matches(sc)) return "Вкажіть масштаб, наприклад 0.5 або 0 (вимкнути)"
                    sh("settings put global window_animation_scale $sc", "settings put global transition_animation_scale $sc", "settings put global animator_duration_scale $sc")
                    "Анімації: ×$sc"
                }
                "setFontScale" -> {
                    val sc = arg("scale")
                    if (!DEC.matches(sc)) return "Вкажіть масштаб, наприклад 1.1"
                    sh("settings put system font_scale $sc"); "Розмір шрифту: ×$sc"
                }
                "showTouches" -> { sh("settings put system show_touches ${if (bool(state)) 1 else 0}"); "Показ дотиків: ${if (bool(state)) "увімкнено" else "вимкнено"}" }
                "killBackground" -> { sh("am kill-all"); "Фонові додатки закрито" }
                "clearCache" -> { sh("pm trim-caches 999999999999"); "Кеш додатків очищено" }
                "clearData", "disableApp", "enableApp" -> {
                    val p = arg("package")
                    if (!PKG.matches(p)) return "Не вказано додаток"
                    if (p == context.packageName) return "Цей додаток не можна змінювати з макросу"
                    val label = AppUtils.labelFor(context, p)
                    when (name) {
                        "clearData" -> { sh("pm clear $p"); "Дані додатка «$label» очищено" }
                        "disableApp" -> { sh("pm disable-user --user 0 $p"); "Додаток «$label» вимкнено" }
                        else -> { sh("pm enable $p"); "Додаток «$label» увімкнено" }
                    }
                }
                "setRotation" -> {
                    val r = arg("value")
                    if (r !in setOf("0", "1", "2", "3")) return "Поворот: 0, 1, 2 або 3"
                    sh("settings put system accelerometer_rotation 0", "settings put system user_rotation $r"); "Орієнтація екрана: $r"
                }
                else -> "Невідома дія: $name"
            }
        } catch (t: Throwable) { "Помилка: ${t.message}" }
    }
}
