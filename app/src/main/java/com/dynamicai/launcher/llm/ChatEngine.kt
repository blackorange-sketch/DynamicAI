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
    /** Чи схоже питання на запит факту (хто/коли/де/скільки/що таке…), для якого Вікіпедія корисна. */
    private fun looksFactual(question: String): Boolean {
        val t = question.lowercase().trim().trimEnd('?', '.', '!', ' ')
        if (t.split(Regex("\\s+")).size > 14) return false
        // Поточні дані (новини, курси, «зараз») у Вікіпедії не знайти
        if (Regex("зараз|сьогодні|вчора|завтра|новин|курс|ціна|погода").containsMatchIn(t)) return false
        return Regex("^(хто|коли|де|скільки|що\\s+таке|що\\s+за|який|яка|яке|які|чому|звідки|з\\s+чого|як\\s+називається|розкажи\\s+про|поясни)(?!\\p{L})").containsMatchIn(t)
    }

    private val SEARCH_STOP = setOf(
        "хто", "такий", "така", "таке", "такі", "що", "коли", "де", "скільки", "розкажи", "про", "поясни", "народився",
        "народилась", "народилася", "помер", "померла", "був", "була", "є", "це", "мені", "будь", "ласка", "який", "яка", "яке", "які"
    )

    /** Прибирає питальні слова: «коли помер Тарас Шевченко» → «Тарас Шевченко» (так пошук точніший). */
    private fun searchQueryFrom(question: String): String {
        val words = question.trim().trimEnd('?', '.', '!').split(Regex("\\s+"))
        val kept = words.filter { it.lowercase() !in SEARCH_STOP }
        return kept.joinToString(" ").ifBlank { question }
    }

    private val fastRouter by lazy { FastRouter { agentEngine.runTool(it) } }

    private val commandParser by lazy { com.dynamicai.launcher.command.CommandParser() }

    // ===== Підтвердження небезпечних дій («так» / «ні» наступною фразою) =====
    private var pendingAction: Pair<String, suspend () -> String>? = null

    init {
        agentEngine.confirmHook = { desc, action ->
            pendingAction = desc to action
            "Підтвердіть: $desc. Скажіть «так» або «ні»."
        }
    }

    private val YES = Regex("^(так|ага|підтверджую|давай|гаразд|добре|роби|виконуй|звісно|авжеж|ок|окей)(?!\\p{L}).*")
    private val NO = Regex("^(ні|не треба|скасуй|скасувати|відміна|стоп|не роби|не потрібно)(?!\\p{L}).*")
    private val REMEMBER = Regex("^запам.?ятай,?\\s+(?:що\\s+)?(.+?)\\s+(?:це|є|—|-|=)\\s+(.+)$")

    private fun findContact(contacts: List<com.dynamicai.launcher.suggestion.ContactInfo>, name: String): com.dynamicai.launcher.suggestion.ContactInfo? {
        val n = name.lowercase().trim()
        contacts.firstOrNull { it.name.lowercase().contains(n) }?.let { return it }
        val stems = n.split(Regex("\\s+")).filter { it.isNotBlank() }.map { AliasStore.stem(it) }
        if (stems.isEmpty()) return null
        return contacts.firstOrNull { c -> val cn = c.name.lowercase(); stems.all { cn.contains(it) } }
    }

    /** «Зателефонуй мамі»: псевдонім → контакт → набір номера (користувач сам натискає виклик). */
    private suspend fun callContact(rawName: String): String {
        val name = AliasStore(context).resolve(rawName)
        if (name.any { it.isDigit() } && name.count { it.isDigit() } >= 5) {
            return dial(name)
        }
        val contacts = try { com.dynamicai.launcher.suggestion.ContactRepository(context).loadAll() } catch (e: Exception) { emptyList() }
        val c = findContact(contacts, name)
        val number = c?.phones?.firstOrNull()
        if (c == null || number == null) return "Контакт «$rawName» не знайдено. Можна сказати: «запам'ятай, що $rawName — це Ім'я Прізвище»."
        return dial(number, c.name)
    }

    private fun dial(number: String, label: String? = null): String = try {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + android.net.Uri.encode(number)))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        "Набираю ${label ?: number}. Натисніть виклик."
    } catch (e: Exception) { "Не вдалося відкрити набір номера." }

    /**
     * Проста команда телефону без LLM (null — це не проста команда).
     * Спершу канонічний CommandParser/CommandExecutor проєкту (таймери, нагадування, дзвінки, медіа, перемикачі,
     * калькулятор…), потім FastRouter (погода, навігація, камера, валюта, YouTube, нотатки).
     */
    suspend fun tryFastRoute(question: String, apps: List<com.dynamicai.launcher.model.AppInfo> = emptyList()): String? {
        val t = question.lowercase().trim().trimEnd('.', '!', '?', ',', ' ')
        // Відповідь на запит підтвердження
        pendingAction?.let { (_, action) ->
            pendingAction = null
            if (YES.matches(t)) return try { action() } catch (e: Exception) { "Не вдалося виконати: ${e.message}" }
            if (NO.matches(t)) return "Скасовано."
        }
        // «Запам'ятай, що мама — це Олена Іванівна»
        REMEMBER.find(t)?.let { m ->
            AliasStore(context).put(m.groupValues[1], m.groupValues[2])
            return "Запам'ятав: ${m.groupValues[1]} — це ${m.groupValues[2]}."
        }
        commandRoute(question, apps)?.let { return it }
        return fastRouter.tryHandle(question)
    }

    private suspend fun commandRoute(question: String, apps: List<com.dynamicai.launcher.model.AppInfo>): String? {
        val C = com.dynamicai.launcher.command.Command
        val cmd = try { commandParser.parse(question) } catch (e: Exception) { return null }
        // Ці типи або не прості, або обробляються окремо (пошук, питання, переклад, нотатки) — лишаємо моделі
        fun blocked(c: com.dynamicai.launcher.command.Command) =
            c is C.Unknown || c is C.Raw || c is C.AskQuestion || c is C.WebSearch || c is C.OpenNotes ||
                c is C.TranslateScreen || c is C.TranslateText
        if (blocked(cmd)) return null
        // Дзвінок за іменем: парсер набирає ім'я як номер, тому шукаємо контакт самі
        if (cmd is C.Call) return callContact(cmd.name)
        if (cmd is C.Compound && cmd.steps.any { blocked(it) }) return null
        val executor = com.dynamicai.launcher.command.CommandExecutorProvider.get(context)
        val result = try { executor.execute(cmd, apps) } catch (e: Exception) {
            Log.e(TAG, "command execute failed", e); return null
        }
        Log.e(TAG, "⚡ command ${cmd::class.simpleName} → $result")
        return when (result) {
            is com.dynamicai.launcher.command.CommandResult.Success -> result.message
            is com.dynamicai.launcher.command.CommandResult.Error ->
                if (result.message.startsWith("__") || result.message.contains("не знайд", ignoreCase = true)) null else result.message
            is com.dynamicai.launcher.command.CommandResult.ChooseApp ->
                "Знайшов кілька додатків: " + result.candidates.take(4).joinToString(", ") { it.label } + ". Уточніть назву."
        }
    }

    suspend fun simpleAnswer(question: String, onToken: ((String) -> Unit)? = null): String {
        if (!llm.isReady) return "LLM не активна"
        return try {
            val isMnn = llm.isMnn
            val settings = com.dynamicai.launcher.data.SettingsStorage(context).load()
            // «Коротко»: додається до питання, а не до шаблону, тож власні промпти з налаштувань лишаються недоторканими
            // Фактичні питання («хто такий…», «коли народився…»): спершу дані з Вікіпедії — мала модель вигадує дати й імена
            val facts = if (settings.autoSearch && looksFactual(question)) {
                try {
                    val ctx = webSearch.toContext(webSearch.search(searchQueryFrom(question)))
                    Log.e(TAG, "🔎 auto-search for '$question': ${ctx.length} chars")
                    ctx
                } catch (e: Exception) { "" }
            } else ""
            val base = if (facts.isNotBlank())
                "Дані з Вікіпедії:\n${facts.take(1500)}\n\nПитання: $question\nВідповідай за цими даними, не вигадуй. Якщо даних недостатньо — так і скажи."
            else question
            val q = if (settings.answerStyle == "short") base + "\n(Відповідай коротко: 1–3 речення.)" else base

            // Беремо шаблон з файлу (не з SharedPreferences — надійніше)
            val template = if (isMnn) {
                com.dynamicai.launcher.llm.PromptManager.loadMnn(context)
            } else {
                com.dynamicai.launcher.llm.PromptManager.loadLitert(context)
            }
            Log.e("MNN_PROMPT", "Template from file (isMnn=$isMnn): ${template.replace("\n", "\\n")}")
            // LiteRT сам застосовує chat template (Conversation API). Старий шаблон за замовчуванням з
            // <start_of_turn> дав би подвійне обгортання — у цьому випадку шлемо чисте питання.
            val prompt = if (!isMnn && template.contains("<start_of_turn>")) q
                         else template.replace("{{question}}", q)
            Log.e("MNN_PROMPT", "Final prompt (${prompt.length} chars): ${prompt.replace("\n", "\\n")}")
            
            // LiteRT: стрім з ліміту довжини (зупиняє генерацію раніше); якщо порожньо — звичайний виклик
            val response = if (onToken != null) {
                // Потокова генерація: токени одразу йдуть у озвучення
                llm.streamComplete(prompt, onToken)?.takeIf { it.isNotBlank() } ?: llm.complete(prompt)
            } else if (!isMnn) {
                llm.streamComplete(prompt) { }?.takeIf { it.isNotBlank() } ?: llm.complete(prompt)
            } else llm.complete(prompt)
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
