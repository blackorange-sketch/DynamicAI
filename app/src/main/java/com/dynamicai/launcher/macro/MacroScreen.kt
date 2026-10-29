package com.dynamicai.launcher.macro

import com.dynamicai.launcher.ui.theme.AppTheme
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Шаблон кроку: {0}, {1} у template замінюються значеннями полів. */
private data class StepSpec(val label: String, val template: String, val params: List<Pair<String, String>> = emptyList(), val desc: String = "", val pick: String = "")

private val STEP_CATALOG: List<Pair<String, List<StepSpec>>> = listOf(
    "Мережа" to listOf(
        StepSpec("Wi-Fi увімкнути", "toggleWifi(state=\"on\")"),
        StepSpec("Wi-Fi вимкнути", "toggleWifi(state=\"off\")"),
        StepSpec("Bluetooth увімкнути", "toggleBluetooth(state=\"on\")"),
        StepSpec("Bluetooth вимкнути", "toggleBluetooth(state=\"off\")"),
        StepSpec("Підключити Bluetooth-пристрій", "connectBluetooth(name=\"{0}\")", desc = "Обрати зі спарених або «Моє авто»", pick = "btcar"),
        StepSpec("NFC увімкнути", "toggleNfc(state=\"on\")", desc = "Автоматично — з Shizuku, інакше відкриє налаштування"),
        StepSpec("NFC вимкнути", "toggleNfc(state=\"off\")", desc = "Автоматично — з Shizuku, інакше відкриє налаштування"),
        StepSpec("Геолокація увімкнути", "toggleLocation(state=\"on\")", desc = "Автоматично — з Shizuku"),
        StepSpec("Геолокація вимкнути", "toggleLocation(state=\"off\")", desc = "Автоматично — з Shizuku"),
        StepSpec("Є інтернет (вимкнути політ, увімкнути дані)", "ensureInternet()")
    ),
    "Додатки" to listOf(
        StepSpec("Відкрити додаток", "openApp(package=\"{0}\")", desc = "Оберіть додаток зі списку", pick = "app"),
        StepSpec("Примусово закрити додаток", "forceStop(package=\"{0}\")", desc = "Потрібен Shizuku", pick = "app"),
        StepSpec("Закрити / на головний", "closeApp()"),
        StepSpec("YouTube Music: грати", "playYouTubeMusic()"),
        StepSpec("Пошук на YouTube", "playYouTube(query=\"{0}\")", listOf("Запит" to "")),
        StepSpec("Відкрити посилання", "openUrl(url=\"{0}\")", listOf("URL" to "https://")),
        StepSpec("Маршрут", "navigateTo(destination=\"{0}\")", listOf("Куди" to "")),
        StepSpec("Камера", "openCamera(mode=\"photo\")"),
        StepSpec("HTTP-запит (GET)", "httpGet(url=\"{0}\")", listOf("URL" to "https://"))
    ),
    "Медіа" to listOf(
        StepSpec("Плеєр: Play", "mediaKey(key=\"play\")"),
        StepSpec("Плеєр: пауза", "mediaKey(key=\"pause\")"),
        StepSpec("Плеєр: наступний", "mediaKey(key=\"next\")"),
        StepSpec("Плеєр: попередній", "mediaKey(key=\"prev\")"),
        StepSpec("Гучність, %", "setVolume(level=\"{0}\")", listOf("Рівень 0–100" to "60"))
    ),
    "Пристрій" to listOf(
        StepSpec("Ліхтарик увімкнути", "toggleFlashlight(state=\"on\")"),
        StepSpec("Ліхтарик вимкнути", "toggleFlashlight(state=\"off\")"),
        StepSpec("Яскравість, %", "setBrightness(percent=\"{0}\")", listOf("Рівень 0–100" to "50")),
        StepSpec("Не турбувати: увімкнути", "setDnd(state=\"on\")"),
        StepSpec("Не турбувати: вимкнути", "setDnd(state=\"off\")"),
        StepSpec("Автоповорот увімкнути", "setAutoRotate(state=\"on\")"),
        StepSpec("Автоповорот вимкнути", "setAutoRotate(state=\"off\")"),
        StepSpec("Надіслати SMS", "sendSms(number=\"{0}\", text=\"{1}\")", listOf("Номер" to "+380", "Текст ({time}, {battery}…)" to "")),
        StepSpec("Копіювати текст", "copyText(text=\"{0}\")", listOf("Текст ({body}, {title}, {last}…)" to "")),
        StepSpec("Режим дзвінка", "setRingerMode(mode=\"{0}\")", listOf("normal, vibrate або silent" to "vibrate")),
        StepSpec("Мобільні дані: увімкнути", "setMobileData(state=\"on\")"),
        StepSpec("Мобільні дані: вимкнути", "setMobileData(state=\"off\")"),
        StepSpec("Режим польоту: увімкнути", "setAirplane(state=\"on\")"),
        StepSpec("Режим польоту: вимкнути", "setAirplane(state=\"off\")"),
        StepSpec("Вібрація", "vibrate(ms=\"{0}\")", listOf("Мілісекунд" to "400")),
        StepSpec("Сповіщення", "notify(title=\"{0}\", text=\"{1}\")", listOf("Заголовок" to "DynamicAI", "Текст" to "")),
        StepSpec("Озвучити текст", "speak(text=\"{0}\")", listOf("Текст (можна {time}, {battery}, {last})" to "")),
        StepSpec("Shell-команда (Shizuku)", "shell(cmd=\"{0}\")", listOf("Команда" to "")),
        StepSpec("Будильник", "setAlarm(hour=\"{0}\", minute=\"{1}\", message=\"{2}\")", listOf("Година" to "7", "Хвилини" to "0", "Підпис" to "Будильник")),
        StepSpec("Нотатка", "takeNote(text=\"{0}\")", listOf("Текст" to "")),
        StepSpec("Системна дія", "systemAction(action=\"{0}\")", listOf("home, back, recents, notifications, lock, screenshot" to "home")),
        StepSpec("Натиснути текст на екрані", "tapText(text=\"{0}\")", listOf("Текст кнопки" to "")),
        StepSpec("Ввести текст", "typeText(text=\"{0}\")", listOf("Текст" to ""))
    ),
    "⚙ Система (Shizuku)" to listOf(
        StepSpec("Екран увімкнути", "screenOn()", desc = "Розбудити екран"),
        StepSpec("Екран вимкнути / заблокувати", "screenOff()", desc = "Заблокувати телефон"),
        StepSpec("Знімок екрана", "screenshot()"),
        StepSpec("Натиснути клавішу", "pressKey(key=\"{0}\")", listOf("home, back, recents, power, volup, voldown, mute, menu, enter, camera, play, pause, next, prev" to "home")),
        StepSpec("Відкрити шторку сповіщень", "expandNotifications()"),
        StepSpec("Відкрити швидкі налаштування", "expandQuickSettings()"),
        StepSpec("Закрити шторку", "collapsePanels()"),
        StepSpec("Тап по координатах", "tap(x=\"{0}\", y=\"{1}\")", listOf("x, px" to "540", "y, px" to "1200")),
        StepSpec("Свайп", "swipe(x=\"{0}\", y=\"{1}\", x2=\"{2}\", y2=\"{3}\", ms=\"{4}\")", listOf("x початок" to "540", "y початок" to "1600", "x кінець" to "540", "y кінець" to "600", "Тривалість, мс" to "300")),
        StepSpec("Темна тема", "setDarkMode(state=\"{0}\")", listOf("on, off або auto" to "on")),
        StepSpec("Економія заряду", "setBatterySaver(state=\"{0}\")", listOf("on або off" to "on")),
        StepSpec("Автояскравість", "setAutoBrightness(state=\"{0}\")", listOf("on або off" to "on")),
        StepSpec("Нічний режим екрана", "setNightLight(state=\"{0}\")", listOf("on або off" to "on")),
        StepSpec("Час до вимкнення екрана", "setScreenTimeout(seconds=\"{0}\")", listOf("Секунд" to "60")),
        StepSpec("Не гасити екран на зарядці", "setStayAwake(state=\"{0}\")", listOf("on або off" to "on")),
        StepSpec("Анімації (масштаб)", "setAnimations(scale=\"{0}\")", listOf("0 — вимкнути, 0.5, 1" to "0.5")),
        StepSpec("Розмір шрифту", "setFontScale(scale=\"{0}\")", listOf("Масштаб, напр. 1.0, 1.15" to "1.0")),
        StepSpec("Показувати дотики", "showTouches(state=\"{0}\")", listOf("on або off" to "on")),
        StepSpec("Орієнтація екрана", "setRotation(value=\"{0}\")", listOf("0 — звичайна, 1 — вліво, 2 — догори дриґом, 3 — вправо" to "0")),
        StepSpec("Відкрити налаштування", "openSettings(page=\"{0}\")", listOf("wifi, bluetooth, display, battery, apps, nfc, location, sound, developer, storage, accessibility" to "wifi")),
        StepSpec("Закрити всі фонові додатки", "killBackground()"),
        StepSpec("Очистити кеш усіх додатків", "clearCache()"),
        StepSpec("Очистити дані додатка", "clearData(package=\"{0}\")", desc = "Скидає додаток до чистого стану!", pick = "app"),
        StepSpec("Вимкнути додаток", "disableApp(package=\"{0}\")", desc = "Ховає додаток із системи", pick = "app"),
        StepSpec("Увімкнути додаток", "enableApp(package=\"{0}\")", desc = "Повертає вимкнений додаток", pick = "app")
    ),
    "Інфо" to listOf(
        StepSpec("Погода", "getWeather(city=\"{0}\")", listOf("Місто (порожньо — авто)" to "")),
        StepSpec("Новини", "getNews()"),
        StepSpec("Час і дата", "getTime()"),
        StepSpec("Заряд батареї", "getBattery()"),
        StepSpec("Пошук в інтернеті", "searchWeb(query=\"{0}\")", listOf("Запит" to ""))
    ),
    "Логіка" to listOf(
        StepSpec("⏱ Пауза (секунд)", "WAIT: {0}", listOf("Секунд" to "2")),
        StepSpec("⌛ Чекати умову", "WAIT_UNTIL: {0} {1}", listOf("Умова: ${MacroRunner.CONDITIONS}" to "online", "Макс. секунд" to "15")),
        StepSpec("❓ Якщо умова — виконати крок", "IF: {0} ? {1}", listOf("Умова: ${MacroRunner.CONDITIONS}" to "online", "Крок" to "playYouTubeMusic()")),
        StepSpec("❓ Якщо НЕ умова — виконати крок", "IF_NOT: {0} ? {1}", listOf("Умова: ${MacroRunner.CONDITIONS}" to "online", "Крок" to "ensureInternet()")),
        StepSpec("↔ Якщо умова — A, інакше — B", "IF: {0} ? {1} || {2}", listOf("Умова: ${MacroRunner.CONDITIONS}" to "charging", "Крок A" to "SAY: Заряджається", "Крок B" to "SAY: Не заряджається")),
        StepSpec("🔁 Повторити N разів", "REPEAT: {0} ? {1}", listOf("Разів" to "3", "Крок" to "vibrate(ms=\"300\")")),
        StepSpec("📦 Змінна (SET)", "SET: {0}={1}", listOf("Ім'я" to "city", "Значення" to "Київ")),
        StepSpec("💬 Повідомлення", "SAY: {0}", listOf("Текст (можна {time}, {battery}, {last}, {device})" to "")),
        StepSpec("Запустити інший макрос", "runMacro(name=\"{0}\")", listOf("Назва макросу" to ""))
    ),
    "Сценарії" to listOf(
        StepSpec("🚗 Музика в авто (усе разом)", "carMode()")
    )
)

