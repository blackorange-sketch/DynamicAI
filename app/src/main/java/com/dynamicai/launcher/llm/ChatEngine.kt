package com.dynamicai.launcher.llm

import android.util.Log

/**
 * Chat-режим: LLM + Web Search (RAG).
 */
class ChatEngine(
    private val context: android.content.Context,
    private val llm: LlmEngine,
    private val webSearch: WebSearchRepository
) {
    private val newsRepo = NewsRepository()
    private val translationRepo = TranslationRepository()

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
        
        // Новини → RSS
        if (q.contains("новини") || q.contains("news") ||
            q.contains("що нового") || q.contains("останні новини")) {
            return getNewsContext(question)
        }
        
        // Переклад → MyMemory API
        if (q.contains("переклад") || q.contains("переклади") ||
            q.contains("translate") || q.contains("як буде")) {
            return getTranslationContext(question)
        }
        
        return ""
    }
    
    private suspend fun getNewsContext(question: String): String {
        return try {
            val category = if (question.contains("технолог", ignoreCase = true) ||
                              question.contains("tech", ignoreCase = true)) "tech" else ""
            val items = newsRepo.getNews(category, limit = 5)
            if (items.isEmpty()) {
                return ""
            }
            "Останні новини:\n" + newsRepo.toContext(items)
        } catch (t: Throwable) {
            Log.e(TAG, "getNewsContext failed", t)
            ""
        }
    }
    
    private suspend fun getTranslationContext(question: String): String {
        return try {
            // Витягуємо текст для перекладу (після двокрапки або лапок)
            val textMatch = Regex("[\"\u201c]([^\"\u201d]+)[\"\u201d]").find(question)
                ?: Regex("(?:переклад[а-я]*|translate)\\s*[:\\s]+(.+)", RegexOption.IGNORE_CASE).find(question)
            
            val textToTranslate = textMatch?.groupValues?.get(1)?.trim() ?: return ""
            if (textToTranslate.isBlank()) return ""
            
            val targetLang = translationRepo.detectTargetFromQuestion(question)
            val result = translationRepo.translate(textToTranslate, "auto", targetLang)
            
            if (result.success) {
                "Переклад: ${result.text}"
            } else {
                ""
            }
        } catch (t: Throwable) {
            Log.e(TAG, "getTranslationContext failed", t)
            ""
        }
    }
    
    private suspend fun getWeatherContext(question: String): String {
        return try {
            // Покращений regex — 3 варіанти
            val cityPatterns = listOf(
                Regex("[\"\u201c]([^\"\u201d]+)[\"\u201d]"),
                Regex("(?:в|у|для|in)\\s+([А-ЯІЇЄҐA-Z][а-яіїєґa-zA-Z\\-\\s]+)"),
                Regex("погода\\s+(?:в|у)?\\s*(.+)", RegexOption.IGNORE_CASE)
            )

            var city = "Київ"
            for (pattern in cityPatterns) {
                val match = pattern.find(question)
                if (match != null) {
                    val extracted = match.groupValues[1].trim()
                        .replace(Regex("[?.!,]"), "")
                        .trim()
                    if (extracted.isNotBlank()) {
                        city = extracted
                        break
                    }
                }
            }

            Log.d(TAG, "Weather: city=$city")

            // Тимчасово підмінюємо місто у налаштуваннях
            val settingsStorage = com.dynamicai.launcher.data.SettingsStorage(context)
            val settings = settingsStorage.load()
            val originalCity = settings.cityName

            settingsStorage.save(settings.copy(cityName = city))

            val repo = com.dynamicai.launcher.data.WeatherRepository(context)
            val state = try {
                repo.fetch()
            } finally {
                settingsStorage.save(settings.copy(cityName = originalCity))
            }

            when (state) {
                is com.dynamicai.launcher.data.WeatherState.Data -> {
                    "Поточна погода в $city: ${state.tempC}°${state.unit}, ${state.description}. " +
                    "Відчувається як ${state.feelsLike}°. Вологість ${state.humidity}%, вітер ${state.windSpeed} км/год. " +
                    "Мінімум ${state.tempMin}°, максимум ${state.tempMax}°."
                }
                is com.dynamicai.launcher.data.WeatherState.Error -> {
                    Log.d(TAG, "Weather error: ${state.message}")
                    ""
                }
                else -> ""
            }
        } catch (t: Throwable) {
            Log.e(TAG, "getWeatherContext failed", t)
            ""
        }
    }

}
