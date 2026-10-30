package com.dynamicai.launcher.suggestion

import android.content.Context
import com.dynamicai.launcher.command.Command
import com.dynamicai.launcher.command.FuzzyMatcher
import com.dynamicai.launcher.command.SettingsRoutes
import com.dynamicai.launcher.model.AppInfo

class SuggestionProvider(private val context: Context) {

    private val customStorage = CustomActionStorage(context)

    // Кеш важких сховищ: раніше макроси/нотатки/архів чатів читались і парсились на кожен символ запиту
    private class Extras(
        val macros: List<com.dynamicai.launcher.macro.Macro>,
        val notes: List<com.dynamicai.launcher.note.Note>,
        val chats: List<com.dynamicai.launcher.overlay.SavedChat>,
        val at: Long
    )
    @Volatile private var extras: Extras? = null
    private fun extras(): Extras {
        val e = extras
        if (e != null && System.currentTimeMillis() - e.at < 4000) return e
        val macros = try { com.dynamicai.launcher.macro.MacroStorage(context).get() } catch (_: Throwable) { emptyList() }
        val notes = try { com.dynamicai.launcher.note.NotesStorage(context).get() } catch (_: Throwable) { emptyList() }
        val chats = try {
            com.dynamicai.launcher.overlay.ChatArchive.init(context)
            com.dynamicai.launcher.overlay.ChatArchive.sorted()
        } catch (_: Throwable) { emptyList() }
        return Extras(macros, notes, chats, System.currentTimeMillis()).also { extras = it }
    }

    private val categoryWords = mapOf(
        "games" to listOf("ігри", "гра", "games", "game"),
        "social" to listOf("соцмережі", "месенджер", "соцмережа", "social", "chat"),
        "media" to listOf("музика", "відео", "фото", "медіа", "music", "video", "photo"),
        "maps" to listOf("карти", "навігація", "maps", "navigation"),
        "tools" to listOf("інструменти", "робота", "новини", "tools", "news", "office")
    )