private val TRIGGER_GROUPS: List<Pair<String, List<StepSpec>>> = listOf(
    "🔋 Живлення" to listOf(
        StepSpec("🔌 Зарядку підключено", "charging_start", desc = "Коли ви вставили зарядний кабель або поставили на бездротову зарядку"),
        StepSpec("🔌 Зарядку відключено", "charging_stop", desc = "Коли зарядку відключили"),
        StepSpec("🪫 Батарея нижче N %", "battery_low:{0}", listOf("Відсоток" to "20"), "Спрацює один раз, коли заряд опуститься нижче вказаного рівня")
    ),
    "📡 Зв'язок" to listOf(
        StepSpec("🔵 Bluetooth підключився", "bt_connected:{0}", desc = "Коли телефон з'єднався з Bluetooth-пристроєм: авто, колонка, навушники", pick = "bt"),
        StepSpec("🔵 Bluetooth відключився", "bt_disconnected:{0}", desc = "Коли з'єднання з пристроєм обірвалось", pick = "bt"),
        StepSpec("🔵 Bluetooth увімкнено", "bt_on", desc = "Коли ввімкнули Bluetooth"),
        StepSpec("🔵 Bluetooth вимкнено", "bt_off", desc = "Коли вимкнули Bluetooth"),
        StepSpec("🏷 NFC-мітка", "nfc:{0}", desc = "Піднесіть мітку або картку — ми запам'ятаємо її ідентифікатор. Можна й «будь-яка мітка»", pick = "nfc"),
        StepSpec("📶 Wi-Fi підключено (будь-яка)", "wifi_connected", desc = "Коли телефон підключився до Wi-Fi"),
        StepSpec("📶 Конкретна Wi-Fi мережа", "wifi_connected:{0}", listOf("Назва мережі (SSID)" to ""), "Коли підключились до мережі з цією назвою (вдома, на роботі). Потрібна геолокація"),
        StepSpec("📶 Вийшли з конкретної Wi-Fi", "wifi_disconnected:{0}", listOf("Назва мережі (SSID)" to ""), "Коли відключились від мережі з цією назвою"),
        StepSpec("📩 Вхідне SMS", "sms_received:{0}", listOf("Відправник містить (порожньо — будь-який)" to ""), "У змінних буде {sender} і {body}"),
        StepSpec("📞 Вхідний дзвінок", "call_incoming", desc = "Коли телефон дзвонить"),
        StepSpec("📞 Дзвінок завершено", "call_ended", desc = "Коли розмова або виклик закінчились"),
        StepSpec("📶 Wi-Fi відключено", "wifi_disconnected", desc = "Коли Wi-Fi зник або вимкнувся"),
        StepSpec("✈ Режим польоту увімкнено", "airplane_on", desc = "Коли ввімкнули режим польоту"),
        StepSpec("✈ Режим польоту вимкнено", "airplane_off", desc = "Коли вимкнули режим польоту")
    ),
    "📱 Пристрій" to listOf(
        StepSpec("🎧 Навушники підключено", "headset_plugged", desc = "Дротові або USB-навушники вставлено"),
        StepSpec("🎧 Навушники відключено", "headset_unplugged", desc = "Дротові або USB-навушники вийнято"),
        StepSpec("📱 Екран увімкнено", "screen_on", desc = "Коли засвітився екран"),
        StepSpec("📱 Екран вимкнено", "screen_off", desc = "Коли екран погас"),
        StepSpec("🔓 Телефон розблоковано", "unlock", desc = "Після розблокування екрана"),
        StepSpec("📳 Струс телефону", "shake", desc = "Різко струсніть телефон тричі"),
        StepSpec("🔋 Батарею заряджено на 100%", "battery_full", desc = "Коли заряд досяг 100%")
    ),
    "📲 Додатки" to listOf(
        StepSpec("📲 Додаток відкрито", "app_opened:{0}", desc = "Коли обраний додаток вийшов на екран (потрібен доступ до статистики використання)", pick = "app"),
        StepSpec("📲 Додаток закрито", "app_closed:{0}", desc = "Коли ви залишили обраний додаток (потрібен доступ до статистики використання)", pick = "app"),
        StepSpec("🔔 Сповіщення від додатка", "notification:{0}:{1}", listOf("Текст містить (порожньо — будь-яке)" to ""), "Коли додаток показав сповіщення; змінні {app} {title} {text}. Потрібен доступ до сповіщень", "app")
    ),
    "⏰ Час і система" to listOf(
        StepSpec("⏰ За часом", "time:{0}:{1}:{2}", listOf("Година" to "7", "Хвилини" to "30", "Дні 1=Пн…7=Нд (порожньо — щодня)" to "12345"), "Щодня або в обрані дні тижня в заданий час"),
        StepSpec("🔁 Кожні N хвилин", "every:{0}", listOf("Хвилин" to "30"), "Повторюється, поки телефон увімкнений"),
        StepSpec("🚀 Запуск телефону", "boot", desc = "Після перезавантаження телефону")
    )
)

