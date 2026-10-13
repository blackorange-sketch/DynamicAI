package com.dynamicai.launcher.suggestion

import android.content.Context
import com.dynamicai.launcher.command.Command
import com.dynamicai.launcher.command.FuzzyMatcher
import com.dynamicai.launcher.command.SettingsRoutes
import com.dynamicai.launcher.model.AppInfo

class SuggestionProvider(context: Context) {

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

        // === 2. Контакти з номерами ===
        val matchedContacts = contacts
            .filter { it.name.lowercase().contains(q) }
            .sortedBy { it.name.length }
            .take(2)

        var contactRows = 0
        for (contact in matchedContacts) {
            val baseScore = when {
                contact.name.lowercase() == q -> 96
                contact.name.lowercase().startsWith(q) -> 92
                else -> 84
            }
            val phones = contact.phones.take(3)
            if (phones.isEmpty()) continue

            // Дзвінок — для кожного номера
            phones.forEachIndexed { idx, phone ->
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
                "Viber" to ContactAction.VIBER
            )
            others.forEachIndexed { i, (label, action) ->
                if (contactRows >= 8) return@forEachIndexed
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

        return result
    }
}