    fun build(
        query: String,
        apps: List<AppInfo>,
        contacts: List<ContactInfo>,
        flashlightOn: Boolean
    ): List<Suggestion> {
        if (query.isBlank()) return apps.map { Suggestion.App(it, 0) }

        val q = query.lowercase().trim()
        val result = mutableListOf<Suggestion>()

        // === 1. Дії ===
        val builtIn = ActionCatalog.all.map { a ->
            val best = a.keywords.maxOf { kw -> FuzzyMatcher.score(kw, q) }
            Triple(a, best, false)
        }
        val custom = customStorage.get().map { c ->
            val best = FuzzyMatcher.score(c.trigger, q)
            val action = BuiltInAction(
                id = c.id,
                label = c.label,
                keywords = listOf(c.trigger),
                subtitle = "кастомна",
                build = {
                    when (c.commandType) {
                        "app" -> Command.OpenApp(c.commandArg)
                        "url" -> Command.WebSearch(c.commandArg)
                        "setting" -> Command.OpenSettings(c.commandArg)
                        else -> Command.OpenApp(c.commandArg)
                    }
                }
            )
            Triple(action, best, true)
        }

        (builtIn + custom)
            .filter { it.second >= 55 }
            .sortedByDescending { it.second }
            .take(4)
            .forEach { (a, score, isCustom) ->
                val label = if (a.id == "flashlight" && flashlightOn) "Вимкнути ліхтарик" else a.label
                result.add(
                    Suggestion.Action(
                        id = a.id,
                        label = label,
                        subtitle = a.subtitle,
                        command = a.build(),
                        score = score,
                        isCustom = isCustom
                    )
                )
            }

        // === 2. Контакти з номерами (скоринг за якістю збігу) ===
        val matchedContacts = contacts
            .map { it to contactScore(it.name, q) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(2)

        var contactRows = 0
        for (contact in matchedContacts) {
            val baseScore = contactScore(contact.name, q)
            val phones = contact.phones.take(3)
            if (phones.isEmpty()) continue

            // Дзвінок — для кожного номера
            for ((idx, phone) in phones.withIndex()) {
                if (contactRows >= 8) break
                result.add(
                    Suggestion.Contact(
                        name = contact.name,
                        actionLabel = "Подзвонити",
                        actionType = ContactAction.CALL,
                        target = phone,
                        phoneDisplay = formatPhone(phone),
                        score = baseScore - idx
                    )
                )
                contactRows++
            }

            // Інші дії — тільки для першого номера
            val primary = phones.first()
            val primaryDisplay = formatPhone(primary)
            val others = listOf(
                "Telegram" to ContactAction.TELEGRAM,
                "WhatsApp" to ContactAction.WHATSAPP,
                "SMS" to ContactAction.SMS,
                "Viber" to ContactAction.VIBER,
                "Instagram" to ContactAction.INSTAGRAM
            )
            for ((i, entry) in others.withIndex()) {
                if (contactRows >= 8) break
                val (label, action) = entry
                result.add(
                    Suggestion.Contact(
                        name = contact.name,
                        actionLabel = label,
                        actionType = action,
                        target = primary,
                        phoneDisplay = primaryDisplay,
                        score = baseScore - 2 - i
                    )
                )
                contactRows++
            }
        }

        // === 3. Налаштування ===
        SettingsRoutes.all().keys
            .map { it to FuzzyMatcher.score(it, q) }
            .filter { it.second >= 60 }
            .sortedByDescending { it.second }
            .take(2)
            .forEach { (route, score) ->
                result.add(
                    Suggestion.Setting(
                        label = "Налаштування: ${route.replaceFirstChar { it.uppercase() }}",
                        route = route,
                        score = score - 5
                    )
                )
            }

        // === 4. Застосунки ===
        val appHits = apps
            .map { it to FuzzyMatcher.score(it.label, q) }
            .filter { it.second >= 35 }
            .map { (app, score) -> app to (score + AppUsage.boost(context, app.packageName)) }
            .sortedByDescending { it.second }
            .take(8)
        appHits.forEach { (app, score) -> result.add(Suggestion.App(app, score)) }

        // Пошук за категорією: «ігри», «музика», «карти»…
        if (q.length >= 3) {
            val cat = categoryWords.entries.firstOrNull { (_, words) -> words.any { it.startsWith(q) } }?.key
            if (cat != null) {
                val have = appHits.map { it.first.packageName }.toSet()
                apps.filter { it.category == cat && it.packageName !in have }
                    .sortedByDescending { AppUsage.count(context, it.packageName) }
                    .take(6)
                    .forEach { result.add(Suggestion.App(it, 58 + AppUsage.boost(context, it.packageName) / 2)) }
            }
        }

        // Калькулятор та конвертер
        try {
            val conv = com.dynamicai.launcher.util.UnitConverter.convert(q)
            if (conv != null) {
                result.add(Suggestion.Extra("calc", "🔁", "= $conv", "Скопіювати результат", conv, 99))
            } else if (q.any { it.isDigit() } && q.any { it in "+-*/×÷" } && q.matches(Regex("^[\\d\\s+\\-*/×÷().,]+$"))) {
                val r = com.dynamicai.launcher.util.Calculator.calculate(q)
                if (r is com.dynamicai.launcher.util.Calculator.Result.success && !r.value.isNaN() && !r.value.isInfinite()) {
                    val txt = if (r.value == Math.floor(r.value) && kotlin.math.abs(r.value) < 1e15) r.value.toLong().toString()
                    else String.format(java.util.Locale.US, "%.6f", r.value).trimEnd('0').trimEnd('.')
                    result.add(Suggestion.Extra("calc", "🧮", "= $txt", "Скопіювати результат", txt, 99))
                }
            }
        } catch (_: Throwable) {}

        // Фото й відео за назвою файлу (потрібен дозвіл на медіа)
        if (q.length >= 3) {
            try {
                result.addAll(findMedia(q))
            } catch (_: Throwable) {}
        }

        // === 5. Макроси, нотатки, збережені розмови, «спитати асистента» ===
        try {
            val ex = extras()
            ex.macros.forEach { m ->
                val phrases = m.trigger.split(',').map { it.trim() }.filter { it.isNotBlank() }
                val best = (listOf(m.name) + phrases).maxOf { FuzzyMatcher.score(it, q) }
                if (best >= 60) result.add(Suggestion.Extra("macro", m.icon, m.name, "Макрос · запустити", m.id, best))
            }
            ex.notes
                .filter { it.text.contains(q, ignoreCase = true) }.take(2)
                .forEach { result.add(Suggestion.Extra("note", "📝", it.text.replace("\n", " ").take(60), "Нотатка", it.id, 50)) }
            val fmt = java.text.SimpleDateFormat("d MMM", java.util.Locale("uk", "UA"))
            ex.chats
                .filter { c -> c.title.contains(q, ignoreCase = true) || c.messages.any { it.content.contains(q, ignoreCase = true) } }
                .take(2)
                .forEach { result.add(Suggestion.Extra("chat", "💬", c(it.title), "Розмова · ${fmt.format(java.util.Date(it.ts))}", it.id, 45)) }
        } catch (_: Throwable) {}
        if (q.length >= 2) {
            val enc = android.net.Uri.encode(query.trim())
            result.add(Suggestion.Extra("web", "🌐", "Шукати «${query.trim().take(40)}» в Google", "Браузер", "https://www.google.com/search?q=$enc", 9))
            result.add(Suggestion.Extra("web", "▶️", "Шукати в YouTube", "YouTube у браузері або додатку", "https://www.youtube.com/results?search_query=$enc", 8))
        }
        if (q.length >= 3) {
            result.add(Suggestion.Extra("ask", "🤖", "Спитати асистента: «${query.trim().take(40)}»", "Відповідь текстом і голосом", query.trim(), 10))
        }

        return result.sortedByDescending { it.score }
    }

    private fun c(s: String) = s

    private fun findMedia(q: String): List<Suggestion> {
        val out = mutableListOf<Suggestion>()
        val sources = listOf(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "🖼",
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "🎞"
        )
        for ((uri, icon) in sources) {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.MediaStore.MediaColumns._ID, android.provider.MediaStore.MediaColumns.DISPLAY_NAME),
                    "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                    arrayOf("%$q%"),
                    "${android.provider.MediaStore.MediaColumns.DATE_ADDED} DESC"
                )?.use { c ->
                    var n = 0
                    while (c.moveToNext() && n < 2) {
                        val id = c.getLong(0)
                        out.add(Suggestion.Extra("file", icon, c.getString(1) ?: "файл", "Файл · відкрити",
                            android.content.ContentUris.withAppendedId(uri, id).toString(), 40))
                        n++
                    }
                }
            } catch (_: Throwable) {}
        }
        return out
    }

    private fun contactScore(name: String, q: String): Int {
        val n = name.lowercase().trim()
        val nt = com.dynamicai.launcher.command.FuzzyMatcher.transliterate(n)
        val qt = com.dynamicai.launcher.command.FuzzyMatcher.transliterate(q)

        fun score(a: String, b: String): Int = when {
            a == b -> 100
            a.startsWith("$b ") -> 98
            a.startsWith(b) -> 92
            a.split(" ", "-", "_", ".", ",").any { it == b } -> 85
            a.contains("$b ") -> 80
            a.contains(" $b") -> 78
            a.contains(b) -> 55   // підрядок — низький пріоритет
            else -> 0
        }

        return maxOf(
            score(n, q),
            score(nt, qt),
            score(n, qt),
            score(nt, q)
        )
    }
}
