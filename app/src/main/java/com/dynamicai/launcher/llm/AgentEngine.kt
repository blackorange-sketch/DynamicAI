package com.dynamicai.launcher.llm

import android.content.Context
import com.dynamicai.launcher.data.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AgentEngine(private val context: Context, private val llm: LlmEngine) {
    private val weatherRepo = WeatherRepository(context)

    // Описуємо інструмент для моделі
    private val toolsDescription = """
        Доступні інструменти:
        1. getWeather(city: String) -> String. Повертає поточну погоду в місті.
    """.trimIndent()

    suspend fun processWithTools(question: String): String = withContext(Dispatchers.IO) {
        // 1. Формуємо промпт, щоб модель вирішила, який інструмент викликати
        val prompt = """
            Ти — агент. Користувач запитав: "$question"
            $toolsDescription
            
            Якщо для відповіді потрібен інструмент, напиши у форматі:
            TOOL_CALL: getWeather(city="Назва міста")
            
            Якщо інструмент не потрібен, просто дай відповідь.
        """.trimIndent()

        val response = llm.complete(prompt) ?: return@withContext "Помилка агента"

        // 2. Перевіряємо, чи модель вирішила викликати інструмент
        if (response.contains("TOOL_CALL: getWeather")) {
            val city = Regex("""city="([^"]+)"""").find(response)?.groupValues?.get(1) ?: "Київ"
            val weather = weatherRepo.loadCachedWeather(city) // Ваш метод
            val finalAnswer = llm.complete("Ось дані про погоду: $weather. Дай коротку відповідь користувачу.")
            return@withContext finalAnswer ?: "Не вдалося отримати погоду."
        }
        
        return@withContext response
    }
}
