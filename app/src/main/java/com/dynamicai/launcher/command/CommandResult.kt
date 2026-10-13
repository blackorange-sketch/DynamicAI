package com.dynamicai.launcher.command

import com.dynamicai.launcher.model.AppInfo

sealed class CommandResult {
    data class Success(val message: String) : CommandResult()
    data class Error(val message: String) : CommandResult()
    data class ChooseApp(val query: String, val candidates: List<AppInfo>) : CommandResult()
}