private fun describeTrigger(t: String, ctx: android.content.Context? = null): String {
    fun app(p: String) = if (p.isBlank()) "будь-який додаток" else if (ctx != null) AppUtils.labelFor(ctx, p) else p
    val kind = t.substringBefore(':')
    val arg = t.substringAfter(':', "")
    return when (kind) {
        "charging_start" -> "🔌 Зарядку підключено"
        "charging_stop" -> "🔌 Зарядку відключено"
        "battery_low" -> "🪫 Батарея нижче $arg%"
        "bt_connected" -> "🔵 Bluetooth підключився" + (if (arg.isNotBlank()) ": $arg" else "")
        "bt_disconnected" -> "🔵 Bluetooth відключився" + (if (arg.isNotBlank()) ": $arg" else "")
        "wifi_connected" -> "📶 Wi-Fi підключено" + (if (arg.isNotBlank()) ": $arg" else "")
        "wifi_disconnected" -> "📶 Wi-Fi відключено" + (if (arg.isNotBlank()) ": $arg" else "")
        "bt_on" -> "🔵 Bluetooth увімкнено"
        "bt_off" -> "🔵 Bluetooth вимкнено"
        "nfc" -> "🏷 NFC-мітка" + (if (arg.isNotBlank()) " $arg" else " (будь-яка)")
        "sms_received" -> "📩 Вхідне SMS" + (if (arg.isNotBlank()) " від $arg" else "")
        "call_incoming" -> "📞 Вхідний дзвінок"
        "call_ended" -> "📞 Дзвінок завершено"
        "shake" -> "📳 Струс телефону"
        "battery_full" -> "🔋 Заряд 100%"
        "every" -> "🔁 Кожні $arg хв"
        "app_opened" -> "📲 Відкрито: ${app(arg)}"
        "app_closed" -> "📲 Закрито: ${app(arg)}"
        "notification" -> "🔔 Сповіщення: ${app(arg.substringBefore(':'))}" + (arg.substringAfter(':', "").takeIf { it.isNotBlank() }?.let { " «$it»" } ?: "")
        "headset_plugged" -> "🎧 Навушники підключено"
        "headset_unplugged" -> "🎧 Навушники відключено"
        "screen_on" -> "📱 Екран увімкнено"
        "screen_off" -> "📱 Екран вимкнено"
        "unlock" -> "🔓 Розблоковано"
        "airplane_on" -> "✈ Політ увімкнено"
        "airplane_off" -> "✈ Політ вимкнено"
        "time" -> {
            val p = arg.split(':')
            "⏰ ${p.getOrNull(0)?.padStart(2, '0')}:${p.getOrNull(1)?.padStart(2, '0')}" +
                (p.getOrNull(2)?.takeIf { it.isNotBlank() && it != "1234567" }?.let { " (дні $it)" } ?: "")
        }
        "boot" -> "🚀 Запуск телефону"
        else -> t
    }
}

