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
        contacts: List<ContactInfo>
    ): List<Suggestion> {
        if (query.isBlank()) return apps.map { Suggestion.App(it, 0) }

        val q = query.lowercase().trim()
        val result = mutableListOf<Suggestion>()

        // === 1. Дії (вбудовані + кастомні) ===
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
                result.add(
                    Suggestion.Action(
                        id = a.id,
                        label = a.label,
                        subtitle = a.subtitle,
                        command = a.build(),
                        score = score,
                        isCustom = isCustom
                    )
                )
            }

        // === 2. Контакти ===
        contacts
            .filter { it.name.lowercase().contains(q) }
            .sortedBy { it.name.length }
            .take(3)
            .forEach { contact ->
                val baseScore = when {
                    contact.name.lowercase() == q -> 96
                    contact.name.lowercase().startsWith(q) -> 90
                    else -> 80
                }
                val phone = contact.phone
                if (!phone.isNullOrBlank()) {
                    result.add(Suggestion.Contact(contact.name, "Подзвонити", ContactAction.CALL, phone, baseScore))
                    result.add(Suggestion.Contact(contact.name, "Telegram", ContactAction.TELEGRAM, phone, baseScore - 1))
                    result.add(Suggestion.Contact(contact.name, "WhatsApp", ContactAction.WHATSAPP, phone, baseScore - 2))
                    result.add(Suggestion.Contact(contact.name, "SMS", ContactAction.SMS, phone, baseScore - 3))
                    result.add(Suggestion.Contact(contact.name, "Viber", ContactAction.VIBER, phone, baseScore - 4))
                } else {
                    result.add(Suggestion.Contact(contact.name, "Telegram", ContactAction.TELEGRAM, contact.name, baseScore))
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
