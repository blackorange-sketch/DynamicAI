package com.dynamicai.launcher.llm

import android.content.Context
import com.dynamicai.launcher.data.WeatherRepository
import com.dynamicai.launcher.data.WildRiftRepository
import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import com.google.adk.kt.litertlm.LiteRtLmModel
import com.google.adk.kt.models.Model
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AssistantAgent(context: Context, private val llmModel: Model) {
    private val weatherRepo = WeatherRepository(context)
    private val wildRiftRepo = WildRiftRepository(context)

    // Наші інструменти
    inner class DeviceTools {
        @Tool(description = "Отримати поточну погоду в заданому місті.")
        suspend fun getWeather(@Param("Назва міста, наприклад, Київ") city: String): String {
            return withContext(Dispatchers.IO) {
                try {
                    val weather = weatherRepo.loadCachedWeather(city)
                    "Погода у місті $city: ${weather ?: "дані недоступні"}"
                } catch (e: Exception) {
                    "Не вдалося отримати погоду: ${e.message}"
                }
            }
        }

        @Tool(description = "Отримати поради щодо збірки предметів у Wild Rift для конкретного чемпіона проти списку ворогів.")
        suspend fun getWildRiftAdvice(
            @Param("Назва вашого чемпіона") myChampion: String,
            @Param("Список ворожих чемпіонів через кому") enemies: String
        ): String {
            return withContext(Dispatchers.IO) {
                try {
                    val enemyList = enemies.split(",").map { it.trim() }
                    val advice = wildRiftRepo.buildAdviceContext(
                        wildRiftRepo.findChampion(myChampion) ?: return@withContext "Чемпіона не знайдено",
                        enemyList.mapNotNull { wildRiftRepo.findChampion(it) }
                    )
                    advice
                } catch (e: Exception) {
                    "Помилка порад: ${e.message}"
                }
            }
        }
    }

    // Створюємо агента
    val rootAgent: LlmAgent = LlmAgent(
        name = "dynamic_ai_assistant",
        description = "Голосовий асистент лаунчера DynamicAI.",
        model = llmModel,
        instruction = Instruction(
            "Ти — корисний асистент у лаунчері Android. Відповідай українською мовою, коротко (1-3 речення). " +
            "Якщо користувач запитує про погоду або поради для Wild Rift, використовуй відповідні інструменти."
        ),
        tools = DeviceTools().generatedTools()
    )
}
