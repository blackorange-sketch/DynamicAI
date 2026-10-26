package com.dynamicai.launcher.llm

import android.content.Context
import android.content.Intent
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
    """.trimIndent()

    suspend fun processWithTools(question: String): String = withContext(Dispatchers.IO) {
        // 1. Формуємо промпт
        val prompt = """
            Ти — агент-асистент. Користувач запитав: "$question"
            $toolsDescription
            
            Якщо для відповіді потрібен інструмент, напиши ТІЛЬКИ команду у форматі:
            TOOL_CALL: назва_інструменту(параметр1="значення", параметр2="значення")
            
            Якщо інструмент не потрібен, просто дай текстову відповідь.
        """.trimIndent()

        val response = llm.complete(prompt)?.trim() ?: return@withContext "Помилка агента"

        // 2. Перевіряємо, чи модель вирішила викликати інструмент
        if (response.startsWith("TOOL_CALL:")) {
            val toolCall = response.substringAfter("TOOL_CALL:").trim()
            
            return@withContext when {
                toolCall.startsWith("openApp") -> {
                    val appName = Regex("""name="([^"]+)"""").find(toolCall)?.groupValues?.get(1) ?: ""
                    val apps = appRepo.getInstalledApps()
                    val app = apps.find { it.label.contains(appName, ignoreCase = true) }
                    if (app != null) {
                        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            "Відкриваю ${app.label}"
                        } else "Не вдалося відкрити ${app.label}"
                    } else "Додаток '$appName' не знайдено."
                }
                
                toolCall.startsWith("getWildRiftAdvice") -> {
                    val me = Regex("""myChampion="([^"]+)"""").find(toolCall)?.groupValues?.get(1) ?: ""
                    val enemies = Regex("""enemies="([^"]+)"""").find(toolCall)?.groupValues?.get(1) ?: ""
                    val enemyList = enemies.split(",").map { it.trim() }
                    val meChamp = wildRiftRepo.findChampion(me)
                    val enemyChamps = enemyList.mapNotNull { wildRiftRepo.findChampion(it) }
                    if (meChamp != null) {
                        wildRiftRepo.buildAdviceContext(meChamp, enemyChamps)
                    } else "Чемпіона '$me' не знайдено."
                }
                
                toolCall.startsWith("getWeather") -> {
                    val city = Regex("""city="([^"]+)"""").find(toolCall)?.groupValues?.get(1) ?: "Київ"
                    // Тут має бути реальний виклик вашого WeatherRepository
                    "У місті $city зараз сонячно, +20°C." 
                }
                
                else -> "Невідомий інструмент: $toolCall"
            }
        }
        
        // Якщо це звичайна текстова відповідь
        return@withContext response
    }
}
