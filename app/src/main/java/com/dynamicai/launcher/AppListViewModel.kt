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
import com.dynamicai.launcher.voice.TtsManager
import com.dynamicai.launcher.system.ShizukuManager
import com.dynamicai.launcher.data.WeatherRepository
import com.dynamicai.launcher.data.WeatherState
import com.dynamicai.launcher.folder.Folder
import com.dynamicai.launcher.folder.FolderStorage
import com.dynamicai.launcher.llm.ChatEngine
import com.dynamicai.launcher.llm.LlmAgent
import com.dynamicai.launcher.llm.WebSearchRepository
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
    private val folderStorage = FolderStorage(app)
    private val appsCache = com.dynamicai.launcher.data.AppsCache(app)
    val shizukuManager = ShizukuManager(app)
    private val weatherRepo by lazy { WeatherRepository(app) }

    val llm by lazy { LiteRtLlmEngine(app) }
    val tts by lazy { TtsManager(app) }
    val downloader by lazy { ModelDownloader(app) }

    private val _downloadProgress = MutableStateFlow<ModelDownloader.Progress?>(null)
    val downloadProgress: StateFlow<ModelDownloader.Progress?> = _downloadProgress.asStateFlow()
    private val llmAgent by lazy { LlmAgent(llm) }
    val chatEngine by lazy { ChatEngine(app, llm, WebSearchRepository()) }

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

    private val _weather = MutableStateFlow<WeatherState>(WeatherState.Loading)
    val weather: StateFlow<WeatherState> = _weather.asStateFlow()

    private val _folders = MutableStateFlow(folderStorage.get())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    private val _dockPkgs = MutableStateFlow<List<String>>(emptyList())
    val dockPkgs: StateFlow<List<String>> = _dockPkgs.asStateFlow()

    private val _recentTasks = MutableStateFlow<List<ShizukuManager.RecentTask>>(emptyList())
    val recentTasks: StateFlow<List<ShizukuManager.RecentTask>> = _recentTasks.asStateFlow()

    private val _response = MutableStateFlow("")
    val response: StateFlow<String> = _response.asStateFlow()

    private val _dockAppeared = MutableStateFlow(false)
    val dockAppeared: StateFlow<Boolean> = _dockAppeared.asStateFlow()
    fun markDockAppeared() { _dockAppeared.value = true }

    private val _openNotesRequest = MutableStateFlow(false)
    val openNotesRequest: StateFlow<Boolean> = _openNotesRequest.asStateFlow()

    fun clearOpenNotes() { _openNotesRequest.value = false }

    private val _llmReady = MutableStateFlow(false)
    val llmReady: StateFlow<Boolean> = _llmReady.asStateFlow()

    // Публічний, але НЕ торкається llm поки не спитають
    fun checkLlmReady() {
        // Тільки перевірка наявності файлу моделі. Engine НЕ створюється.
        _llmReady.value = downloader.isDownloaded
    }

    /** Викликається лениво при першому реальному запиті LLM. */
    private suspend fun ensureLlmLoaded(): Boolean {
        if (llm.isReady) return true
        if (!downloader.isDownloaded) return false
        return try {
            llm.initialize(downloader.modelFile)
        } catch (_: Throwable) { false }
    }

    private val _pendingChoice = MutableStateFlow<List<AppInfo>?>(null)
    val pendingChoice: StateFlow<List<AppInfo>?> = _pendingChoice.asStateFlow()

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private var pendingText: String = ""

    private val runtimePrefs = app.getSharedPreferences("dynamicai_runtime", android.content.Context.MODE_PRIVATE)
    private val pkgChangeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "apps_changed_at") {
            android.util.Log.e("AppListVM", "package changed, refreshing")
            refresh()
        }
    }

    init {
        runtimePrefs.registerOnSharedPreferenceChangeListener(pkgChangeListener)
        android.util.Log.d("APPINIT", "init start, RSS before everything")
        viewModelScope.launch {
            executor.flashlightOn.collect {
                updateQuery("")
            }
        }
        _dockPkgs.value = dockStorage.getDockApps()
        refreshWeather()
        _history.value = historyStorage.get()
        refresh()
        android.util.Log.d("APPINIT", "init done")
    }

    fun refreshIfStale() {
        val last = runtimePrefs.getLong("apps_refreshed_at", 0L)
        if (System.currentTimeMillis() - last > 60_000L) {
            refresh()
        }
    }

    fun refresh() {
        runtimePrefs.edit().putLong("apps_refreshed_at", System.currentTimeMillis()).apply()
        viewModelScope.launch {
            // 1. Одразу показуємо кешований список (миттєво)
            val cached = try { repo.loadCached() } catch (_: Throwable) { emptyList() }
            if (cached.isNotEmpty()) {
                _apps.value = cached
                updateQuery("")
            }

            // 2. Паралельно оновлюємо повний список
            try {
                val loaded = repo.loadInstalledApps()
                _apps.value = loaded
                try { appsCache.save(loaded) } catch (_: Exception) {}
                updateQuery("")
            } catch (_: Throwable) {}
        }
    }

    fun updateQuery(query: String) {
        viewModelScope.launch {
            val foldersPkgs = _folders.value.flatMap { it.packageNames }.toSet()
            val visibleApps = _apps.value.filter {
                it.packageName !in _hidden.value && it.packageName !in foldersPkgs
            }
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
        refreshWeather()
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
        checkLlmReady()
    }

    fun updateSettings(newSettings: LauncherSettings) {
        settingsStorage.save(newSettings)
        _settings.value = newSettings
        // Оновлюємо TTS якщо змінились speed/pitch
        tts.reloadSettings()
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

    fun createFolder(name: String, initialPkgs: List<String>): Folder {
        return try {
            val f = folderStorage.create(name, initialPkgs)
            val all = folderStorage.get()
            _folders.value = all
            _response.value = "Папка '${f.name}' створена · всього папок: ${all.size} · застосунків: ${f.packageNames.size}"
            updateQuery("")
            f
        } catch (t: Throwable) {
            _response.value = "Помилка створення папки: ${t.message}"
            Folder(id = "err", name = "err", packageNames = emptyList())
        }
    }

    fun deleteFolder(id: String) {
        folderStorage.remove(id)
        _folders.value = folderStorage.get()
        updateQuery("")
    }

    fun addToFolder(folderId: String, pkg: String) {
        folderStorage.addApp(folderId, pkg)
        _folders.value = folderStorage.get()
        updateQuery("")
    }

    fun removeFromFolder(folderId: String, pkg: String) {
        folderStorage.removeApp(folderId, pkg)
        _folders.value = folderStorage.get()
        updateQuery("")
    }

    fun folderOf(pkg: String): Folder? = folderStorage.folderOf(pkg)

    fun setResponse(text: String) {
        _response.value = text
    }

    private var weatherJob: kotlinx.coroutines.Job? = null

    fun startWeatherAutoRefresh(screenOnCheck: () -> Boolean) {
        weatherJob?.cancel()
        weatherRepo.loadCachedWeather()?.let { _weather.value = it }
        weatherJob = viewModelScope.launch {
            // Перше завантаження
            _weather.value = weatherRepo.fetch()

            while (true) {
                kotlinx.coroutines.delay(60 * 60 * 1000L)  // 1 година
                if (screenOnCheck()) {
                    _weather.value = weatherRepo.fetch()
                }
            }
        }
    }

    fun refreshWeather() {
        // 1. Спершу показуємо кеш — миттєво
        weatherRepo.loadCachedWeather()?.let { cached ->
            _weather.value = cached
        } ?: run {
            _weather.value = WeatherState.Loading
        }
        // 2. Потім у фоні підтягуємо свіжу
        viewModelScope.launch {
            val fresh = weatherRepo.fetch()
            if (fresh is WeatherState.Data) {
                _weather.value = fresh
            } else if (_weather.value !is WeatherState.Data) {
                // якщо кешу не було — показуємо помилку
                _weather.value = fresh
            }
        }
    }

    fun loadRecentTasks() {
        viewModelScope.launch {
            _recentTasks.value = shizukuManager.getRecentTasks()
        }
    }

    fun forceStopApp(packageName: String) {
        viewModelScope.launch {
            shizukuManager.forceStop(packageName)
            loadRecentTasks()
        }
    }

    fun forceStopAll() {
        viewModelScope.launch {
            _recentTasks.value.forEach { shizukuManager.forceStop(it.packageName) }
            loadRecentTasks()
        }
    }

    suspend fun testLlm(prompt: String): String {
        val diag = buildString {
            append("isReady=").append(llm.isReady).append("\n")
            append("lastError='").append(llm.lastError).append("'\n")
            append("---\n")
        }

        // Спочатку сирий виклик (показує "сиру" відповідь)
        val raw = llm.complete(prompt)
        val rawText = raw ?: "(null)"

        // Потім через LlmAgent (з systemPrompt + tool call)
        val agentResult = try {
            llmAgent.resolve(prompt)?.toString() ?: "(agent: null)"
        } catch (t: Throwable) {
            "(agent error: ${t.message})"
        }

        return diag + "RAW:\n" + rawText.take(400) + "\n\nAGENT:\n" + agentResult
    }

    fun isCommand(text: String) = parser.parse(text) !is com.dynamicai.launcher.command.Command.Raw

    fun executeCommand(text: String) {
        if (text.isBlank()) return
        val cmd = parser.parse(text)

        // Питання → ChatEngine (LLM + Web Search)
        if (cmd is com.dynamicai.launcher.command.Command.AskQuestion) {
            viewModelScope.launch {
                _response.value = "🔍 Шукаю…"
                val answer = chatEngine.answer(cmd.query)
                _response.value = answer
                val settings = settingsStorage.load()
                if (settings.useTts) {
                    tts.speak(answer)
                }
            }
            return
        }

        if (cmd is com.dynamicai.launcher.command.Command.Raw) {
            val asApp = executor.resolveOpenApp(text, _apps.value)
            if (asApp is CommandResult.Success || asApp is CommandResult.ChooseApp) {
                handleResult(asApp, text)
                return
            }
            // Перевіряємо toggle + готовність LLM (через файл, БЕЗ engine)
            val settings = settingsStorage.load()
            if (!settings.useLlm || !downloader.isDownloaded) {
                _response.value = "Не розумію команду: \"$text\""
                return
            }

            viewModelScope.launch {
                _response.value = "🧠 Думаю… (5-15 сек)"
                try {
                    ensureLlmLoaded()
                    val llmCmd = llmAgent.resolve(text)
                    if (llmCmd != null) {
                        val result = executor.execute(llmCmd, _apps.value)
                        handleResult(result, text)
                    } else {
                        _response.value = "Не розумію команду: \"$text\""
                    }
                } catch (t: Throwable) {
                    _response.value = "Помилка LLM: ${t.message}"
                }
            }
            return
            return
        }

        val result = executor.execute(cmd, _apps.value)
        handleResult(result, text)
    }


    fun translateScreen(targetLang: String = "uk") {
        viewModelScope.launch {
            _response.value = "🌐 Роблю скріншот…"
            try {
                ensureLlmLoaded()
                val translator = com.dynamicai.launcher.ocr.ScreenTranslator(getApplication(), llm)
                val blocks = translator.captureAndTranslate(
                    ocrLangs = "ukr+eng",
                    targetLang = targetLang
                ) { stage ->
                    _response.value = "🌐 $stage"
                }
                if (blocks.isEmpty()) {
                    _response.value = "Не вдалось розпізнати текст (перевір чи завантажено OCR-мови в налаштуваннях)"
                    return@launch
                }
                com.dynamicai.launcher.ocr.ScreenTranslateOverlay.show(
                    context = getApplication(),
                    blocks = blocks,
                    targetLang = targetLang
                )
                _response.value = "Перекладено ${blocks.size} блоків"
                val st = settingsStorage.load()
                if (st.useTts) tts.speak("Готово. Перекладено ${blocks.size} блоків")
            } catch (t: Throwable) {
                _response.value = "Помилка перекладу: ${t.message}"
            }
        }
    }


    fun translateText(text: String, to: String) {
        viewModelScope.launch {
            _response.value = "🌐 Перекладаю…"
            try {
                val langName = when (to) {
                    "en" -> "англійську"
                    "uk" -> "українську"
                    "pl" -> "польську"
                    "de" -> "німецьку"
                    "ru" -> "російську"
                    else -> to
                }
                val prompt = "Переклади наступний текст на $langName. Виведи ТІЛЬКИ переклад, без пояснень, без лапок:\n\n$text"
                if (!ensureLlmLoaded()) {
                    _response.value = "LLM не завантажена"
                    return@launch
                }
                val result = llm.complete(prompt)?.trim()
                if (result.isNullOrBlank()) {
                    _response.value = "Не вдалось перекласти"
                } else {
                    _response.value = "📝 $text\n➡️ $result"
                    val st = settingsStorage.load()
                    if (st.useTts) tts.speak(result)
                }
            } catch (t: Throwable) {
                _response.value = "Помилка перекладу: ${t.message}"
            }
        }
    }

    private fun handleResult(result: CommandResult, originalText: String) {
        if (result is CommandResult.Error && result.message.startsWith("__TRANSLATE_TEXT__")) {
            val rest = result.message.removePrefix("__TRANSLATE_TEXT__")
            val parts = rest.split("\u0000", limit = 2)
            val to = parts.getOrNull(0) ?: "en"
            val text = parts.getOrNull(1) ?: ""
            translateText(text, to)
            return
        }
        if (result is CommandResult.Error && result.message.startsWith("__TRANSLATE_SCREEN__")) {
            val lang = result.message.removePrefix("__TRANSLATE_SCREEN__").ifBlank { "en" }
            translateScreen(lang)
            return
        }
        if (result is CommandResult.Error && result.message == "__OPEN_NOTES__") {
            _openNotesRequest.value = true
            _response.value = ""
            return
        }
        when (result) {
            is CommandResult.Success -> {
                _response.value = result.message
                historyStorage.add(originalText)
                _history.value = historyStorage.get()
                // Озвучуємо
                val settings = settingsStorage.load()
                if (settings.useTts) {
                    tts.speak(result.message)
                }
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
        tts.shutdown()
        super.onCleared()
    }
}
