package com.dynamicai.launcher.command

sealed class Command {
    data class OpenApp(val query: String) : Command()
    data class WebSearch(val query: String) : Command()
    data class Call(val name: String) : Command()
    object ToggleFlashlight : Command()
    data class Unknown(val raw: String) : Command()
}
