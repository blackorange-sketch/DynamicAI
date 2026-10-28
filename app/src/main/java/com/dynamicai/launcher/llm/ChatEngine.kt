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
    private val koleoClient = KoleoClient()
    private val translationRepo = TranslationRepository()
    private val wildRiftRepo = com.dynamicai.launcher.data.WildRiftRepository(context)
    private val agentEngine = AgentEngine(context, llm)
    private val smalltalkPrompt: String by lazy {
        try {
            context.assets.open("llm/smalltalk_prompt.txt").bufferedReader().use { it.readText() }
        } catch (_: Exception) { "" }
    }

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
    private suspend fun structuredAnswer(question: String, isEvent: Boolean): String {
        if (!isEvent) {
            // Для потягів пробуємо Koleo API
            val fromTo = extractFromTo(question)
            if (fromTo != null) {
                val connections = koleoClient.findConnections(fromTo.first, fromTo.second)
                if (connections.isNotEmpty()) {
                    val sb = StringBuilder()
                    sb.append("🚆 ${fromTo.first} → ${fromTo.second}\n")
                    connections.forEach { c ->
                        sb.append("🕐 ${c.departure} → ${c.arrival}")
                        if (c.duration.isNotBlank()) sb.append(" (${c.duration})")
                        if (c.price != null) sb.append(" · ${c.price}")
                        sb.append("\n")
                    }
                    return sb.toString().trim()
                }
            }
        }

        // Якщо Koleo не допоміг (або це подія) — шукаємо в інтернеті
        val searchResult = webSearch.search(question)
        val context = webSearch.toContext(searchResult)
        
        val format = if (isEvent) {
            "🎤 Назва події\n📍 Місце\n📅 Дата та час\n🎫 Ціна (якщо є)"
        } else {
            "🚆 Маршрут\n🕐 Час\n⏱ Тривалість\n💰 Ціна (якщо є)"
        }
        
        val prompt = """
            Ти — AI-асистент. Користувач шукає ${if (isEvent) "концерт/подію" else "транспорт"}.
            Питання: $question
            
            Результати пошуку в інтернеті:
            ${if (context.isNotBlank()) context else "Немає даних."}
            
            Сформуй відповідь СУВОРО у такому форматі (до 5 рядків):
            $format
            
            Якщо в результатах немає конкретних дат/цін, напиши "Не знайдено точних даних" замість вигадування.
        """.trimIndent()

        val response = llm.complete(prompt)
        return response?.trim() ?: "Не вдалося отримати відповідь."
    }

    private fun extractFromTo(question: String): Pair<String, String>? {
        val q = question.lowercase()
        // "з Варшави до Кракова"
        val m1 = Regex("з\\s+([а-яіїєґa-z\\s-]+?)\\s+до\\s+([а-яіїєґa-z\\s-]+)").find(q)
        if (m1 != null) {
            return m1.groupValues[1].trim() to m1.groupValues[2].trim()
        }
        // "від Варшави до Кракова"
        val m2 = Regex("від\\s+([а-яіїєґa-z\\s-]+?)\\s+до\\s+([а-яіїєґa-z\\s-]+)").find(q)
        if (m2 != null) {
            return m2.groupValues[1].trim() to m2.groupValues[2].trim()
        }
        return null
    }

    /**
     * Стрімінг-версія answer().
     */
    suspend fun answerStreaming(question: String, onToken: (String) -> Unit): String {
        if (!llm.isReady) return "LLM не активна"
        try {
            val smartContext = getSmartContext(question)
            val context = if (smartContext.isNotBlank()) {
                smartContext
            } else {
                val searchResult = webSearch.search(question)
                webSearch.toContext(searchResult)
            }

            val prompt = buildString {
                append(smalltalkPrompt)
                append("\n\n[USER QUESTION]\n")
                append("Відповідай українською мовою, коротко (максимум 2-3 речення).\n\n")
                if (context.isNotBlank()) {
                    append("Ось інформація з інтернету, яка може допомогти:\n")
                    append(context)
                    append("\n\n")
                }
                append("Питання користувача: ").append(question).append("\n\n")
                append("Дай точну і коротку відповідь українською.")
            }

            return llm.streamComplete(prompt, onToken) ?: "Не вдалося отримати відповідь."
        } catch (t: Throwable) {
            Log.e(TAG, "answerStreaming failed", t)
            return "Помилка: ${t.message}"
        }
    }


    suspend fun getWildRiftAdvice(myChampion: String, enemies: List<String>): String {
        if (!llm.isReady) return "LLM не активна"
        try {
            val repo = com.dynamicai.launcher.data.WildRiftRepository(context)
            val me = repo.findChampion(myChampion) ?: return "Чемпіон не знайдений: $myChampion"
            val enemyChamps = enemies.mapNotNull { repo.findChampion(it) }
            if (enemyChamps.isEmpty()) return "Не вказано ворожих чемпіонів"
            
            val ragContext = repo.buildAdviceContext(me, enemyChamps)
            
            val prompt = buildString {
                append("Ти — експерт з Wild Rift. Дай поради щодо збірки предметів.\n\n")
                append(ragContext)
                append("\n\n")
                append("Формат відповіді (українською, максимум 6 рядків):\n")
                append("1. Ключові предмети (3-4, через кому)\n")
                append("2. Проти кожного ворога — 1 предмет + коротка причина\n")
                append("3. Без пояснень, без вступів, тільки факти.\n")
            }
            
            return llm.complete(prompt)?.trim() ?: "Не вдалося отримати поради"
        } catch (t: Throwable) {
            return "Помилка: ${t.message}"
        }
    }

    private suspend fun getSmartContext(question: String): String {
        val q = question.lowercase()

        // Wild Rift — поради щодо предметів проти чемпіонів
        if (q.contains("wild rift") || q.contains("item") || q.contains("build") ||
            q.contains("counter") || q.contains("проти") || q.contains("збірк")) {
            val wildRiftContext = wildRiftRepo.buildContextForQuery(question)
            if (wildRiftContext.isNotBlank()) {
                return "Ти — експерт Wild Rift. Ось дані з нашої бази:\n$wildRiftContext\n\n" +
                        "Дай відповідь українською у форматі:\n" +
                        "1. Рекомендовані предмети (3-4 назви через кому)\n" +
                        "2. Проти кожного ворога — 1 предмет + коротка причина\n" +
                        "3. Не вигадуй предмети, яких немає в списку. Максимум 6 рядків."
            }
        }
        
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
            val cityPatterns = listOf(
                Regex("[\u201c\"]([^\"\u201d]+)[\"\u201d]"),
                Regex("(?:\u0432|\u0443|\u0434\u043b\u044f|in)\\s+([\u0410-\u042f\u0406\u0407\u0404\u0490A-Z][\u0430-\u044f\u0456\u0457\u0454\u0491a-zA-Z\\-]+(?:\\s+[\u0410-\u042f\u0406\u0407\u0404\u0490A-Z][\u0430-\u044f\u0456\u0457\u0454\u0491a-zA-Z\\-]+)?)"),
                Regex("\u043f\u043e\u0433\u043e\u0434\u0430\\s+(?:\u0432|\u0443)?\\s*([\u0410-\u042f\u0406\u0407\u0404\u0490A-Z][\u0430-\u044f\u0456\u0457\u0454\u0491a-zA-Z\\-]+(?:\\s+[\u0410-\u042f\u0406\u0407\u0404\u0490A-Z][\u0430-\u044f\u0456\u0457\u0454\u0491a-zA-Z\\-]+)?)", RegexOption.IGNORE_CASE)
            )

            var city: String? = null
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

            val repo = com.dynamicai.launcher.data.WeatherRepository(context)
            val state = repo.fetch(cityOverride = city)

            when (state) {
                is com.dynamicai.launcher.data.WeatherState.Data -> {
                    val cityLabel = city?.takeIf { it.isNotBlank() }
                        ?: com.dynamicai.launcher.data.SettingsStorage(context).load().cityName
                            .takeIf { it.isNotBlank() } ?: "\u0432\u0430\u0448\u043e\u043c\u0443 \u043c\u0456\u0441\u0442\u0456"
                    "\u041f\u043e\u0442\u043e\u0447\u043d\u0430 \u043f\u043e\u0433\u043e\u0434\u0430 \u0432 $cityLabel: ${state.tempC}\u00b0${state.unit}, ${state.description}. " +
                    "\u0412\u0456\u0434\u0447\u0443\u0432\u0430\u0454\u0442\u044c\u0441\u044f \u044f\u043a ${state.feelsLike}\u00b0. \u0412\u043e\u043b\u043e\u0433\u0456\u0441\u0442\u044c ${state.humidity}%, \u0432\u0456\u0442\u0435\u0440 ${state.windSpeed} \u043a\u043c/\u0433\u043e\u0434. " +
                    "\u041c\u0456\u043d\u0456\u043c\u0443\u043c ${state.tempMin}\u00b0, \u043c\u0430\u043a\u0441\u0438\u043c\u0443\u043c ${state.tempMax}\u00b0."
                }
                is com.dynamicai.launcher.data.WeatherState.Error -> {
                    Log.d(TAG, "Weather error: ${state.message}")
                    state.message
                }
                else -> ""
            }
        } catch (t: Throwable) {
            Log.e(TAG, "getWeatherContext failed", t)
            ""
        }
    }


    /** Простий чат без агента та інструментів — для маленьких моделей (MNN Qwen 1.5B/0.6B). */
    suspend fun simpleAnswer(question: String): String {
        if (!llm.isReady) return "LLM не активна"
        return try {
            val settings = com.dynamicai.launcher.data.SettingsStorage(context).load()
            val isMnn = llm is com.dynamicai.launcher.llm.MnnLlmEngine
            
            // Вибираємо шаблон залежно від рушія
            val template = if (isMnn) settings.mnnPrompt else settings.litertPrompt
            val prompt = template.replace("{{question}}", question)
            
            val response = llm.complete(prompt)
            Log.d(TAG, "simpleAnswer: prompt len=${prompt.length}, response=${response?.take(100)}")
            response?.trim() ?: "LLM не відповіла"
        } catch (t: Throwable) {
            Log.e(TAG, "simpleAnswer failed", t)
            "Помилка: ${t.message}"
        }
    }

    suspend fun answerWithAgent(question: String, history: String = ""): String {
        if (!llm.isReady) return "LLM не активна"
        return try {
            // Використовуємо наш власний AgentEngine, який вже написаний і працює
            agentEngine.processWithTools(question)
        } catch (t: Throwable) {
            android.util.Log.e("ChatEngine", "Agent error", t)
            "Помилка агента: ${t.message}"
        }
    }
}
