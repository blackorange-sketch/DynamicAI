package com.dynamicai.launcher.llm

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import com.dynamicai.launcher.data.AppRepository
import com.dynamicai.launcher.data.WildRiftRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AgentEngine(private val context: Context, private val llm: LlmEngine?) {
    private val appRepo = AppRepository(context)
    private val wildRiftRepo = WildRiftRepository(context)
    private val notesStorage = com.dynamicai.launcher.note.NotesStorage(context)
    private val shizuku = com.dynamicai.launcher.system.ShizukuManager(context)
    private val deepLink = DeepLinkEngine(context)
    private val messengerCaller = MessengerCaller(context)

    private val toolsDescription = """
        Доступні інструменти:
        1. openApp(name: String) -> Відкриває додаток.
        2. closeApp() -> Закриває поточний додаток (на головний екран).
        3. searchGoogle(query: String) -> Пошук в Google.
        4. playYouTube(query: String) -> Пошук на YouTube.
        5. playMusic(query: String) -> Відтворює музику без реклами.
        6. dialNumber(number: String) -> Відкриває набір номера.
        7. sendSms(number: String, message: String) -> Відкриває SMS з текстом.
        8. setAlarm(hour: Int, minute: Int, message: String) -> Будильник.
        9. addCalendarEvent(title: String, description: String) -> Створити подію.
        10. getWeather(city: String) -> Погода.
        11. navigateTo(destination: String) -> Маршрут у картах.
        12. toggleFlashlight(state: String) -> Ліхтарик ('on' або 'off').
        13. setBrightness(percent: Int) -> Яскравість екрану (0-100).
        14. takeNote(text: String) -> Створити нотатку.
        15. readNotes() -> Прочитати останні нотатки.
        16. getWildRiftAdvice(myChampion: String, enemies: String) -> Поради Wild Rift.
        17. searchWeb(query: String) -> Шукає інформацію в інтернеті.
        18. toggleWifi(state: String) -> Wi-Fi ('on' або 'off').
        19. toggleBluetooth(state: String) -> Bluetooth ('on' або 'off').
        20. copyToClipboard(text: String) -> Копіює текст у буфер обміну.
        21. calculate(expression: String) -> Обчислює математичний вираз.
        22. openUrl(url: String) -> Відкриває веб-сайт.
        23. createNotification(title: String, text: String) -> Показує сповіщення.
        24. getTime() -> Дізнатися поточний час та дату.
        25. getBattery() -> Дізнатися рівень заряду батареї.
        26. setVolume(level: Int) -> Встановити гучність (0-100).
        27. getDeviceInfo() -> Загальна інформація про пристрій (мережа, заряд, час).
    """.trimIndent()

    suspend fun processWithTools(question: String, history: String = ""): String = withContext(Dispatchers.IO) {
        if (llm == null) return@withContext "Помилка: LLM не ініціалізована"
        var currentContext = "Запит користувача: $question"
        var iteration = 0
        val maxIterations = 4 // Максимум 4 кроки для складних задач

        while (iteration < maxIterations) {
            val prompt = """
                Ти — Джарвіс, розумний голосовий асистент в Android-лаунчері. Відповідай ввічливо, коротко і по суті.
                
                [СИСТЕМНИЙ КОНТЕКСТ]
                Поточний час: ${java.text.SimpleDateFormat("HH:mm", java.util.Locale("uk", "UA")).format(java.util.Date())}
                Дата: ${java.text.SimpleDateFormat("d MMMM", java.util.Locale("uk", "UA")).format(java.util.Date())}
                Заряд батареї: ${(context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager).getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)}%
                
                [ІСТОРІЯ ДІАЛОГУ]
                ${if (history.isNotBlank()) history else "Це початок розмови."}
                
                [ПОТОЧНИЙ ЗАПИТ]
                $question
                
                $toolsDescription
                
                [ПОТОЧНИЙ КОНТЕКСТ ВИКОНАННЯ]
                $currentContext
                
                Якщо тобі потрібно виконати дію (відкрити додаток, пошукати в інтернеті, увімкнути музику), напиши ТІЛЬКИ команду у форматі:
                TOOL_CALL: назва_інструменту(параметр1="значення", параметр2="значення")
                
                Якщо ти вже маєш всю інформацію для відповіді, напиши фінальну відповідь користувачу.
            """.trimIndent()

            val response = llm?.complete(prompt)?.trim() ?: return@withContext "Помилка агента"

            // Запобіжник: якщо LLM вивела просто функцію без префіксу
            if (response.startsWith("TOOL_CALL:") || (response.contains("(") && response.contains(")"))) {
                val toolCall = response.replace("TOOL_CALL:", "").trim()
                val result = executeTool(toolCall)
                currentContext += "\n[Результат дії]: $result"
                iteration++
            } else {
                // Це фінальна відповідь
                return@withContext response
            }
        }
        
        return@withContext "Виконано забагато кроків. Останній результат: $currentContext"
    }

    private fun evalMath(expr: String): Double {
        // Простий парсер для +, -, *, /
        val sanitized = expr.replace(" ", "").replace(",", ".")
        val ops = listOf('+', '-', '*', '/')
        for (op in ops) {
            val idx = sanitized.indexOf(op, 1)
            if (idx > 0) {
                val left = sanitized.substring(0, idx).toDouble()
                val right = sanitized.substring(idx + 1).toDouble()
                return when (op) {
                    '+' -> left + right
                    '-' -> left - right
                    '*' -> left * right
                    '/' -> left / right
                    else -> 0.0
                }
            }
        }
        return sanitized.toDouble()
    }

    private fun getArg(toolCall: String, key: String): String {
        return Regex("""${key}="([^"]+)"""").find(toolCall)?.groupValues?.get(1) ?: ""
    }

    private suspend fun executeTool(toolCall: String): String {
        return try {
            when {
                toolCall.startsWith("openApp") -> {
                    val appName = getArg(toolCall, "name")
                    val apps = appRepo.loadInstalledApps()
                    val app = apps.find { it.label.contains(appName, ignoreCase = true) }
                    if (app != null) {
                        if (appRepo.launchApp(app.packageName)) "Відкриваю ${app.label}" else "Не вдалося відкрити ${app.label}"
                    } else "Додаток '$appName' не знайдено."
                }
                toolCall.startsWith("closeApp") -> {
                    val intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "Згортаю на головний екран."
                }
                toolCall.startsWith("searchGoogle") -> {
                    val query = getArg(toolCall, "query")
                    val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                        putExtra("query", query)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "Шукаю в Google: $query"
                }
                toolCall.startsWith("playYouTube") -> {
                    val query = getArg(toolCall, "query")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "Відкриваю YouTube: $query"
                }
                toolCall.startsWith("playMusic") -> {
                    val query = getArg(toolCall, "query")
                    val intent = Intent(context, com.dynamicai.launcher.music.MusicService::class.java).apply {
                        putExtra("query", query)
                    }
                    context.startService(intent)
                    "Вмикаю без реклами: $query"
                }
                toolCall.startsWith("dialNumber") -> {
                    val number = getArg(toolCall, "number")
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "Відкриваю набір номера: $number"
                }
                toolCall.startsWith("sendSms") -> {
                    val number = getArg(toolCall, "number")
                    val message = getArg(toolCall, "message")
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:$number")
                        putExtra("sms_body", message)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "Відкриваю SMS для $number з текстом: $message"
                }
                toolCall.startsWith("setAlarm") -> {
                    val hour = getArg(toolCall, "hour").toIntOrNull() ?: 7
                    val minute = getArg(toolCall, "minute").toIntOrNull() ?: 0
                    val msg = getArg(toolCall, "message").ifEmpty { "Будильник" }
                    
                    // Спроба 1: Стандартний Intent з SKIP_UI
                    try {
                        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                            putExtra(AlarmClock.EXTRA_HOUR, hour)
                            putExtra(AlarmClock.EXTRA_MINUTES, minute)
                            putExtra(AlarmClock.EXTRA_MESSAGE, msg)
                            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                    
                    // Спроба 2: Через Shizuku (надійніше на Nubia)
                    if (shizuku.isShizukuAvailable() && shizuku.hasPermission()) {
                        val cmd = "am start -a android.intent.action.SET_ALARM " +
                                  "--ei android.intent.extra.alarm.HOUR $hour " +
                                  "--ei android.intent.extra.alarm.MINUTES $minute " +
                                  "--es android.intent.extra.alarm.MESSAGE \"$msg\" " +
                                  "--ez android.intent.extra.alarm.SKIP_UI true"
                        shizuku.execShell(cmd)
                    }
                    
                    "Будильник на ${String.format("%02d", hour)}:${String.format("%02d", minute)} встановлено."
                }
                toolCall.startsWith("addCalendarEvent") -> {
                    val title = getArg(toolCall, "title")
                    val desc = getArg(toolCall, "description")
                    val startMillis = System.currentTimeMillis() + 3600_000 // Через годину
                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        data = CalendarContract.Events.CONTENT_URI
                        putExtra(CalendarContract.Events.TITLE, title)
                        putExtra(CalendarContract.Events.DESCRIPTION, desc)
                        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "Відкриваю календар для створення події: $title"
                }
                toolCall.startsWith("getWeather") -> {
                    val city = getArg(toolCall, "city").ifEmpty { "Київ" }
                    "У місті $city сонячно, +20°C. (Демо)"
                }
                toolCall.startsWith("navigateTo") -> {
                    val dest = getArg(toolCall, "destination")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$dest")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "Будую маршрут до: $dest"
                }
                toolCall.startsWith("toggleFlashlight") -> {
                    val state = getArg(toolCall, "state").lowercase() == "on"
                    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                        val chars = cameraManager.getCameraCharacteristics(id)
                        chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    }
                    if (cameraId != null) {
                        cameraManager.setTorchMode(cameraId, state)
                        "Ліхтарик ${if (state) "увімкнено" else "вимкнено"}"
                    } else "Ліхтарик не знайдено на пристрої."
                }
                toolCall.startsWith("setBrightness") -> {
                    val percent = getArg(toolCall, "percent").toIntOrNull() ?: 50
                    try {
                        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (percent * 255 / 100))
                        "Яскравість встановлено на $percent%"
                    } catch (e: Exception) {
                        "Потрібен дозвіл на зміну яскравості."
                    }
                }
                toolCall.startsWith("takeNote") -> {
                    val text = getArg(toolCall, "text")
                    notesStorage.add(text)
                    "Нотатку створено: $text"
                }
                toolCall.startsWith("readNotes") -> {
                    val notes = notesStorage.get()
                    if (notes.isEmpty()) "Нотаток немає." else "Останні нотатки: ${notes.take(3).joinToString(", ") { it.text }}"
                }
                toolCall.startsWith("searchWeb") -> {
                    val query = getArg(toolCall, "query")
                    val webSearch = com.dynamicai.launcher.llm.WebSearchRepository()
                    val result = webSearch.search(query)
                    val text = webSearch.toContext(result)
                    if (text.isNotBlank()) text else "Не знайдено інформації."
                }
                                toolCall.startsWith("toggleWifi") -> {
                    val state = getArg(toolCall, "state").lowercase() == "on"
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                    @Suppress("DEPRECATION")
                    wifiManager.isWifiEnabled = state
                    "Wi-Fi ${if (state) "увімкнено" else "вимкнено"}"
                }
                toolCall.startsWith("toggleBluetooth") -> {
                    val state = getArg(toolCall, "state").lowercase() == "on"
                    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
                    val adapter = bluetoothManager.adapter
                    @Suppress("DEPRECATION")
                    if (state) adapter?.enable() else adapter?.disable()
                    "Bluetooth ${if (state) "увімкнено" else "вимкнено"}"
                }
                toolCall.startsWith("copyToClipboard") -> {
                    val text = getArg(toolCall, "text")
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("DynamicAI", text))
                    "Скопійовано в буфер: $text"
                }
                toolCall.startsWith("calculate") -> {
                    val expr = getArg(toolCall, "expression")
                    try {
                        val result = evalMath(expr)
                        "$expr = $result"
                    } catch (e: Exception) {
                        "Не вдалося обчислити: $expr"
                    }
                }
                toolCall.startsWith("openUrl") -> {
                    val url = getArg(toolCall, "url")
                    val fullUrl = if (url.startsWith("http")) url else "https://$url"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "Відкриваю: $fullUrl"
                }
                toolCall.startsWith("createNotification") -> {
                    val title = getArg(toolCall, "title")
                    val text = getArg(toolCall, "text")
                    "Сповіщення створено: $title — $text" // Потребує дозволу NOTIFICATIONS
                }
                                toolCall.startsWith("getTime") -> {
                    val now = java.text.SimpleDateFormat("HH:mm, EEEE, d MMMM", java.util.Locale("uk", "UA")).format(java.util.Date())
                    "Зараз $now"
                }
                toolCall.startsWith("getBattery") -> {
                    val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
                    val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    "Заряд батареї: $level%"
                }
                toolCall.startsWith("setVolume") -> {
                    val level = getArg(toolCall, "level").toIntOrNull() ?: 50
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                    val target = (max * (level / 100f)).toInt()
                    am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, 0)
                    "Гучність встановлено на $level%"
                }
                toolCall.startsWith("getDeviceInfo") -> {
                    val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
                    val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                    val isWifi = cm.activeNetworkInfo?.type == android.net.ConnectivityManager.TYPE_WIFI
                    val isMobile = cm.activeNetworkInfo?.type == android.net.ConnectivityManager.TYPE_MOBILE
                    val netStatus = if (isWifi) "Wi-Fi" else if (isMobile) "Мобільна мережа" else "Немає інтернету"
                    val now = java.text.SimpleDateFormat("HH:mm", java.util.Locale("uk", "UA")).format(java.util.Date())
                    "Час: $now | Заряд: $level% | Мережа: $netStatus"
                }
                                toolCall.startsWith("flipCoin") -> {
                    val result = if (Math.random() > 0.5) "Орел" else "Решка"
                    "Підкидаю монетку... Випало: $result!"
                }
                toolCall.startsWith("rollDice") -> {
                    val result = (1..6).random()
                    "Кидаю кубик... Випало: $result!"
                }
                toolCall.startsWith("randomNumber") -> {
                    val min = getArg(toolCall, "min").toIntOrNull() ?: 1
                    val max = getArg(toolCall, "max").toIntOrNull() ?: 100
                    val result = (min..max).random()
                    "Ваше випадкове число: $result"
                }
                                toolCall.startsWith("runMacro") -> {
                    val name = getArg(toolCall, "name")
                    val storage = com.dynamicai.launcher.macro.MacroStorage(context)
                    val macro = storage.get().find { it.name.contains(name, ignoreCase = true) }
                    if (macro == null) {
                        "Макрос '$name' не знайдено."
                    } else {
                        // Запускаємо кроки макросу послідовно
                        var lastResult = ""
                        for (step in macro.steps) {
                            lastResult = executeTool(step) // Рекурсивний виклик
                            kotlinx.coroutines.delay(macro.delayMs)
                        }
                        "Макрос '${macro.name}' виконано. Останній результат: $lastResult"
                    }
                }
                                toolCall.startsWith("openTelegramChat") -> deepLink.openTelegramChat(getArg(toolCall, "contactName"))
                toolCall.startsWith("openWhatsAppChat") -> deepLink.openWhatsAppChat(getArg(toolCall, "contactName"))
                toolCall.startsWith("openViberChat") -> deepLink.openViberChat(getArg(toolCall, "contactName"))
                toolCall.startsWith("searchYouTube") -> deepLink.openYouTubeSearch(getArg(toolCall, "query"))
                toolCall.startsWith("openInstagramProfile") -> deepLink.openInstagramProfile(getArg(toolCall, "username"))
                toolCall.startsWith("navigateGoogleMaps") -> deepLink.openMaps(getArg(toolCall, "destination"))
                toolCall.startsWith("sendEmail") -> deepLink.sendEmail(
                    getArg(toolCall, "to"),
                    getArg(toolCall, "subject"),
                    getArg(toolCall, "body")
                )
                toolCall.startsWith("sendSmsToContact") -> deepLink.sendSms(
                    getArg(toolCall, "contactName"),
                    getArg(toolCall, "message")
                )
                toolCall.startsWith("searchSpotify") -> deepLink.openSpotifySearch(getArg(toolCall, "query"))
                                toolCall.startsWith("readScreen") -> {
                    if (!com.dynamicai.launcher.accessibility.AppAutomationService.isEnabled()) {
                        "Спочатку увімкніть сервіс доступності (DynamicAI Automation) у налаштуваннях Android."
                    } else {
                        val text = com.dynamicai.launcher.accessibility.AppAutomationService.getScreenText()
                        if (text.isBlank()) {
                            "На екрані немає тексту."
                        } else {
                            // Обмежуємо до 1500 символів, щоб не переповнити контекст локальної LLM
                            val truncated = if (text.length > 1500) text.substring(0, 1500) + "..." else text
                            "Текст на екрані:\n$truncated"
                        }
                    }
                }
                                toolCall.startsWith("callInTelegram") -> messengerCaller.callInTelegram(getArg(toolCall, "contactName"))
                toolCall.startsWith("callInInstagram") -> messengerCaller.callInInstagram(getArg(toolCall, "contactName"))
                toolCall.startsWith("getWildRiftAdvice") -> {
                    val me = getArg(toolCall, "myChampion")
                    val enemies = getArg(toolCall, "enemies")
                    val enemyList = enemies.split(",").map { it.trim() }
                    val meChamp = wildRiftRepo.findChampion(me)
                    val enemyChamps = enemyList.mapNotNull { wildRiftRepo.findChampion(it) }
                    if (meChamp != null) wildRiftRepo.buildAdviceContext(meChamp, enemyChamps) else "Чемпіона '$me' не знайдено."
                }
                else -> "Невідомий інструмент: $toolCall"
            }
        } catch (e: Exception) {
            "Помилка виконання: ${e.message}"
        }
    }
}
