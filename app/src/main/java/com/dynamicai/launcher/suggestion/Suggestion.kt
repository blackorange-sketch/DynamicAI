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
        override val score: Int
    ) : Suggestion() {
        override val label: String get() = "$name · $actionLabel"
    }
}

fun Suggestion.stableKey(): String = when (this) {
    is Suggestion.App -> "app:${app.packageName}"
    is Suggestion.Action -> "action:$id"
    is Suggestion.Setting -> "setting:$route"
    is Suggestion.Contact -> "contact:$name:${actionType.name}:$target"
}
