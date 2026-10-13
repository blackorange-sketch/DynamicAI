package com.dynamicai.launcher.suggestion

import com.dynamicai.launcher.command.Command
import com.dynamicai.launcher.model.AppInfo

enum class ContactAction { CALL, SMS, TELEGRAM, WHATSAPP, VIBER, INSTAGRAM, SIGNAL, EMAIL }

sealed class Suggestion {
    abstract val label: String
    abstract val score: Int

    data class App(
        val app: AppInfo,
        override val score: Int
    ) : Suggestion() {
        override val label: String get() = app.label
    }

    data class Action(
        val id: String,
        override val label: String,
        val subtitle: String?,
        val command: Command,
        override val score: Int,
        val isCustom: Boolean = false
    ) : Suggestion()

    data class Setting(
        override val label: String,
        val route: String,
        override val score: Int
    ) : Suggestion()

    data class Contact(
        val name: String,
        val actionLabel: String,
        val actionType: ContactAction,
        val target: String,
        val phoneDisplay: String?,
        override val score: Int
    ) : Suggestion() {
        override val label: String get() = name
        val subtitle: String get() = buildString {
            append(actionLabel)
            if (!phoneDisplay.isNullOrBlank()) append(" · ").append(phoneDisplay)
        }
    }
}

fun Suggestion.stableKey(): String = when (this) {
    is Suggestion.App -> "app:${app.packageName}"
    is Suggestion.Action -> "action:$id"
    is Suggestion.Setting -> "setting:$route"
    is Suggestion.Contact -> "contact:$name:${actionType.name}:$target"
}

fun formatPhone(raw: String): String {
    val digits = raw.filter { it.isDigit() || it == '+' }
    return when {
        digits.matches(Regex("\\+380\\d{9}")) ->
            "+380 ${digits.substring(4, 6)} ${digits.substring(6, 9)} ${digits.substring(9, 11)} ${digits.substring(11, 13)}"
        digits.matches(Regex("0\\d{9}")) ->
            "0${digits.substring(1, 3)} ${digits.substring(3, 6)} ${digits.substring(6, 8)} ${digits.substring(8, 10)}"
        digits.length > 7 -> digits
        else -> raw
    }
}
