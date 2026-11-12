package com.dynamicai.launcher.llm

import android.util.Log
import com.dynamicai.launcher.command.Command as C
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.dynamicai.launcher.accessibility.AppAutomationService
import com.dynamicai.launcher.command.Command
import com.dynamicai.launcher.command.CommandExecutorProvider
import com.dynamicai.launcher.command.CommandParser
import com.dynamicai.launcher.command.CommandResult
import com.dynamicai.launcher.data.LauncherSettings
import com.dynamicai.launcher.data.SettingsStorage
import com.dynamicai.launcher.data.WeatherRepository
import com.dynamicai.launcher.data.WeatherState
import com.dynamicai.launcher.data.WildRiftRepository
import com.dynamicai.launcher.macro.MacroStorage
import com.dynamicai.launcher.model.AppInfo
import com.dynamicai.launcher.overlay.ChatHistoryManager
import com.dynamicai.launcher.overlay.ChatMessage
import com.dynamicai.launcher.suggestion.ContactInfo
import com.dynamicai.launcher.suggestion.ContactRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Chat-режим: LLM + Web Search (RAG).
 */
class ChatEngine(
    private val context: Context,
    private val llm: LlmEngine,
    private val webSearch: WebSearchRepository
) {
    private val newsRepo = NewsRepository()
    private val koleoClient = KoleoClient()
    private val translationRepo = TranslationRepository()
    private val wildRiftRepo = WildRiftRepository(context)
    private val agentEngine = AgentEngine(context, llm)
    private val smalltalkPrompt: String by lazy {
        try {
            context.assets.open("llm/smalltalk_prompt.txt").bufferedReader().use { it.readText() }
        } catch (_: Exception) { "" }
    }

    companion object {
        private const val TAG = "ChatEngine"
        /** simpleAnswer повертає це, коли хмара недоступна: викликач має завантажити локальну модель і повторити. */
        const val CLOUD_FAILED = "\u0000cloud_failed"
        /** Маркер у тексті відповіді: після нього йдуть рядки «назва<TAB>url» — джерела. */
        const val SOURCES_TAG = "\u27e6\u0434\u0436\u0435\u0440\u0435\u043b\u0430\u27e7"
    }

    @Volatile private var lastSources: List<WebSearchRepository.WebHit> = emptyList()
    @Volatile private var cloudCooldownUntil = 0L

    /** Блок джерел для останньої відповіді (і очищує його). Порожній рядок, якщо джерел нема. */
    fun takeSources(): String {
        val src = lastSources
        lastSources = emptyList()
        if (src.isEmpty()) return ""
        return "\n\n" + SOURCES_TAG + "\n" + src.joinToString("\n") {
            it.title.replace(Regex("[\\t\\n\\r]+"), " ").take(90) + "\t" + it.url
        }
    }

    private val SCREEN_ASK = Regex("(що|про що)\\s+(там\\s+)?(на|в|у)\\s+(цьому\\s+)?(екрані|вікні)|прочитай\\s+(екран|це)|підсумуй\\s+(екран|це|сторінку)|переказуй\\s+екран")

    /** Усі питання (крім екрана й макросів) йдуть у хмару — локальну модель не потрібно завантажувати наперед. */
    fun cloudOnly(): Boolean {
        val s = SettingsStorage(context).load()
        return s.cloudEnabled && CloudLlm.hasKey(s) && s.cloudMode == "always"
    }

    // ===== Підсумок старої частини розмови (для хмари) =====
    private var summaryText = ""
    private var summaryCount = 0
    private var summaryFp = 0
    private var summaryRetryAt = 0L

    /**
     * Репліки, що вийшли за вікно останніх 16, стискаються моделлю в короткий підсумок. Оновлюється пачками по 4 репліки,
     * щоб не робити зайвих запитів; при зміні розмови скидається.
     */
    private suspend fun olderSummary(s: LauncherSettings, older: List<ChatMessage>): String {
        if (older.isEmpty()) return ""
        val fp = older.first().content.hashCode()
        if (fp != summaryFp || older.size < summaryCount) { summaryText = ""; summaryCount = 0; summaryFp = fp }
        val fresh = older.drop(summaryCount)
        val due = fresh.size >= 4 || summaryText.isBlank()
        if (!due || System.currentTimeMillis() < summaryRetryAt) return summaryText
        val input = buildString {
            if (summaryText.isNotBlank()) append("Попередній підсумок:\n").append(summaryText).append("\n\n")
            append("Нові репліки:\n")
            fresh.forEach {
                val who = if (it.role == "user") "Користувач" else "Асистент"
                append(who).append(": ").append(it.content.substringBefore(SOURCES_TAG).trim().take(500)).append('\n')
            }
        }.takeLast(5000)
        val r = try {
            withTimeoutOrNull(9_000) {
                CloudLlm.generate(
                    s,
                    "Ти стискаєш розмову. Зроби стислий підсумок (до 8 коротких пунктів, до 900 символів): теми, " +
                        "названі користувачем факти про нього й домовленості, що можуть знадобитися далі. " +
                        "Якщо є попередній підсумок — онови його новими репліками. Без вступів.",
                    input
                ) { }.trim()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
        if (r.isNullOrBlank()) {
            summaryRetryAt = System.currentTimeMillis() + 120_000
        } else {
            summaryText = r.take(1200); summaryCount = older.size
        }
        return summaryText
    }

    /** Чи відповідатиме на це питання хмарна модель (тоді локальну завантажувати не потрібно). */
    fun cloudPreferred(question: String): Boolean {
        val s = SettingsStorage(context).load()
        if (!s.cloudEnabled || !CloudLlm.hasKey(s)) return false
        if (System.currentTimeMillis() < cloudCooldownUntil) return false
        val t = question.lowercase()
        if (SCREEN_ASK.containsMatchIn(t)) return false           // текст екрана не залишає пристрій
        if (MacroBuilder.looksLikeRequest(question)) return false  // макроси збирає локальна модель
        return when (s.cloudMode) {
            "always" -> true
            else -> looksFactual(t.removePrefix("а ").removePrefix("і ").removePrefix("ну ")) || t.length > 70
        }
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
            val repo = WildRiftRepository(context)
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
            val now = LocalDateTime.now()
            val fmt = DateTimeFormatter.ofPattern("HH:mm, d MMMM yyyy", Locale("uk", "UA"))
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

            val repo = WeatherRepository(context)
            val state = repo.fetch(cityOverride = city)

            when (state) {
                is WeatherState.Data -> {
                    val cityLabel = city?.takeIf { it.isNotBlank() }
                        ?: SettingsStorage(context).load().cityName
                            .takeIf { it.isNotBlank() } ?: "\u0432\u0430\u0448\u043e\u043c\u0443 \u043c\u0456\u0441\u0442\u0456"
                    "\u041f\u043e\u0442\u043e\u0447\u043d\u0430 \u043f\u043e\u0433\u043e\u0434\u0430 \u0432 $cityLabel: ${state.tempC}\u00b0${state.unit}, ${state.description}. " +
                    "\u0412\u0456\u0434\u0447\u0443\u0432\u0430\u0454\u0442\u044c\u0441\u044f \u044f\u043a ${state.feelsLike}\u00b0. \u0412\u043e\u043b\u043e\u0433\u0456\u0441\u0442\u044c ${state.humidity}%, \u0432\u0456\u0442\u0435\u0440 ${state.windSpeed} \u043a\u043c/\u0433\u043e\u0434. " +
                    "\u041c\u0456\u043d\u0456\u043c\u0443\u043c ${state.tempMin}\u00b0, \u043c\u0430\u043a\u0441\u0438\u043c\u0443\u043c ${state.tempMax}\u00b0."
                }
                is WeatherState.Error -> {
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
    private val NO_SEARCH = Regex("^(привіт|вітаю|добрий\\s+(день|ранок|вечір)|дякую|дякуй|добре|гаразд|ок\\b|як\\s+справи|як\\s+ти\\b|хто\\s+ти\\b|як\\s+тебе|напиши|склади|придумай|переклади|порахуй|скільки\\s+буде|розкажи\\s+(жарт|анекдот)|жарт|анекдот|виправ|перефразуй|поясни\\s+(цей|мені\\s+цей)\\s+код)")
    private val EXPLICIT_SEARCH = Regex("в\\s+інтернеті|в\\s+інеті|у\\s+мережі|в\\s+гугл|погугли|пошукай|знайди\\s+(інформацію|в)|загугли")
    private val CURRENT_DATA = Regex("зараз|сьогодні|вчора|завтра|востаннє|останн|новин|курс|ціна|ціни|скільки\\s+коштує|вийшов|вийде|рейтинг|найкращ|топ\\b|20(2[4-9]|3\\d)")

    /** Чи варто шукати в інтернеті: майже кожне питання про світ, окрім балачок, перекладів і творчих запитів. */
    private fun looksFactual(question: String): Boolean {
        val t = question.lowercase().trim().trimEnd('?', '.', '!', ' ')
        if (EXPLICIT_SEARCH.containsMatchIn(t)) return true
        if (NO_SEARCH.containsMatchIn(t)) return false
        val words = t.split(Regex("\\s+")).size
        if (words > 20 || words < 2) return false
        if (CURRENT_DATA.containsMatchIn(t)) return true
        val interrogative = Regex("^(хто|коли|де|скільки|що|який|яка|яке|які|чому|навіщо|звідки|куди|як|чи|розкажи|поясни|порадь|підкажи|поясни|що\\s+таке)(?!\\p{L})").containsMatchIn(t)
        return interrogative || question.trimEnd().endsWith("?")
    }

    /** Збирає контекст: Вікіпедія (сутності) + вебпошук (свіжі дані й усе, чого немає у Вікіпедії). */
    private suspend fun gatherFacts(question: String): String {
        val query = searchQueryFrom(question)
        val wiki = try { webSearch.toContext(webSearch.search(query)) } catch (e: Exception) {
            if (e is CancellationException) throw e; ""
        }
        val needWeb = EXPLICIT_SEARCH.containsMatchIn(question.lowercase()) ||
            CURRENT_DATA.containsMatchIn(question.lowercase()) || wiki.length < 200
        val web = if (needWeb) webSearch.searchWeb(query) else ""
        return listOf(wiki.take(1100), web.take(1100)).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    private val SEARCH_STOP = setOf(
        "хто", "такий", "така", "таке", "такі", "що", "коли", "де", "скільки", "розкажи", "про", "поясни", "народився", "і", "а", "чи", "мені", "пошукай", "знайди", "інтернеті", "інеті", "мережі", "гугл", "загугли", "погугли", "наступний", "наступна", "найближчий", "найближча",
        "народилась", "народилася", "помер", "померла", "був", "була", "є", "це", "мені", "будь", "ласка", "який", "яка", "яке", "які"
    )

    /** Прибирає питальні слова: «коли помер Тарас Шевченко» → «Тарас Шевченко» (так пошук точніший). */
    private fun searchQueryFrom(question: String): String {
        val words = question.trim().trimEnd('?', '.', '!').split(Regex("\\s+"))
        val kept = words.filter { it.lowercase() !in SEARCH_STOP }
        return kept.joinToString(" ").ifBlank { question }
    }

    private val fastRouter by lazy { FastRouter { agentEngine.runTool(it) } }

    private val commandParser by lazy { CommandParser() }

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

    private fun findContact(contacts: List<ContactInfo>, name: String): ContactInfo? {
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
        val contacts = try { ContactRepository(context).loadAll() } catch (e: Exception) { emptyList() }
        val c = findContact(contacts, name)
        val number = c?.phones?.firstOrNull()
        if (c == null || number == null) return "Контакт «$rawName» не знайдено. Можна сказати: «запам'ятай, що $rawName — це Ім'я Прізвище»."
        return dial(number, c.name)
    }

    private fun dial(number: String, label: String? = null): String = try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "Набираю ${label ?: number}. Натисніть виклик."
    } catch (e: Exception) { "Не вдалося відкрити набір номера." }

    /**
     * Проста команда телефону без LLM (null — це не проста команда).
     * Спершу канонічний CommandParser/CommandExecutor проєкту (таймери, нагадування, дзвінки, медіа, перемикачі,
     * калькулятор…), потім FastRouter (погода, навігація, камера, валюта, YouTube, нотатки).
     */
    suspend fun tryFastRoute(question: String, apps: List<AppInfo> = emptyList()): String? {
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
        // Прохання створити макрос віддаємо моделі (macroFlow), а не парсеру команд
        if (MacroBuilder.looksLikeRequest(question)) return null
        // Власні макроси: назва або тригер, сказані повністю («доброї ночі», «режим роботи»)
        try {
            MacroStorage(context).matchUtterance(t)?.let { m ->
                val macroName = m.name.replace('"', ' ')
                return agentEngine.runTool("runMacro(name=\"" + macroName + "\")")
            }
        } catch (e: Exception) { if (e is CancellationException) throw e }
        if (fastRouter.isToggleOrMedia(question)) fastRouter.tryHandle(question)?.let { return it }
        commandRoute(question, apps)?.let { return it }
        return fastRouter.tryHandle(question)
    }

    private suspend fun commandRoute(question: String, apps: List<AppInfo>): String? {
        val cmd = try { commandParser.parse(question) } catch (e: Exception) { return null }
        // Ці типи або не прості, або обробляються окремо (пошук, питання, переклад, нотатки) — лишаємо моделі
        fun blocked(c: Command) =
            c is C.Unknown || c is C.Raw || c is C.AskQuestion || c is C.WebSearch || c is C.OpenNotes ||
                c is C.TranslateScreen || c is C.TranslateText
        if (blocked(cmd)) return null
        // Дзвінок за іменем: парсер набирає ім'я як номер, тому шукаємо контакт самі
        if (cmd is C.Call) return callContact(cmd.name)
        if (cmd is C.Compound && cmd.steps.any { blocked(it) }) return null
        val executor = CommandExecutorProvider.get(context)
        val result = try { executor.execute(cmd, apps) } catch (e: Exception) {
            Log.e(TAG, "command execute failed", e); return null
        }
        Log.e(TAG, "⚡ command ${cmd::class.simpleName} → $result")
        return when (result) {
            is CommandResult.Success -> result.message
            is CommandResult.Error ->
                if (result.message.startsWith("__") || result.message.contains("не знайд", ignoreCase = true)) null else result.message
            is CommandResult.ChooseApp ->
                "Знайшов кілька додатків: " + result.candidates.take(4).joinToString(", ") { it.label } + ". Уточніть назву."
        }
    }

    /** «Зроби макрос…» / «коли …, вмикай …»: модель складає макрос, користувач підтверджує. */
    private suspend fun macroFlow(question: String): String? {
        if (!MacroBuilder.looksLikeRequest(question)) return null
        val draft = try { MacroBuilder.build(context, llm, question) } catch (e: Exception) {
            if (e is CancellationException) throw e; null
        } ?: return "Не вдалося скласти макрос із цього опису. Спробуйте простіше або створіть його вручну в розділі «Макроси»."
        pendingAction = "створити макрос" to {
            MacroBuilder.save(context, draft.macro)
            "Макрос «${draft.macro.name}» створено. Відредагувати його можна в розділі «Макроси»."
        }
        return "Створити макрос «${draft.macro.name}»? Скажіть «так» або «ні».\n\n${draft.preview}"
    }

    private val FOLLOW_UP = Regex("(^|\\s)(він|вона|воно|вони|його|її|їх|йому|їй|ним|нею|нього|неї|цього|цьому|цей|ця|це|ті|там|тут|тоді|ще|інші|інший|детальніше|докладніше|далі|продовжуй|продовж|повтори|а\\s+як|а\\s+що|а\\s+скільки|а\\s+коли|а\\s+де|а\\s+чому)(\\s|$|[?.!,])")

    /** Чи залежить питання від попередньої розмови (займенники, «а скільки…», дуже коротке). */
    private fun isFollowUp(question: String): Boolean {
        val t = question.lowercase().trim()
        return t.split(Regex("\\s+")).size <= 3 || FOLLOW_UP.containsMatchIn(t)
    }

    /** Стискає історію діалогу до кількох останніх реплік (мала модель + кирилиця = дорогий префіл). */
    private fun compactHistory(history: String): String {
        val lines = history.lines().filter { it.isNotBlank() }.takeLast(6).map { it.take(220) }
        var out = lines.joinToString("\n")
        if (out.length > 750) out = out.takeLast(750).substringAfter('\n', out.takeLast(750))
        return out
    }

    suspend fun simpleAnswer(
        question: String,
        onToken: ((String) -> Unit)? = null,
        history: String = "",
        onStatus: ((String) -> Unit)? = null,
        allowCloud: Boolean = true
    ): String {
        if (allowCloud && cloudPreferred(question)) {
            return cloudAnswer(question, onToken, history, onStatus) ?: CLOUD_FAILED
        }
        if (!llm.isReady) return "LLM не активна"
        macroFlow(question)?.let { return it }
        return try {
            val isMnn = llm.isMnn
            val settings = SettingsStorage(context).load()
            // «Коротко»: додається до питання, а не до шаблону, тож власні промпти з налаштувань лишаються недоторканими
            // Фактичні питання («хто такий…», «коли народився…»): спершу дані з Вікіпедії — мала модель вигадує дати й імена
            // Пам'ять діалогу: додаємо лише коли питання залежить від попередніх реплік
            val followUp = history.isNotBlank() && isFollowUp(question)
            val prevUser = history.lines().lastOrNull { it.startsWith("Користувач:") }?.removePrefix("Користувач:")?.trim().orEmpty()
            // «А скільки йому років?» → шукаємо за темою попереднього питання
            val searchText = if (followUp && prevUser.isNotBlank()) "$prevUser. $question" else question
            val facts = if (settings.autoSearch && looksFactual(question.removePrefix("а ").removePrefix("і ").removePrefix("ну "))) {
                try {
                    val ctx = gatherFacts(searchText)
                    Log.e(TAG, "🔎 auto-search for '$question': ${ctx.length} chars; ${webSearch.lastError}")
                    ctx
                } catch (e: CancellationException) { throw e } catch (e: Exception) { "" }
            } else ""
            // Явне «пошукай в інтернеті», а результатів немає — чесно кажемо, а не даємо моделі вигадувати
            if (settings.autoSearch && facts.isBlank() && EXPLICIT_SEARCH.containsMatchIn(question.lowercase()) && looksFactual(question)) {
                return "Не вдалося отримати результати пошуку: перевірте інтернет і спробуйте ще раз (${webSearch.lastError.take(80)})."
            }
            // «Що на екрані?»: передаємо моделі текст екрана (сервіс доступності)
            val screenAsk = Regex("(що|про що)\\s+(там\\s+)?(на|в|у)\\s+(цьому\\s+)?(екрані|вікні)|прочитай\\s+(екран|це)|підсумуй\\s+(екран|це|сторінку)|переказуй\\s+екран")
                .containsMatchIn(question.lowercase())
            val screenText = if (screenAsk && AppAutomationService.isEnabled())
                AppAutomationService.getScreenText().take(2500) else ""
            if (screenAsk && screenText.isBlank() && !AppAutomationService.isEnabled()) {
                return "Щоб бачити екран, увімкніть сервіс доступності (DynamicAI Automation) у налаштуваннях Android."
            }
            val baseCore = if (screenText.isNotBlank())
                "Текст на екрані користувача:\n$screenText\n\nПитання: $question\nВідповідай за цим текстом."
            else if (facts.isNotBlank())
                "Дані з інтернету:\n${facts.take(2300)}\n\nПитання: $question\nВідповідай за цими даними, не вигадуй. Якщо даних недостатньо — так і скажи."
            else question
            val base = if (followUp) "Попередня розмова (для контексту):\n${compactHistory(history)}\n\n" + baseCore else baseCore
            val langHint = when (settings.answerLanguage) {
                "en" -> "\n(Answer in English.)"
                "auto" -> "\n(Відповідай мовою питання.)"
                else -> "\n(Відповідай українською мовою.)"
            }
            val q = base + langHint + (if (settings.answerStyle == "short") "\n(Відповідай коротко: 1–3 речення.)" else "")

            // Беремо шаблон з файлу (не з SharedPreferences — надійніше)
            val template = if (isMnn) {
                PromptManager.loadMnn(context)
            } else {
                PromptManager.loadLitert(context)
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
            if (t is CancellationException) throw t
            Log.e(TAG, "simpleAnswer failed", t)
            "Помилка: ${t.message}"
        }
    }

    /**
     * Відповідь хмарної моделі з «розумним пошуком»: для фактичних питань шукаємо в мережі, читаємо кращі сторінки
     * й даємо моделі пронумеровані джерела. null — хмара не відповіла (потрібен запасний локальний шлях).
     */
    /** «Користувач: …/Асистент: …» → список реплік (багаторядкові відповіді склеюються), найновіші в межах ~7000 символів. */
    private fun parseHistory(history: String): List<Pair<String, String>> {
        val msgs = ArrayList<Pair<String, String>>()
        for (line in history.lines()) {
            when {
                line.startsWith("Користувач: ") -> msgs.add("user" to line.removePrefix("Користувач: "))
                line.startsWith("Асистент: ") -> msgs.add("assistant" to line.removePrefix("Асистент: "))
                msgs.isNotEmpty() -> msgs[msgs.size - 1] = msgs.last().first to (msgs.last().second + "\n" + line)
            }
        }
        val kept = ArrayList<Pair<String, String>>()
        var budget = 7000
        for ((role, text) in msgs.asReversed()) {
            val t = text.trim().take(1200)
            if (t.isBlank()) continue
            if (budget - t.length < 0) break
            budget -= t.length
            kept.add(role to t)
        }
        return kept.asReversed()
    }

    /** Робить із уточнення («а в Кракові?») самостійний пошуковий запит за допомогою моделі; null — не вдалося. */
    private suspend fun rewriteQuery(s: LauncherSettings, hist: List<Pair<String, String>>, question: String): String? {
        if (hist.isEmpty()) return null
        return try {
            withTimeoutOrNull(6_000) {
                CloudLlm.generate(
                    s,
                    "Перепиши останнє повідомлення користувача як самостійний пошуковий запит (до 12 слів), враховуючи попередню розмову. " +
                        "Лише текст запиту, без пояснень і лапок.",
                    question, hist.takeLast(6)
                ) { }.trim().trim('"', '«', '»').takeIf { it.length in 3..120 }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private suspend fun cloudAnswer(
        question: String, onToken: ((String) -> Unit)?, history: String, onStatus: ((String) -> Unit)?
    ): String? {
        val s = SettingsStorage(context).load()
        lastSources = emptyList()
        val followUp = history.isNotBlank() && isFollowUp(question)
        val prevUser = history.lines().lastOrNull { it.startsWith("Користувач:") }?.removePrefix("Користувач:")?.trim().orEmpty()
        val searchText = if (followUp && prevUser.isNotBlank()) "$prevUser. $question" else question
        val histMsgs = parseHistory(history)
        // Репліки поточної теми, що не влізли у вікно останніх 16 → підсумок
        val conv = ChatHistoryManager.currentTopic().dropLast(1)
        val older = conv.dropLast(minOf(16, conv.size))
        val summary = olderSummary(s, older)
        val bare = question.removePrefix("а ").removePrefix("і ").removePrefix("ну ")
        val wantSearch = s.autoSearch && (looksFactual(bare) || EXPLICIT_SEARCH.containsMatchIn(question.lowercase()) ||
            (followUp && looksFactual(prevUser)))

        var sourcesText = ""
        if (wantSearch) {
            try {
                onStatus?.invoke("Шукаю в інтернеті…")
                // Уточнення («а в Кракові?») → самостійний запит; якщо хмара не впоралась — простий склейка з попереднім питанням
                val query = (if (followUp) rewriteQuery(s, histMsgs, question) else null) ?: searchQueryFrom(searchText)
                Log.e(TAG, "☁️ search query: $query")
                val hits = webSearch.searchHits(query, 6)
                val wiki = try { webSearch.toContext(webSearch.search(query)) } catch (e: Exception) {
                    if (e is CancellationException) throw e; ""
                }
                val top = hits.take(3)
                val pages = if (top.isNotEmpty()) {
                    onStatus?.invoke("Читаю сторінки (${top.size})…")
                    webSearch.readPages(top.map { it.url }, query)
                } else emptyMap()
                val used = ArrayList<WebSearchRepository.WebHit>()
                val sb = StringBuilder()
                hits.forEachIndexed { i, h ->
                    val body = pages[h.url] ?: h.snippet
                    if (body.isBlank()) return@forEachIndexed
                    if (used.size >= 5) return@forEachIndexed
                    used.add(h)
                    sb.append("[").append(used.size).append("] ").append(h.title).append(" (").append(h.domain).append(")\n")
                        .append(body.take(1500)).append("\n\n")
                }
                if (wiki.isNotBlank()) sb.append("Вікіпедія:\n").append(wiki.take(900)).append("\n")
                sourcesText = sb.toString().trim()
                lastSources = used.take(4)
                Log.e(TAG, "☁️ smart search '$query': ${used.size} sources, ${sourcesText.length} chars")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "smart search failed: ${e.message}")
            }
        }

        val now = LocalDateTime.now()
        val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm", Locale("uk", "UA")))
        val lang = when (s.answerLanguage) {
            "en" -> "Відповідай англійською."
            "auto" -> "Відповідай мовою питання."
            else -> "Відповідай українською."
        }
        val style = if (s.answerStyle == "short")
            "Відповідай стисло: 1–3 речення."
        else
            "Довжину добирай за питанням: просте — 1–2 речення; складне — короткі абзаци або список з рядків, що починаються з «•». " +
                "Спершу пряма відповідь, потім деталі."
        val system = buildString {
            append("Ти — голосовий асистент у мобільному лаунчері Android. Зараз: ").append(date).append(". ")
            append(lang).append(' ').append(style).append('\n')
            append("Не використовуй markdown-розмітку (зірочки, решітки, блоки коду) і не став посилань чи номерів джерел у тексті: ")
            append("відповідь озвучується, а джерела показуються окремо. ")
            if (summary.isNotBlank()) append("Підсумок попередньої частини розмови: ").append(summary).append(' ')
            append("Враховуй попередню розмову: короткі уточнення («а в Кракові?», «чому?») стосуються її теми. ")
            append("Якщо передано джерела — спирайся лише на них і на загальновідомі факти, нічого не вигадуй. ")
            append("Якщо в джерелах немає відповіді, чесно скажи про це. Для свіжих даних (ціни, новини, результати) називай, звідки вони, і коли це актуально.")
        }
        val user = buildString {
            if (sourcesText.isNotBlank()) append("Джерела з інтернету:\n").append(sourcesText).append("\n\n")
            append("Питання: ").append(question)
        }

        onStatus?.invoke("Пишу відповідь…")
        val acc = StringBuilder()
        return try {
            val r = CloudLlm.generate(s, system, user, histMsgs) { tok -> acc.append(tok); onToken?.invoke(tok) }
            Log.e(TAG, "☁️ cloud answer via ${CloudLlm.lastProvider}: ${r.length} chars")
            r.trim()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "cloud failed: ${e.message}")
            lastSources = if (acc.length > 40) lastSources else emptyList()
            if (acc.length > 40) acc.toString().trim()      // частину вже озвучено: лишаємо її
            else {
                cloudCooldownUntil = System.currentTimeMillis() + 90_000   // кілька хвилин не мучимо недоступну хмару
                null
            }
        }
    }

    /** Чи варто віддати це прохання хмарному агентові (керування телефоном без локальної моделі). */
    fun cloudAgentEligible(question: String): Boolean {
        val s = SettingsStorage(context).load()
        if (!s.cloudEnabled || !CloudLlm.hasKey(s)) return false
        if (System.currentTimeMillis() < cloudCooldownUntil) return false
        if (SCREEN_ASK.containsMatchIn(question.lowercase())) return false
        if (MacroBuilder.looksLikeRequest(question)) return false
        return agentEngine.looksActionable(question)
    }

    /** Керування телефоном через Gemini/Groq: хмара обирає інструмент, виконуємо його на пристрої. CLOUD_FAILED — хмара недоступна. */
    suspend fun cloudAgent(question: String, history: String = ""): String {
        val s = SettingsStorage(context).load()
        return try {
            val r = agentEngine.processWithTools(question, compactHistory(history), cloud = s)
            Log.e(TAG, "☁️ cloud agent via ${CloudLlm.lastProvider}: ${r.take(80)}")
            r
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "cloud agent failed: ${e.message}")
            cloudCooldownUntil = System.currentTimeMillis() + 90_000
            CLOUD_FAILED
        }
    }

    suspend fun answerWithAgent(question: String, history: String = ""): String {
        if (!llm.isReady) return "LLM не активна"
        macroFlow(question)?.let { return it }
        return try {
            // Використовуємо наш власний AgentEngine, який вже написаний і працює
            agentEngine.processWithTools(question, compactHistory(history))
        } catch (t: Throwable) {
            Log.e("ChatEngine", "Agent error", t)
            "Помилка агента: ${t.message}"
        }
    }
}
