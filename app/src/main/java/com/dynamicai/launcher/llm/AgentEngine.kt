package com.dynamicai.launcher.llm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.app.SearchManager
import android.view.KeyEvent
import android.media.AudioManager
import com.dynamicai.launcher.data.AppRepository
import com.dynamicai.launcher.data.WildRiftRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AgentEngine(private val context: Context, private val llm: LlmEngine) {
    private val appRepo = AppRepository(context)
    private val wildRiftRepo = WildRiftRepository(context)

    private val toolsDescription = """
        Доступні інструменти:
        1. openApp(name: String) -> Відкриває додаток на телефоні за назвою.
        2. getWildRiftAdvice(myChampion: String, enemies: String) -> Поради щодо предметів у Wild Rift.
        3. getWeather(city: String) -> Поточна погода в місті.
        4. setAlarm(hour: Int, minute: Int, message: String) -> Встановлює будильник.
        5. searchGoogle(query: String) -> Шукає інформацію в Google.
        6. playYouTube(query: String) -> Шукає та відкриває відео на YouTube.
        7. dialNumber(number: String) -> Відкриває діалер з набраним номером.
        8. openCamera() -> Відкриває додаток камери.
        9. openUrl(url: String) -> Відкриває веб-сайт за посиланням.
        10. navigateTo(destination: String) -> Відкриває Google Maps з маршрутом.
        11. closeApp(name: String) -> Закриває додаток.
        12. playMusic(query: String) -> Відтворює музику (через системний плеєр).
        13. tapText(text: String) -> Натискає на елемент з текстом на екрані (для автоматизації).
    """.trimIndent()

    suspend fun processWithTools(question: String): String = withContext(Dispatchers.IO) {
        val prompt = """
            Ти — агент-асистент. Користувач запитав: "$question"
            
            $toolsDescription
            
            Якщо для відповіді потрібен інструмент, напиши ТІЛЬКИ команду у такому форматі:
            TOOL_CALL: назва_інструменту(параметр1="значення", параметр2="значення")
            
            Приклади:
            Користувач: "Розбуди мене о 7 ранку" -> TOOL_CALL: setAlarm(hour="7", minute="0", message="Доброго ранку!")
            Користувач: "Знайди в ютубі котиків" -> TOOL_CALL: playYouTube(query="котики")
            Користувач: "Відкрий YouTube" -> TOOL_CALL: openApp(name="YouTube")
            Користувач: "Включи музику" -> TOOL_CALL: openApp(name="Музика")
            Користувач: "Відкрий галерею" -> TOOL_CALL: openApp(name="Галерея")
            Користувач: "Закрий YouTube" -> TOOL_CALL: closeApp(name="YouTube")
            Користувач: "Увімкни музику" -> TOOL_CALL: playMusic(query="")
            Користувач: "Увімкни гурт Океан Ельзи" -> TOOL_CALL: playMusic(query="Океан Ельзи")
            
            Якщо інструмент не потрібен, просто дай коротку текстову відповідь.
        """.trimIndent()

        val response = llm.complete(prompt)?.trim() ?: return@withContext "Помилка агента"

        if (response.startsWith("TOOL_CALL:")) {
            val toolCall = response.substringAfter("TOOL_CALL:").trim()
            
            fun getArg(key: String): String {
                return Regex("""${key}="([^"]+)"""").find(toolCall)?.groupValues?.get(1) ?: ""
            }
            
            return@withContext try {
                when {
                    toolCall.startsWith("openApp") -> {
                    val appName = getArg("name")
                    val apps = appRepo.loadInstalledApps()
                    
                    // Шукаємо додаток за назвою (ігноруючи регістр та пробіли)
                    val searchName = appName.lowercase().trim()
                    var app = apps.find { it.label.lowercase().contains(searchName) }
                    
                    // Спроба розпізнати загальні назви (Галерея, Музика, Камера)
                    if (app == null) {
                        val keyword = when {
                            searchName.contains("галере") || searchName.contains("фото") -> "фото"
                            searchName.contains("музик") -> "music"
                            searchName.contains("браузер") -> "chrome"
                            searchName.contains("ютуб") || searchName.contains("youtube") -> "youtube"
                            else -> ""
                        }
                        if (keyword.isNotEmpty()) {
                            app = apps.find { it.label.lowercase().contains(keyword) }
                        }
                    }

                    if (app != null) {
                        val success = appRepo.launchApp(app.packageName)
                        if (success) "Відкриваю ${app.label}"
                        else "Не вдалося відкрити ${app.label}"
                    } else {
                        // Якщо додаток не знайдено, пробуємо системні інтенти
                        try {
                            val intent = when {
                                searchName.contains("галере") || searchName.contains("фото") -> 
                                    Intent(Intent.ACTION_VIEW).apply { type = "image/*" }
                                searchName.contains("музик") -> 
                                    Intent(Intent.ACTION_VIEW).apply { type = "audio/*" }
                                searchName.contains("камер") -> 
                                    Intent("android.media.action.STILL_IMAGE_CAMERA")
                                searchName.contains("ютуб") || searchName.contains("youtube") -> 
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://youtube.com"))
                                else -> null
                            }
                            if (intent != null) {
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                                "Відкриваю системний додаток для '$appName'"
                            } else {
                                "Додаток '$appName' не знайдено."
                            }
                        } catch (e: Exception) {
                            "Додаток '$appName' не знайдено."
                        }
                    }
                }
                    
                    toolCall.startsWith("getWildRiftAdvice") -> {
                        val me = getArg("myChampion")
                        val enemies = getArg("enemies")
                        val enemyList = enemies.split(",").map { it.trim() }
                        val meChamp = wildRiftRepo.findChampion(me)
                        val enemyChamps = enemyList.mapNotNull { wildRiftRepo.findChampion(it) }
                        if (meChamp != null) wildRiftRepo.buildAdviceContext(meChamp, enemyChamps)
                        else "Чемпіона '$me' не знайдено."
                    }
                    
                    toolCall.startsWith("getWeather") -> {
                        val city = getArg("city").ifEmpty { "Київ" }
                        "У місті $city зараз сонячно, +20°C. (Демо)"
                    }
                    
                    toolCall.startsWith("setAlarm") -> {
                        val hour = getArg("hour").toIntOrNull() ?: 7
                        val minute = getArg("minute").toIntOrNull() ?: 0
                        val msg = getArg("message").ifEmpty { "Будильник" }
                        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                            putExtra(AlarmClock.EXTRA_HOUR, hour)
                            putExtra(AlarmClock.EXTRA_MINUTES, minute)
                            putExtra(AlarmClock.EXTRA_MESSAGE, msg)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        "Будильник на $hour:${String.format("%02d", minute)} встановлено."
                    }
                    
                    toolCall.startsWith("searchGoogle") -> {
                        val query = getArg("query")
                        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra("query", query)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        "Шукаю в Google: $query"
                    }
                    
                    toolCall.startsWith("playYouTube") -> {
                        val query = getArg("query")
                        val intent = Intent(Intent.ACTION_SEARCH).apply {
                            putExtra("query", query)
                            setPackage("com.google.android.youtube")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(intent)
                            "Відкриваю YouTube: $query"
                        } catch (e: Exception) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                            "Відкриваю YouTube у браузері: $query"
                        }
                    }
                    
                    toolCall.startsWith("dialNumber") -> {
                        val number = getArg("number")
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        "Відкриваю набір номера: $number"
                    }
                    
                    toolCall.startsWith("openCamera") -> {
                        val intent = Intent("android.media.action.STILL_IMAGE_CAMERA").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        "Відкриваю камеру."
                    }
                    
                    toolCall.startsWith("openUrl") -> {
                        val url = getArg("url")
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        "Відкриваю посилання: $url"
                    }
                    
                    toolCall.startsWith("navigateTo") -> {
                        val dest = getArg("destination")
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$dest")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        "Будую маршрут до: $dest"
                    }
                    
                    toolCall.startsWith("closeApp") -> {
                        // Імітуємо натискання кнопки "Додому" (згортає додаток)
                        val intent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        "Згортаю додаток на головний екран."
                    }
                    
                    toolCall.startsWith("playMusic") -> {
                        val query = getArg("query").ifEmpty { "музика" }
                        try {
                            if (!com.dynamicai.launcher.accessibility.AppAutomationService.isEnabled()) {
                                "Спочатку увімкніть сервіс автоматизації в налаштуваннях Android (Доступність)."
                            } else {
                                val uri = Uri.parse("https://music.youtube.com/search?q=$query")
                                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                    setPackage("com.google.android.apps.youtube.music")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                                
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    val success = com.dynamicai.launcher.accessibility.AppAutomationService.clickPlayInYtMusic()
                                    android.util.Log.d("AgentEngine", "YT Music Play click result: $success")
                                }, 4000)
                                
                                "Вмикаю: $query"
                            }
                        } catch (e: Exception) {
                            "Не вдалося відкрити YT Music."
                        }
                    }
                    
                    toolCall.startsWith("tapText") -> {
                        val text = getArg("text")
                        if (!com.dynamicai.launcher.accessibility.AppAutomationService.isEnabled()) {
                            "Сервіс автоматизації вимкнено."
                        } else {
                            val ok = com.dynamicai.launcher.accessibility.AppAutomationService.clickText(text)
                            if (ok) "Натиснув: $text" else "Не знайшов елемент '$text' на екрані."
                        }
                    }
                    
                    else -> "Невідомий інструмент: $toolCall"
                }
            } catch (e: Exception) {
                "Помилка виконання інструменту: ${e.message}"
            }
        } else {
            response
        }
    }
}
