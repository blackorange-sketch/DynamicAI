package com.dynamicai.launcher.llm

import android.util.Log

/**
 * Chat-режим: LLM + Web Search (RAG).
 */
class ChatEngine(
    private val llm: LlmEngine,
    private val webSearch: WebSearchRepository
) {

    companion object {
        private const val TAG = "ChatEngine"
    }

    /**
     * Обробити питання користувача.
     * 1. Шукаємо в інтернеті.
     * 2. Формуємо промпт з контекстом.
     * 3. LLM дає відповідь.
     */
    suspend fun answer(question: String): String {
        if (!llm.isReady) return "LLM не активна"

        try {
            // 1. Розумний роутинг за типом питання
            val smartContext = getSmartContext(question)

            // 2. Якщо є контекст — використовуємо його, інакше DuckDuckGo
            val context = if (smartContext.isNotBlank()) {
                smartContext
            } else {
                val searchResult = webSearch.search(question)
                webSearch.toContext(searchResult)
            }

            // 2. Формуємо промпт
            val prompt = buildString {
                append("Ти — корисний AI-асистент у лаунчері Android. Відповідай українською мовою, коротко (1-3 речення).\n\n")
                if (context.isNotBlank()) {
                    append("Ось інформація з інтернету, яка може допомогти:\n")
                    append(context)
                    append("\n\n")
                }
                append("Питання користувача: ").append(question).append("\n\n")
                append("Дай точну і коротку відповідь українською.")
            }

            // 3. Отримуємо відповідь
            val response = llm.complete(prompt)
            Log.d(TAG, "Answer: ${response?.take(100)}")

            if (response == null) {
                return "LLM не відповіла. WebSearch: ${webSearch.lastError}"
            }
            return response.trim()
        } catch (t: Throwable) {
            Log.e(TAG, "answer failed", t)
            return "Помилка: ${t.message}"
        }
    }

    /**
     * Розумний роутинг за типом питання.
     * Повертає готовий контекст або порожній рядок.
     */
    private suspend fun getSmartContext(question: String): String {
        val q = question.lowercase()
        
        // Погода → Open-Meteo
        if (q.contains("погод") || q.contains("weather") ||
            q.contains("температур") || q.contains("дощ") || q.contains("сонце")) {
            return getWeatherContext(question)
        }
        
        // Час → локально
        if (q.contains("який час") || q.contains("котра година") || q.contains("current time")) {
            val now = java.time.LocalDateTime.now()
            val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm, d MMMM yyyy", java.util.Locale("uk", "UA"))
            return "Поточний час: ${now.format(fmt)}"
        }
        
        // Курс валют → поки без API
        if (q.contains("курс") || q.contains("долар") || q.contains("євро") || q.contains("exchange rate")) {
            // Можна додати exchangerate API
            return ""
        }
        
        return ""
    }
    
    private suspend fun getWeatherContext(question: String): String {
        return try {
            // Витягуємо місто з питання (груба евристика)
            val cityMatch = Regex("(?:в|у|для)\\s+([А-ЯІЇЄҐA-Z][а-яіїєґa-z]+)").find(question)
            val city = cityMatch?.groupValues?.get(1) ?: "Київ"
            
            val repo = com.dynamicai.launcher.data.WeatherRepository(
                getApplicationContext()
            )
            // Використовуємо cityName через налаштування — але краще прямо
            val state = repo.fetch()
            
            when (state) {
                is com.dynamicai.launcher.data.WeatherState.Data -> {
                    "Поточна погода в $city: ${state.tempC}°${state.unit}, ${state.description}. " +
                    "Відчувається як ${state.feelsLike}°. Вологість ${state.humidity}%, вітер ${state.windSpeed} км/год. " +
                    "Мінімум ${state.tempMin}°, максимум ${state.tempMax}°."
                }
                else -> ""
            }
        } catch (t: Throwable) {
            Log.e(TAG, "getWeatherContext failed", t)
            ""
        }
    }

}