private fun describeStep(step: String, ctx: android.content.Context? = null): String {
    val s = step.trim().removePrefix("TOOL_CALL:").trim()
    fun pkgOf() = Regex("""package="([^"]*)"""").find(s)?.groupValues?.get(1).orEmpty().let { if (ctx != null) AppUtils.labelFor(ctx, it) else it }
    return when {
        s.startsWith("openApp(") && s.contains("package=") -> "📲 Відкрити «${pkgOf()}»"
        s.startsWith("forceStop(") -> "⛔ Закрити «${pkgOf()}»"
        s.startsWith("clearData(") -> "🧹 Очистити дані «${pkgOf()}»"
        s.startsWith("disableApp(") -> "🚫 Вимкнути «${pkgOf()}»"
        s.startsWith("enableApp(") -> "✅ Увімкнути «${pkgOf()}»"
        s.startsWith("WAIT_UNTIL:") -> "⌛ Чекати: " + s.removePrefix("WAIT_UNTIL:").trim()
        s.startsWith("WAIT:") -> "⏱ Пауза " + s.removePrefix("WAIT:").trim() + " с"
        s.startsWith("IF_NOT:") -> "❓ Якщо НЕ " + s.removePrefix("IF_NOT:").trim().replace("?", "→")
        s.startsWith("IF:") -> "❓ Якщо " + s.removePrefix("IF:").trim().replace("?", "→")
        s.startsWith("SAY:") -> "💬 " + s.removePrefix("SAY:").trim()
        s.startsWith("SET:") -> "📦 " + s.removePrefix("SET:").trim()
        s.startsWith("REPEAT:") -> "🔁 " + s.removePrefix("REPEAT:").trim().replace("?", "разів →")
        else -> "▶ $s"
    }
}

@Composable
fun MacroScreen(onClose: () -> Unit, carBtName: String = "", onCarChange: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val storage = remember { MacroStorage(ctx) }
    var macros by remember { mutableStateOf(storage.get()) }
    val scope = rememberCoroutineScope()
    var runningMacro by remember { mutableStateOf<String?>(null) }
    var progressText by remember { mutableStateOf("") }
    var runJob by remember { mutableStateOf<Job?>(null) }
    var resultLog by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editingMacro by remember { mutableStateOf<Macro?>(null) }
    var autoOn by remember { mutableStateOf(storage.autoEnabled()) }
    var showCarPicker by remember { mutableStateOf(false) }
    fun syncService() = com.dynamicai.launcher.macro.MacroTriggerService.sync(ctx.applicationContext)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AppTheme.colors.backdropStart, AppTheme.colors.backdropEnd)))
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚡ Макроси", color = AppTheme.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("+", color = AppTheme.colors.successLight, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(8.dp).clickable {
                            editingMacro = null
                            showEditor = true
                        })
                    Text("✕", color = AppTheme.colors.textSecondary, fontSize = 24.sp,
                        modifier = Modifier.padding(8.dp).clickable(onClick = onClose))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Макрос можна запускати голосом: скажіть його назву або фразу-тригер, або «запусти макрос …».",
                color = AppTheme.colors.textSecondary, fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppTheme.colors.textPrimary.copy(alpha = 0.05f))
                    .clickable { showCarPicker = true }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🚗", fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Моє авто (Bluetooth)", color = AppTheme.colors.textPrimary, fontSize = 14.sp)
                    Text(if (carBtName.isBlank()) "Не вибрано — тап, щоб обрати пристрій" else carBtName,
                        color = if (carBtName.isBlank()) AppTheme.colors.warning else AppTheme.colors.successLight, fontSize = 12.sp)
                }
                Text("›", color = AppTheme.colors.textSecondary, fontSize = 20.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                autoOn = !autoOn
                storage.setAutoEnabled(autoOn)
                syncService()
            }.padding(vertical = 4.dp)) {
                Text(if (autoOn) "☑" else "☐", color = AppTheme.colors.successLight, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text("Автозапуск за подіями (служба у фоні)", color = AppTheme.colors.textPrimary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))

            if (macros.isEmpty()) {
                Text("Натисни +, щоб створити свій перший макрос", color = AppTheme.colors.textSecondary, fontSize = 14.sp)
            }

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(macros, key = { it.id }) { macro ->
                    MacroCard(
                        macro = macro,
                        isRunning = runningMacro == macro.id,
                        progress = if (runningMacro == macro.id) progressText else "",
                        onRun = {
                            runJob = scope.launch {
                                runningMacro = macro.id
                                progressText = "Старт…"
                                val agent = com.dynamicai.launcher.llm.AgentEngine(ctx, null)
                                try {
                                    val log = MacroRunner(ctx, { agent.runTool(it) }).run(macro) { p ->
                                        progressText = "Крок ${p.index}/${p.total}: ${describeStep(p.step, ctx)}"
                                    }
                                    resultLog = "«${macro.name}»\n\n$log"
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    resultLog = "«${macro.name}» зупинено"
                                } finally {
                                    runningMacro = null
                                    progressText = ""
                                }
                            }
                        },
                        onStop = { runJob?.cancel() },
                        onToggleEnabled = {
                            storage.add(macro.copy(enabled = !macro.enabled))
                            macros = storage.get()
                            syncService()
                        },
                        onEdit = {
                            editingMacro = macro
                            showEditor = true
                        },
                        onDuplicate = {
                            storage.create(name = macro.name + " (копія)", steps = macro.steps, delayMs = macro.delayMs)
                                .let { storage.add(it.copy(icon = macro.icon, colorHex = macro.colorHex, stopOnError = macro.stopOnError)) }
                            macros = storage.get()
                        },
                        onDelete = {
                            storage.delete(macro.id)
                            macros = storage.get()
                            syncService()
                        }
                    )
                }
            }
        }

        if (showEditor) {
            MacroEditorScreen(
                macro = editingMacro,
                carBtName = carBtName,
                onDismiss = { showEditor = false },
                onSave = { saved ->
                    storage.add(saved)
                    macros = storage.get()
                    showEditor = false
                    syncService()
                }
            )
        }

        if (showCarPicker) {
            BtDevicePickerDialog(
                title = "🚗 Bluetooth-пристрій авто", current = carBtName, anyLabel = "Не вибрано",
                onPick = { onCarChange(it); showCarPicker = false },
                onDismiss = { showCarPicker = false }
            )
        }

        resultLog?.let { log ->
            AlertDialog(
                onDismissRequest = { resultLog = null },
                containerColor = AppTheme.colors.backdropStart,
                title = { Text("Результат", color = AppTheme.colors.textPrimary) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 400.dp)) {
                        Text(log, color = AppTheme.colors.textPrimary, fontSize = 13.sp)
                    }
                },
                confirmButton = { TextButton(onClick = { resultLog = null }) { Text("OK", color = AppTheme.colors.successLight) } }
            )
        }
    }
}

