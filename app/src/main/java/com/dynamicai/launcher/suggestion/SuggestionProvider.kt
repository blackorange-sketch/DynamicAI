package com.dynamicai.launcher.suggestion

import android.content.Context
import com.dynamicai.launcher.command.Command
import com.dynamicai.launcher.command.FuzzyMatcher
import com.dynamicai.launcher.command.SettingsRoutes
import com.dynamicai.launcher.model.AppInfo

class SuggestionProvider(private val context: Context) {

    private val customStorage = CustomActionStorage(context)

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
        apps
            .map { it to FuzzyMatcher.score(it.label, q) }
            .filter { it.second >= 35 }
            .sortedByDescending { it.second }
            .take(8)
            .forEach { (app, score) ->
                result.add(Suggestion.App(app, score))
            }

        // === 5. Макроси, нотатки, збережені розмови, «спитати асистента» ===
        try {
            com.dynamicai.launcher.macro.MacroStorage(context).get().forEach { m ->
                val phrases = m.trigger.split(',').map { it.trim() }.filter { it.isNotBlank() }
                val best = (listOf(m.name) + phrases).maxOf { FuzzyMatcher.score(it, q) }
                if (best >= 60) result.add(Suggestion.Extra("macro", m.icon, m.name, "Макрос · запустити", m.id, best))
            }
            com.dynamicai.launcher.note.NotesStorage(context).get()
                .filter { it.text.contains(q, ignoreCase = true) }.take(2)
                .forEach { result.add(Suggestion.Extra("note", "📝", it.text.replace("\n", " ").take(60), "Нотатка", it.id, 50)) }
            com.dynamicai.launcher.overlay.ChatArchive.init(context)
            val fmt = java.text.SimpleDateFormat("d MMM", java.util.Locale("uk", "UA"))
            com.dynamicai.launcher.overlay.ChatArchive.sorted()
                .filter { c -> c.title.contains(q, ignoreCase = true) || c.messages.any { it.content.contains(q, ignoreCase = true) } }
                .take(2)
                .forEach { result.add(Suggestion.Extra("chat", "💬", c(it.title), "Розмова · ${fmt.format(java.util.Date(it.ts))}", it.id, 45)) }
        } catch (_: Throwable) {}
        if (q.length >= 3) {
            result.add(Suggestion.Extra("ask", "🤖", "Спитати асистента: «${query.trim().take(40)}»", "Відповідь текстом і голосом", query.trim(), 10))
        }

        return result.sortedByDescending { it.score }
    }

    private fun c(s: String) = s

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
