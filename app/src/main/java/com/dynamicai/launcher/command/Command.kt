package com.dynamicai.launcher.command

sealed class Command {
    data class OpenApp(val query: String) : Command()
    data class WebSearch(val query: String) : Command()
    data class Call(val name: String) : Command()
    data class SetAlarm(val time: String, val label: String?) : Command()
    data class SetTimer(val duration: String) : Command()
    data class OpenSettings(val which: String?) : Command()
    data class DeepLink(val app: String, val target: String) : Command()
    data class SearchInApp(val packageKeyword: String, val query: String) : Command()
    data class SetVolume(val level: Int) : Command()
    object ToggleFlashlight : Command()
    object Unknown : Command()
    data class Raw(val text: String) : Command()
}