@Composable
private fun LabeledField(label: String, value: String, hint: String = "", onChange: (String) -> Unit) {
    Text(label, color = AppTheme.colors.textSecondary, fontSize = 12.sp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = TextStyle(color = AppTheme.colors.textPrimary, fontSize = 15.sp),
        cursorBrush = SolidColor(AppTheme.colors.successLight),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp)).background(AppTheme.colors.textPrimary.copy(alpha = 0.05f))
            .padding(12.dp),
        decorationBox = { inner ->
            if (value.isEmpty() && hint.isNotEmpty()) Text(hint, color = AppTheme.colors.textMuted, fontSize = 14.sp)
            inner()
        }
    )
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) AppTheme.colors.successLight.copy(alpha = 0.3f) else AppTheme.colors.textPrimary.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) { Text(label, color = AppTheme.colors.textPrimary, fontSize = 12.sp) }
}

internal fun pairedBtNames(ctx: android.content.Context): List<String> = try {
    if (android.os.Build.VERSION.SDK_INT >= 31 &&
        ctx.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) emptyList()
    else com.dynamicai.launcher.system.BluetoothManager(ctx).getPairedDevices().map { it.name }.distinct()
} catch (_: Throwable) { emptyList() }

/** Вибір Bluetooth-пристрою (зі спарених). Порожній результат = «будь-який». */
@Composable
fun BtDevicePickerDialog(title: String, current: String, anyLabel: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var names by remember { mutableStateOf(pairedBtNames(ctx)) }
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { names = pairedBtNames(ctx) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.backdropStart,
        title = { Text(title, color = AppTheme.colors.textPrimary, fontSize = 16.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 380.dp)) {
                if (names.isEmpty()) {
                    Text("Спарених пристроїв не знайдено або не надано дозвіл Bluetooth. Спаруйте авто в налаштуваннях Android і дайте дозвіл.",
                        color = AppTheme.colors.textSecondary, fontSize = 13.sp)
                    TextButton(onClick = { permLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT) }) {
                        Text("Дозволити Bluetooth", color = AppTheme.colors.successLight)
                    }
                }
                names.forEach { n ->
                    Text((if (n == current) "✓ " else "") + n,
                        color = if (n == current) AppTheme.colors.successLight else AppTheme.colors.textPrimary, fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(n) }.padding(vertical = 10.dp))
                }
                Text(anyLabel, color = AppTheme.colors.textSecondary, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().clickable { onPick("") }.padding(vertical = 10.dp))
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрити", color = AppTheme.colors.textSecondary) } }
    )
}

