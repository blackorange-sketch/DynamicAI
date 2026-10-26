package com.dynamicai.launcher.llm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import com.dynamicai.launcher.data.AppRepository
import com.dynamicai.launcher.data.WildRiftRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AgentEngine(private val context: Context, private val llm: LlmEngine) {
    private val appRepo = AppRepository(context)
    private val wildRiftRepo = WildRiftRepository(context)

    private val toolsDescription = """
        Доступні інструменти:
        1. openApp(name: String) -> Відкриває додаток.
        2. getWildRiftAdvice(myChampion: String, enemies: String) -> Поради Wild Rift.
        3. getWeather(city: String) -> Погода.
        4. setAlarm(hour: Int, minute: Int, message: String) -> Будильник.
        5. searchGoogle(query: String) -> Пошук Google.
        6. playYouTube(query: String) -> Пошук на YouTube.
        7. dialNumber(number: String) -> Набрати номер.
        8. openCamera() -> Відкрити камеру.
        9. openUrl(url: String) -> Відкрити сайт.
        10. navigateTo(destination: String) -> Маршрут.
        11. closeApp(name: String) -> Закрити додаток.
        12. playMusic(query: String) -> Відтворити музику в YT Music.
    """.trimIndent()

    suspend fun processWithTools(question: String): String = withContext(Dispatchers.IO) {
        val prompt = """
            Ти — агент-асистент. Користувач запитав: "$question"
            $toolsDescription
            
            Якщо потрібен інструмент, напиши ТІЛЬКИ команду:
            TOOL_CALL: назва_інструменту(параметр1="значення", параметр2="значення")
            
            Якщо інструмент не потрібен — коротка текстова відповідь.
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
                        val app = apps.find { it.label.contains(appName, ignoreCase = true) }
                        if (app != null) {
                            if (appRepo.launchApp(app.packageName)) "Відкриваю ${app.label}" else "Не вдалося відкрити ${app.label}"
                        } else "Додаток '$appName' не знайдено."
                    }
                    toolCall.startsWith("getWildRiftAdvice") -> {
                        val me = getArg("myChampion")
                        val enemies = getArg("enemies")
                        val enemyList = enemies.split(",").map { it.trim() }
                        val meChamp = wildRiftRepo.findChampion(me)
                        val enemyChamps = enemyList.mapNotNull { wildRiftRepo.findChampion(it) }
                        if (meChamp != null) wildRiftRepo.buildAdviceContext(meChamp, enemyChamps) else "Чемпіона '$me' не знайдено."
                    }
                    toolCall.startsWith("getWeather") -> {
                        val city = getArg("city").ifEmpty { "Київ" }
                        "У місті $city сонячно, +20°C."
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
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        context.startActivity(intent)
                        "Відкриваю набір номера: $number"
                    }
                    toolCall.startsWith("openCamera") -> {
                        val intent = Intent("android.media.action.STILL_IMAGE_CAMERA").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        context.startActivity(intent)
                        "Відкриваю камеру."
                    }
                    toolCall.startsWith("openUrl") -> {
                        val url = getArg("url")
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        context.startActivity(intent)
                        "Відкриваю посилання: $url"
                    }
                    toolCall.startsWith("navigateTo") -> {
                        val dest = getArg("destination")
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$dest")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        context.startActivity(intent)
                        "Будую маршрут до: $dest"
                    }
                    toolCall.startsWith("closeApp") -> {
                        val intent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        "Згортаю додаток на головний екран."
                    }
                    toolCall.startsWith("playMusic") -> {
                        val query = getArg("query")
                        val hasSearch = query.isNotEmpty()
                        if (hasSearch) {
                            val uri = Uri.parse("https://music.youtube.com/search?q=$query")
                            val intent = Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            context.startActivity(intent)
                            "Відкриваю пошук у YT Music: $query"
                        } else {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.youtube.music")
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(launchIntent)
                            }
                            "Відкриваю YT Music."
                        }
                    }
                    else -> "Невідомий інструмент: $toolCall"
                }
            } catch (e: Exception) {
                "Помилка виконання: ${e.message}"
            }
        } else {
            response
        }
    }
}
