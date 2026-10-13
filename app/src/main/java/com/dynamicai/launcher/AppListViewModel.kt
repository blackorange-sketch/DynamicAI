package com.dynamicai.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dynamicai.launcher.command.Command
import com.dynamicai.launcher.command.CommandExecutor
import com.dynamicai.launcher.command.CommandParser
import com.dynamicai.launcher.command.CommandResult
import com.dynamicai.launcher.data.AppRepository
import com.dynamicai.launcher.data.CommandHistory
import com.dynamicai.launcher.data.DockStorage
import com.dynamicai.launcher.llm.LlmEngine
import com.dynamicai.launcher.llm.NoOpLlmEngine
import com.dynamicai.launcher.model.AppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppListViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppRepository(app)
    private val dockStorage = DockStorage(app)
    private val parser = CommandParser()
    private val executor = CommandExecutor(app)
    private val historyStorage = CommandHistory(app)
    val llm: LlmEngine = NoOpLlmEngine()

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _dockPkgs = MutableStateFlow<List<String>>(emptyList())
    val dockPkgs: StateFlow<List<String>> = _dockPkgs.asStateFlow()

    private val _response = MutableStateFlow("")
    val response: StateFlow<String> = _response.asStateFlow()

    private val _pendingChoice = MutableStateFlow<List<AppInfo>?>(null)
    val pendingChoice: StateFlow<List<AppInfo>?> = _pendingChoice.asStateFlow()

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private var pendingText: String = ""

    init {
        _dockPkgs.value = dockStorage.getDockApps()
        _history.value = historyStorage.get()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _apps.value = repo.loadInstalledApps()
        }
    }

    fun launch(pkg: String) {
        repo.launchApp(pkg)
    }

    fun toggleDock(pkg: String) {
        dockStorage.toggle(pkg)
        _dockPkgs.value = dockStorage.getDockApps()
    }

    fun isCommand(text: String): Boolean {
        val cmd = parser.parse(text)
        return cmd !is Command.Unknown
    }

    fun executeCommand(text: String) {
        if (text.isBlank()) return
        val cmd = parser.parse(text)
        val result = executor.execute(cmd, _apps.value)
        handleResult(result, text)
    }

    private fun handleResult(result: CommandResult, originalText: String) {
        when (result) {
            is CommandResult.Success -> {
                _response.value = result.message
                historyStorage.add(originalText)
                _history.value = historyStorage.get()
            }
            is CommandResult.Error -> {
                _response.value = result.message
            }
            is CommandResult.ChooseApp -> {
                _response.value = "Обери застосунок для \"${result.query}\":"
                _pendingChoice.value = result.candidates
                pendingText = originalText
            }
        }
    }

    fun onAppChosen(app: AppInfo) {
        val result = executor.launchApp(app)
        _pendingChoice.value = null
        handleResult(result, pendingText)
        pendingText = ""
    }

    fun dismissChoice() {
        _pendingChoice.value = null
        _response.value = ""
        pendingText = ""
    }

    fun clearHistory() {
        historyStorage.clear()
        _history.value = emptyList()
    }
}
