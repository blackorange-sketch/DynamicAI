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

class AgentEngine(private val context: Context, private val llm: LlmEngine) {
    private val appRepo = AppRepository(context)
    private val wildRiftRepo = WildRiftRepository(context)
    private val notesStorage = com.dynamicai.launcher.note.NotesStorage(context)

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
        17. searchWeb(query: String) -> Шукає інформацію в інтернеті та повертає факти (для питань 'хто такий', 'що таке', новини).
    """.trimIndent()

    suspend fun processWithTools(question: String): String = withContext(Dispatchers.IO) {
        var currentContext = "Запит користувача: $question"
        var iteration = 0
        val maxIterations = 4 // Максимум 4 кроки для складних задач

        while (iteration < maxIterations) {
            val prompt = """
                Ти — агент-асистент. Проаналізуй поточний контекст і виріши, що робити далі.
                
                $toolsDescription
                
                [ПОТОЧНИЙ КОНТЕКСТ]
                $currentContext
                
                Якщо тобі потрібно виконати дію (відкрити додаток, пошукати в інтернеті, увімкнути музику), напиши ТІЛЬКИ команду у форматі:
                TOOL_CALL: назва_інструменту(параметр1="значення", параметр2="значення")
                
                Якщо ти вже маєш всю інформацію для відповіді, напиши фінальну відповідь користувачу.
            """.trimIndent()

            val response = llm.complete(prompt)?.trim() ?: return@withContext "Помилка агента"

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
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, msg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "Будильник на $hour:${String.format("%02d", minute)} встановлено."
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
