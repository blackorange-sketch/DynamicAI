package com.dynamicai.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dynamicai.launcher.command.CommandExecutor
import com.dynamicai.launcher.command.CommandParser
import com.dynamicai.launcher.command.CommandResult
import com.dynamicai.launcher.data.AppRepository
import com.dynamicai.launcher.data.CommandHistory
import com.dynamicai.launcher.data.DockStorage
import com.dynamicai.launcher.data.HiddenAppsStorage
import com.dynamicai.launcher.data.LauncherSettings
import com.dynamicai.launcher.data.SettingsStorage
import com.dynamicai.launcher.llm.LlmAgent
import com.dynamicai.launcher.llm.LiteRtLlmEngine
import com.dynamicai.launcher.llm.ModelDownloader
import com.dynamicai.launcher.model.AppInfo
import com.dynamicai.launcher.suggestion.ContactInfo
import com.dynamicai.launcher.suggestion.ContactRepository
import com.dynamicai.launcher.suggestion.ContactAction
import com.dynamicai.launcher.suggestion.Suggestion
import com.dynamicai.launcher.suggestion.SuggestionProvider
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
    private val suggestionProvider = SuggestionProvider(app)
    private val contactRepo = ContactRepository(app)
    private val settingsStorage = SettingsStorage(app)
    private val hiddenStorage = HiddenAppsStorage(app)

    val llm = LiteRtLlmEngine(app)
    val downloader = ModelDownloader(app)

    private val _downloadProgress = MutableStateFlow<ModelDownloader.Progress?>(null)
    val downloadProgress: StateFlow<ModelDownloader.Progress?> = _downloadProgress.asStateFlow()
    private val llmAgent = LlmAgent(llm)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _contacts = MutableStateFlow<List<ContactInfo>>(emptyList())
    val contacts: StateFlow<List<ContactInfo>> = _contacts.asStateFlow()

    private val _suggestions = MutableStateFlow<List<Suggestion>>(emptyList())
    val suggestions: StateFlow<List<Suggestion>> = _suggestions.asStateFlow()

    private val _settings = MutableStateFlow(settingsStorage.load())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    private val _hidden = MutableStateFlow(hiddenStorage.get())
    val hidden: StateFlow<Set<String>> = _hidden.asStateFlow()

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
        viewModelScope.launch {
            executor.flashlightOn.collect {
                updateQuery("")
            }
        }
        _dockPkgs.value = dockStorage.getDockApps()
        _history.value = historyStorage.get()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _apps.value = repo.loadInstalledApps()
            updateQuery("")
        }
    }

    fun updateQuery(query: String) {
        viewModelScope.launch {
            val visibleApps = _apps.value.filter { it.packageName !in _hidden.value }
            _suggestions.value = suggestionProvider.build(
                query, visibleApps, _contacts.value, executor.flashlightOn.value
            )
        }
    }

    fun loadContacts() {
        viewModelScope.launch {
            _contacts.value = contactRepo.loadAll()
            updateQuery("")
        }
    }

    fun launch(pkg: String) {
        repo.launchApp(pkg)
    }

    fun toggleDock(pkg: String) {
        dockStorage.toggle(pkg)
        _dockPkgs.value = dockStorage.getDockApps()
    }

    fun executeSuggestion(s: Suggestion) {
        when (s) {
            is Suggestion.App -> launch(s.app.packageName)
            is Suggestion.Action -> {
                val result = executor.execute(s.command, _apps.value)
                handleResult(result, s.label)
            }
            is Suggestion.Setting -> {
                val result = executor.execute(
                    com.dynamicai.launcher.command.Command.OpenSettings(s.route),
                    _apps.value
                )
                handleResult(result, s.label)
            }
            is Suggestion.Contact -> executeContact(s)
        }
    }

    private fun executeContact(s: Suggestion.Contact) {
        val cmd = when (s.actionType) {
            ContactAction.CALL -> com.dynamicai.launcher.command.Command.Call(s.target)
            ContactAction.SMS -> com.dynamicai.launcher.command.Command.DeepLink("sms", s.target)
            ContactAction.TELEGRAM -> com.dynamicai.launcher.command.Command.SearchInApp("telegram", s.name)
            ContactAction.WHATSAPP -> com.dynamicai.launcher.command.Command.DeepLink("whatsapp", s.target)
            ContactAction.VIBER -> com.dynamicai.launcher.command.Command.DeepLink("viber", s.target)
            ContactAction.INSTAGRAM -> com.dynamicai.launcher.command.Command.SearchInApp("insta", s.name)
            ContactAction.SIGNAL -> com.dynamicai.launcher.command.Command.DeepLink("signal", s.target)
            ContactAction.EMAIL -> com.dynamicai.launcher.command.Command.DeepLink("email", s.target)
        }
        val result = executor.execute(cmd, _apps.value)
        handleResult(result, s.label)
    }

    fun downloadModel() {
        viewModelScope.launch {
            downloader.download().collect { progress ->
                _downloadProgress.value = progress
                if (progress is ModelDownloader.Progress.Complete) {
                    llm.initialize(progress.file)
                    _downloadProgress.value = null
                }
            }
        }
    }

    fun initLlmIfReady() {
        if (downloader.isDownloaded && !llm.isReady) {
            viewModelScope.launch {
                llm.initialize(downloader.modelFile)
            }
        }
    }

    fun updateSettings(newSettings: LauncherSettings) {
        settingsStorage.save(newSettings)
        _settings.value = newSettings
    }

    fun hideApp(pkg: String) {
        hiddenStorage.hide(pkg)
        _hidden.value = hiddenStorage.get()
        updateQuery("")
    }

    fun unhideApp(pkg: String) {
        hiddenStorage.show(pkg)
        _hidden.value = hiddenStorage.get()
        updateQuery("")
    }

    fun clearHidden() {
        hiddenStorage.clear()
        _hidden.value = emptySet()
        updateQuery("")
    }

    fun setResponse(text: String) {
        _response.value = text
    }

    fun isCommand(text: String) = parser.parse(text) !is com.dynamicai.launcher.command.Command.Raw

    fun executeCommand(text: String) {
        if (text.isBlank()) return
        val cmd = parser.parse(text)

        if (cmd is com.dynamicai.launcher.command.Command.Raw) {
            val asApp = executor.resolveOpenApp(text, _apps.value)
            if (asApp is CommandResult.Success || asApp is CommandResult.ChooseApp) {
                handleResult(asApp, text)
                return
            }
            viewModelScope.launch {
                _response.value = "Думаю…"
                val llmCmd = llmAgent.resolve(text)
                if (llmCmd != null) {
                    val result = executor.execute(llmCmd, _apps.value)
                    handleResult(result, text)
                } else {
                    _response.value = "Не розумію команду: \"$text\""
                }
            }
            return
        }

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

    override fun onCleared() {
        llm.close()
        super.onCleared()
    }
}