/** Повноекранний вибір встановленого додатка з пошуком. Повертає назву пакета ("" = будь-який, якщо задано anyLabel). */
@Composable
fun AppPickerDialog(title: String, anyLabel: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<com.dynamicai.launcher.model.AppInfo>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        apps = try {
            com.dynamicai.launcher.data.AppRepository(ctx).loadInstalledApps().distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
        } catch (_: Throwable) { emptyList() }
    }
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AppTheme.colors.backdropStart, AppTheme.colors.backdropEnd)))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("←", color = AppTheme.colors.textPrimary, fontSize = 24.sp, modifier = Modifier.clickable(onClick = onDismiss).padding(end = 16.dp))
                Text("Оберіть додаток", color = AppTheme.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            LabeledField("Пошук", query, "Назва додатка") { query = it }
            val list = apps
            if (list == null) {
                Text("Завантаження…", color = AppTheme.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
            } else {
                val shown = list.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (anyLabel != null && query.isBlank()) {
                        item(key = "any") {
                            Text(anyLabel, color = AppTheme.colors.accent, fontSize = 15.sp,
                                modifier = Modifier.fillMaxWidth().clickable { onPick("") }.padding(vertical = 12.dp))
                        }
                    }
                    items(shown, key = { it.packageName }) { app ->
                        val icon = remember(app.packageName) {
                            try { (app.bitmap ?: app.icon.toBitmap(96, 96)).asImageBitmap() } catch (_: Throwable) { null }
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(app.packageName) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (icon != null) androidx.compose.foundation.Image(icon, null, Modifier.size(36.dp))
                            else Spacer(Modifier.size(36.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(app.label, color = AppTheme.colors.textPrimary, fontSize = 15.sp)
                                Text(app.packageName, color = AppTheme.colors.textMuted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Зчитування NFC-мітки прямо з редактора (режим читача, поки діалог відкритий). */
@Composable
fun NfcScanDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val activity = remember {
        var c = ctx
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        c as? android.app.Activity
    }
    val adapter = remember { try { android.nfc.NfcAdapter.getDefaultAdapter(ctx) } catch (_: Throwable) { null } }
    var enabled by remember { mutableStateOf(adapter?.isEnabled == true) }
    var found by remember { mutableStateOf("") }
    DisposableEffect(adapter, enabled) {
        val a = activity
        if (adapter != null && enabled && a != null) {
            val h = android.os.Handler(android.os.Looper.getMainLooper())
            try {
                adapter.enableReaderMode(
                    a, { tag -> h.post { found = NfcTagActivity.hex(tag.id) } },
                    android.nfc.NfcAdapter.FLAG_READER_NFC_A or android.nfc.NfcAdapter.FLAG_READER_NFC_B or
                        android.nfc.NfcAdapter.FLAG_READER_NFC_F or android.nfc.NfcAdapter.FLAG_READER_NFC_V, null
                )
            } catch (_: Throwable) {}
        }
        onDispose { try { if (adapter != null && a != null) adapter.disableReaderMode(a) } catch (_: Throwable) {} }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.backdropStart,
        title = { Text("🏷 NFC-мітка", color = AppTheme.colors.textPrimary, fontSize = 16.sp) },
        text = {
            Column {
                when {
                    adapter == null -> Text("На цьому телефоні немає NFC.", color = AppTheme.colors.textSecondary, fontSize = 13.sp)
                    !enabled -> {
                        Text("NFC вимкнено. Увімкніть його в налаштуваннях і поверніться.", color = AppTheme.colors.textSecondary, fontSize = 13.sp)
                        TextButton(onClick = {
                            try { ctx.startActivity(Intent(android.provider.Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
                        }) { Text("Відкрити налаштування NFC", color = AppTheme.colors.successLight) }
                        TextButton(onClick = { enabled = adapter.isEnabled }) { Text("Я увімкнув", color = AppTheme.colors.accent) }
                    }
                    found.isEmpty() -> Text("Піднесіть мітку чи картку до задньої панелі телефону…", color = AppTheme.colors.textPrimary, fontSize = 14.sp)
                    else -> Text("✓ Мітку зчитано\nID: $found", color = AppTheme.colors.successLight, fontSize = 14.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text("Або: будь-яка мітка", color = AppTheme.colors.textSecondary, fontSize = 13.sp,
                    modifier = Modifier.clickable { onPick("") }.padding(vertical = 6.dp))
            }
        },
        confirmButton = {
            if (found.isNotEmpty()) TextButton(onClick = { onPick(found) }) { Text("Використати", color = AppTheme.colors.successLight) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати", color = AppTheme.colors.textSecondary) } }
    )
}

/** Пояснення, який дозвіл потрібен для щойно доданого тригера/кроку, і кнопка його надати. */
@Composable
fun PermissionNeedDialog(need: TriggerPermissions.Need, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { onClose() }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = AppTheme.colors.backdropStart,
        title = { Text(need.title, color = AppTheme.colors.textPrimary, fontSize = 16.sp) },
        text = { Text(need.text, color = AppTheme.colors.textSecondary, fontSize = 13.sp) },
        confirmButton = {
            TextButton(onClick = {
                if (need.runtime != null) launcher.launch(need.runtime)
                else {
                    try { ctx.startActivity(Intent(need.settingsAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
                    onClose()
                }
            }) { Text("Дати дозвіл", color = AppTheme.colors.successLight) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Пізніше", color = AppTheme.colors.textSecondary) } }
    )
}

/** Повноекранний вибір із групами; список завжди прокручується. */
@Composable
private fun PickerScreen(title: String, groups: List<Pair<String, List<StepSpec>>>, onPick: (StepSpec) -> Unit, onClose: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AppTheme.colors.backdropStart, AppTheme.colors.backdropEnd)))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("←", color = AppTheme.colors.textPrimary, fontSize = 24.sp, modifier = Modifier.clickable(onClick = onClose).padding(end = 16.dp))
                Text(title, color = AppTheme.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                groups.forEach { (group, specs) ->
                    item(key = "h_$group") {
                        Text(group, color = AppTheme.colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                    }
                    items(specs, key = { group + it.label }) { spec ->
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                                .background(AppTheme.colors.textPrimary.copy(alpha = 0.05f))
                                .clickable { onPick(spec) }.padding(12.dp)
                        ) {
                            Text(spec.label, color = AppTheme.colors.textPrimary, fontSize = 14.sp)
                            val d = spec.desc.ifBlank { spec.template.replace("{0}", "…").replace("{1}", "…").replace("{2}", "…") }
                            Text(d, color = AppTheme.colors.textMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorSection(title: String, hint: String = "", content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.textPrimary.copy(alpha = 0.05f))
            .border(0.5.dp, AppTheme.colors.glassBorder.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Text(title, color = AppTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (hint.isNotBlank()) Text(hint, color = AppTheme.colors.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
        content()
    }
}

@Composable
fun MacroEditorScreen(
    macro: Macro?,
    carBtName: String,
    onDismiss: () -> Unit,
    onSave: (Macro) -> Unit
) {
    var name by remember { mutableStateOf(macro?.name ?: "") }
    var icon by remember { mutableStateOf(macro?.icon ?: "⚡") }
    var colorHex by remember { mutableStateOf(macro?.colorHex ?: 0xFF2196F3) }
    var trigger by remember { mutableStateOf(macro?.trigger ?: "") }
    var delayMs by remember { mutableStateOf(macro?.delayMs ?: 900L) }
    var stopOnError by remember { mutableStateOf(macro?.stopOnError ?: false) }
    var constraints by remember { mutableStateOf(macro?.constraints ?: "") }
    val triggers = remember { mutableStateListOf<String>().apply { addAll(macro?.triggers ?: emptyList()) } }
    val steps = remember { mutableStateListOf<String>().apply { addAll(macro?.steps ?: emptyList()) } }
    var showTriggerPicker by remember { mutableStateOf(false) }
    var showStepPicker by remember { mutableStateOf(false) }
    var pendingTrigger by remember { mutableStateOf<StepSpec?>(null) }
    var pendingSpec by remember { mutableStateOf<StepSpec?>(null) }
    var editIndex by remember { mutableStateOf(-1) }
    var rawStep by remember { mutableStateOf("") }

    val ctx = LocalContext.current
    var pickFor by remember { mutableStateOf<Pair<Boolean, StepSpec>?>(null) }
    var permNeed by remember { mutableStateOf<TriggerPermissions.Need?>(null) }
    fun commit(isTrigger: Boolean, text: String) {
        if (isTrigger) triggers.add(text) else steps.add(text)
        TriggerPermissions.missing(ctx, text)?.let { permNeed = it }
    }
    fun begin(isTrigger: Boolean, spec: StepSpec) {
        when {
            spec.pick.isNotEmpty() -> pickFor = isTrigger to spec
            spec.params.isEmpty() -> commit(isTrigger, spec.template)
            isTrigger -> pendingTrigger = spec
            else -> pendingSpec = spec
        }
    }
    fun afterPick(isTrigger: Boolean, spec: StepSpec, value: String) {
        val v = value.replace(":", "").replace("\"", "")
        val d = spec.copy(template = spec.template.replace("{0}", v).replace("{1}", "{0}").replace("{2}", "{1}"), pick = "")
        if (d.params.isEmpty()) commit(isTrigger, d.template) else if (isTrigger) pendingTrigger = d else pendingSpec = d
    }

    val iconOptions = listOf("⚡", "🏢", "🚗", "🎬", "🌙", "🏠", "🎮", "🎵", "☕", "💼", "🌞", "🚀")
    val colorOptions = listOf(0xFF2196F3, 0xFF4CAF50, 0xFFFF9800, 0xFF9C27B0, 0xFFE91E63, 0xFF00BCD4)

    androidx.activity.compose.BackHandler(onBack = onDismiss)
    val canSave = name.isNotBlank() && steps.isNotEmpty()

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AppTheme.colors.backdropStart, AppTheme.colors.backdropEnd)))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("←", color = AppTheme.colors.textPrimary, fontSize = 24.sp, modifier = Modifier.clickable(onClick = onDismiss).padding(end = 16.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (macro == null) "Новий макрос" else "Редагування", color = AppTheme.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Подій: ${triggers.size} · кроків: ${steps.size}", color = AppTheme.colors.textMuted, fontSize = 11.sp)
                }
                Text("Зберегти", color = if (canSave) AppTheme.colors.successLight else AppTheme.colors.textMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = canSave) {
                        val base = macro ?: Macro(id = java.util.UUID.randomUUID().toString(), name = name, steps = emptyList())
                        onSave(base.copy(
                            name = name.trim(), icon = icon, colorHex = colorHex, trigger = trigger.trim(),
                            steps = steps.map { it.trim() }.filter { it.isNotBlank() }, delayMs = delayMs, stopOnError = stopOnError,
                            triggers = triggers.toList(), constraints = constraints.trim()
                        ))
                    }.padding(8.dp))
            }
            if (!canSave) Text("Щоб зберегти: вкажіть назву й додайте хоча б один крок", color = AppTheme.colors.textMuted, fontSize = 11.sp)

            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    EditorSection("Основне") {
                        LabeledField("Назва", name, "Наприклад: Їду на роботу") { name = it }
                        Text("Іконка", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            iconOptions.forEach { ic ->
                                Box(
                                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                        .background(if (icon == ic) AppTheme.colors.successLight.copy(alpha = 0.3f) else AppTheme.colors.textPrimary.copy(alpha = 0.05f))
                                        .clickable { icon = ic },
                                    contentAlignment = Alignment.Center
                                ) { Text(ic, fontSize = 20.sp) }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Колір", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            colorOptions.forEach { c ->
                                Box(
                                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(Color(c))
                                        .border(if (colorHex == c) 2.dp else 0.dp, AppTheme.colors.textPrimary, RoundedCornerShape(50))
                                        .clickable { colorHex = c }
                                )
                            }
                        }
                    }
                }
                item {
                    EditorSection("🎙 Голосовий запуск", "Скажіть будь-яку з цих фраз — і макрос запуститься. Назва макросу теж працює.") {
                        LabeledField("Фрази через кому", trigger, "їду на роботу, режим роботи") { trigger = it }
                    }
                }
                item {
                    EditorSection("⚙ Автозапуск за подіями", "Макрос спрацює сам, коли станеться будь-яка з подій нижче.") {
                        if (triggers.isEmpty()) Text("Подій ще немає", color = AppTheme.colors.textMuted, fontSize = 12.sp)
                        triggers.forEachIndexed { i, t ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp))
                                    .background(AppTheme.colors.textPrimary.copy(alpha = 0.06f)).padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(describeTrigger(t, ctx), color = AppTheme.colors.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text("✕", color = AppTheme.colors.danger, fontSize = 16.sp, modifier = Modifier.clickable { triggers.removeAt(i) }.padding(horizontal = 6.dp))
                            }
                        }
                        TextButton(onClick = { showTriggerPicker = true }) { Text("+ Додати подію", color = AppTheme.colors.successLight, fontSize = 13.sp) }
                        if (triggers.isNotEmpty()) {
                            LabeledField("Умови автозапуску (через кому, усі мають виконуватись)", constraints, "time>08:00, time<22:00, charging") { constraints = it }
                            Text("Умови: ${MacroRunner.CONDITIONS}", color = AppTheme.colors.textMuted, fontSize = 11.sp)
                        }
                    }
                }
                item {
                    EditorSection("▶ Що робити (кроки)", "Виконуються по черзі згори вниз. Тап по кроку — редагувати текст.") {
                        if (steps.isEmpty()) Text("Кроків ще немає", color = AppTheme.colors.textMuted, fontSize = 12.sp)
                        steps.forEachIndexed { i, s ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp))
                                    .background(AppTheme.colors.textPrimary.copy(alpha = 0.06f)).padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${i + 1}.", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(describeStep(s, ctx), color = AppTheme.colors.textPrimary, fontSize = 12.sp,
                                    modifier = Modifier.weight(1f).clickable { editIndex = i; rawStep = s })
                                Text("↑", color = AppTheme.colors.accent, fontSize = 18.sp, modifier = Modifier.clickable(enabled = i > 0) {
                                    val t = steps[i]; steps[i] = steps[i - 1]; steps[i - 1] = t
                                }.padding(horizontal = 6.dp))
                                Text("↓", color = AppTheme.colors.accent, fontSize = 18.sp, modifier = Modifier.clickable(enabled = i < steps.size - 1) {
                                    val t = steps[i]; steps[i] = steps[i + 1]; steps[i + 1] = t
                                }.padding(horizontal = 6.dp))
                                Text("⧉", color = AppTheme.colors.accent, fontSize = 16.sp, modifier = Modifier.clickable { steps.add(i + 1, s) }.padding(horizontal = 6.dp))
                                Text("✕", color = AppTheme.colors.danger, fontSize = 15.sp, modifier = Modifier.clickable { steps.removeAt(i) }.padding(horizontal = 6.dp))
                            }
                        }
                        Row {
                            TextButton(onClick = { showStepPicker = true }) { Text("+ Додати крок", color = AppTheme.colors.successLight, fontSize = 13.sp) }
                            TextButton(onClick = { editIndex = -2; rawStep = "" }) { Text("+ Свій крок", color = AppTheme.colors.accent, fontSize = 13.sp) }
                        }
                    }
                }
                item {
                    EditorSection("Параметри виконання") {
                        Text("Пауза між кроками", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0L to "0", 500L to "0.5 с", 900L to "0.9 с", 2000L to "2 с", 3000L to "3 с", 5000L to "5 с").forEach { (ms, lbl) ->
                                Chip(lbl, delayMs == ms) { delayMs = ms }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { stopOnError = !stopOnError }.padding(vertical = 4.dp)) {
                            Text(if (stopOnError) "☑" else "☐", color = AppTheme.colors.successLight, fontSize = 20.sp)
                            Spacer(Modifier.width(8.dp))
                            Text("Зупинитись, якщо крок завершився помилкою", color = AppTheme.colors.textPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        if (showTriggerPicker) PickerScreen("Оберіть подію", TRIGGER_GROUPS, onPick = { spec ->
            showTriggerPicker = false
            begin(true, spec)
        }, onClose = { showTriggerPicker = false })

        if (showStepPicker) PickerScreen("Оберіть дію", STEP_CATALOG, onPick = { spec ->
            showStepPicker = false
            begin(false, spec)
        }, onClose = { showStepPicker = false })

        pickFor?.let { (isTrig, spec) ->
            val close = { pickFor = null }
            when (spec.pick) {
                "app" -> AppPickerDialog(spec.label, if (isTrig) "Будь-який додаток" else null,
                    onPick = { v -> close(); afterPick(isTrig, spec, v) }, onDismiss = { close() })
                "bt" -> BtDevicePickerDialog(spec.label, carBtName, "Будь-який пристрій",
                    onPick = { v -> close(); afterPick(isTrig, spec, v) }, onDismiss = { close() })
                "btcar" -> BtDevicePickerDialog(spec.label, carBtName,
                    if (carBtName.isNotBlank()) "🚗 Моє авто ($carBtName)" else "Авто з налаштувань",
                    onPick = { v -> close(); afterPick(isTrig, spec, v) }, onDismiss = { close() })
                "nfc" -> NfcScanDialog(onPick = { v -> close(); afterPick(isTrig, spec, v) }, onDismiss = { close() })
                else -> close()
            }
        }

        permNeed?.let { PermissionNeedDialog(it) { permNeed = null } }

        pendingTrigger?.let { spec ->
            val values = remember(spec) { mutableStateListOf<String>().apply { addAll(spec.params.map { it.second }) } }
            AlertDialog(
                onDismissRequest = { pendingTrigger = null },
                containerColor = AppTheme.colors.backdropStart,
                title = { Text(spec.label, color = AppTheme.colors.textPrimary, fontSize = 16.sp) },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) { spec.params.forEachIndexed { i, p -> LabeledField(p.first, values[i]) { values[i] = it } } } },
                confirmButton = {
                    TextButton(onClick = {
                        var t = spec.template
                        values.forEachIndexed { i, v -> t = t.replace("{$i}", v.trim().replace(":", "")) }
                        pendingTrigger = null
                        commit(true, t)
                    }) { Text("Додати", color = AppTheme.colors.successLight) }
                },
                dismissButton = { TextButton(onClick = { pendingTrigger = null }) { Text("Скасувати", color = AppTheme.colors.textSecondary) } }
            )
        }

        pendingSpec?.let { spec ->
            val values = remember(spec) { mutableStateListOf<String>().apply { addAll(spec.params.map { it.second }) } }
            AlertDialog(
                onDismissRequest = { pendingSpec = null },
                containerColor = AppTheme.colors.backdropStart,
                title = { Text(spec.label, color = AppTheme.colors.textPrimary, fontSize = 16.sp) },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) { spec.params.forEachIndexed { i, p -> LabeledField(p.first, values[i]) { values[i] = it } } } },
                confirmButton = {
                    TextButton(onClick = {
                        var t = spec.template
                        values.forEachIndexed { i, v -> t = t.replace("{$i}", v.replace("\"", "")) }
                        pendingSpec = null
                        commit(false, t.trim())
                    }) { Text("Додати", color = AppTheme.colors.successLight) }
                },
                dismissButton = { TextButton(onClick = { pendingSpec = null }) { Text("Скасувати", color = AppTheme.colors.textSecondary) } }
            )
        }

        if (editIndex != -1) {
            AlertDialog(
                onDismissRequest = { editIndex = -1 },
                containerColor = AppTheme.colors.backdropStart,
                title = { Text(if (editIndex == -2) "Свій крок" else "Редагувати крок", color = AppTheme.colors.textPrimary, fontSize = 16.sp) },
                text = {
                    Column {
                        LabeledField("Крок", rawStep, "toggleWifi(state=\"on\")  |  WAIT: 3  |  IF: online ? carMode()") { rawStep = it }
                        Text("Службові: WAIT: сек · WAIT_UNTIL: умова сек · IF: умова ? крок · IF_NOT: умова ? крок · SAY: текст · SET: ім'я=значення · REPEAT: N ? крок.\nУмови: ${MacroRunner.CONDITIONS}",
                            color = AppTheme.colors.textMuted, fontSize = 11.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (rawStep.isNotBlank()) {
                            if (editIndex >= 0) steps[editIndex] = rawStep.trim() else steps.add(rawStep.trim())
                        }
                        editIndex = -1
                    }) { Text("OK", color = AppTheme.colors.successLight) }
                },
                dismissButton = { TextButton(onClick = { editIndex = -1 }) { Text("Скасувати", color = AppTheme.colors.textSecondary) } }
            )
        }
    }
}

@Composable
fun MacroCard(
    macro: Macro, isRunning: Boolean, progress: String,
    onRun: () -> Unit, onStop: () -> Unit, onToggleEnabled: () -> Unit, onEdit: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit
) {
    val color = Color(macro.colorHex)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.textPrimary.copy(alpha = 0.05f))
            .border(0.5.dp, if (isRunning) color else AppTheme.colors.glassBorder.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(macro.icon, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).clickable(enabled = !isRunning, onClick = onEdit)) {
            Text(macro.name, color = AppTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            val sub = if (isRunning) progress else
                "${macro.steps.size} кроків" + (if (macro.trigger.isNotBlank()) " · 🎙 ${macro.trigger}" else "") +
                    (if (macro.triggers.isNotEmpty()) " · ⚙ ${macro.triggers.size} подій" else "") + " · тап — редагувати"
            Text(sub, color = if (isRunning) color else AppTheme.colors.textSecondary, fontSize = 11.sp, maxLines = 2)
        }
        if (isRunning) {
            Text("⏹", fontSize = 20.sp, modifier = Modifier.clickable(onClick = onStop).padding(8.dp))
        } else {
            if (macro.triggers.isNotEmpty()) {
                Text(if (macro.enabled) "🔔" else "🔕", fontSize = 18.sp, modifier = Modifier.clickable(onClick = onToggleEnabled).padding(6.dp))
            }
            Text("▶️", fontSize = 20.sp, modifier = Modifier.clickable(onClick = onRun).padding(8.dp))
            Text("⧉", fontSize = 18.sp, color = AppTheme.colors.accent, modifier = Modifier.clickable(onClick = onDuplicate).padding(6.dp))
            Text("🗑️", fontSize = 18.sp, modifier = Modifier.padding(4.dp).clickable { onDelete() })
        }
    }
}
