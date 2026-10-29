package com.dynamicai.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dynamicai.launcher.command.CommandExecutor
import com.dynamicai.launcher.command.CommandExecutorProvider
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
import com.dynamicai.launcher.llm.HybridLlmEngine
import java.io.File
import com.dynamicai.launcher.llm.ModelDownloader
import com.dynamicai.launcher.llm.ModelDownloadService
import com.dynamicai.launcher.model.AppInfo
import com.dynamicai.launcher.suggestion.ContactInfo
import com.dynamicai.launcher.suggestion.ContactRepository
import com.dynamicai.launcher.suggestion.ContactAction
import com.dynamicai.launcher.suggestion.Suggestion
import com.dynamicai.launcher.suggestion.SuggestionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class AppListViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        @Volatile private var sharedLlm: HybridLlmEngine? = null
        @Volatile private var sharedChatEngine: ChatEngine? = null
        @Volatile private var sharedDownloader: ModelDownloader? = null

        fun getSharedLlm(context: android.content.Context): HybridLlmEngine =
            sharedLlm ?: synchronized(this) {
                sharedLlm ?: HybridLlmEngine(context.applicationContext).also { sharedLlm = it }
            }

        fun getSharedChatEngine(context: android.content.Context): ChatEngine =
            sharedChatEngine ?: synchronized(this) {
                sharedChatEngine ?: ChatEngine(
                    context.applicationContext,
                    getSharedLlm(context),
                    WebSearchRepository()
                ).also { sharedChatEngine = it }
            }

        fun getSharedDownloader(context: android.content.Context): ModelDownloader =
            sharedDownloader ?: synchronized(this) {
                sharedDownloader ?: ModelDownloader(context.applicationContext).also { sharedDownloader = it }
            }
    }


    private val repo = AppRepository(app)
    private val dockStorage = DockStorage(app)
    private val parser = CommandParser()
    private val executor = CommandExecutorProvider.get(app)
    private val historyStorage = CommandHistory(app)
    private val suggestionProvider = SuggestionProvider(app)
    private val contactRepo = ContactRepository(app)
    private val settingsStorage = SettingsStorage(app)
    private val hiddenStorage = HiddenAppsStorage(app)
    private val folderStorage = FolderStorage(app)
    private val appsCache = com.dynamicai.launcher.data.AppsCache(app)
    val shizukuManager = ShizukuManager(app)
    private val weatherRepo by lazy { WeatherRepository(app) }

    val llm by lazy { getSharedLlm(app) }
    val tts by lazy { TtsManager(app) }
    val downloader by lazy { getSharedDownloader(app) }
    private val prefs = getApplication<android.app.Application>().getSharedPreferences("llm_prefs", android.content.Context.MODE_PRIVATE)
    var downloadingModelId by androidx.compose.runtime.mutableStateOf<String?>(null)
    var currentModelId by androidx.compose.runtime.mutableStateOf(prefs.getString("current_model_id", "gemma-4-E2B-cpu") ?: "gemma-4-E2B-cpu")

    private val _downloadProgress = MutableStateFlow<ModelDownloader.Progress?>(null)
    val downloadProgress: StateFlow<ModelDownloader.Progress?> = _downloadProgress.asStateFlow()
    private val llmAgent by lazy { LlmAgent(llm) }
    val chatEngine by lazy { getSharedChatEngine(app) }

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

    private var llmUnloadJob: kotlinx.coroutines.Job? = null
    
    /** Авто-вивантаження LLM через 3 хв неактивності. */
    fun startLlmAutoUnload() {
        llmUnloadJob?.cancel()
        llmUnloadJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000L) // перевіряємо кожну хвилину
                try {
                    if (llm.isReady && llm.lastUsedAt > 0) {
                        val idle = System.currentTimeMillis() - llm.lastUsedAt
                        if (idle > 3 * 60_000L) {
                            android.util.Log.e("LlmUnload", "Auto-unloading LLM after ${idle / 1000}s idle")
                            llm.close()
                            _llmReady.value = false
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
    }


    private fun checkOcrMlkitUnload() {
        try {
            val now = System.currentTimeMillis()
            val ocrLast = com.dynamicai.launcher.ocr.OcrUsageTracker.lastOcrAt
            val mlkitLast = com.dynamicai.launcher.ocr.OcrUsageTracker.lastMlkitAt
            
            if (ocrLast > 0 && now - ocrLast > 5 * 60_000L) {
                android.util.Log.e("OcrUnload", "OCR idle 5+ min")
            }
            if (mlkitLast > 0 && now - mlkitLast > 10 * 60_000L) {
                android.util.Log.e("MlkitUnload", "ML Kit idle 10+ min")
            }
        } catch (_: Throwable) {}
    }

    fun checkLlmReady() {
        // _llmReady = true ТІЛЬКИ якщо engine реально ініціалізований
        if (llm.isReady) {
            _llmReady.value = true
        } else {
            // Перевіряємо, чи файл моделі існує, але engine не створюємо
            val modelExists = downloader.isDownloaded
            _llmReady.value = false  // поки не ініціалізовано — false
            android.util.Log.e("LLM_DEBUG", "checkLlmReady: model file exists=$modelExists, engine ready=${llm.isReady}")
        }
    }

    /** Викликається лениво при першому реальному запиті LLM. */
    suspend fun ensureLlmLoaded(): Boolean {
        if (llm.isReady) return true

        // === MNN special case ===
        if (currentModelId.startsWith("mnn-")) {
            val modelName = currentModelId.removePrefix("mnn-")
            val mnnBase = java.io.File(getApplication<android.app.Application>().filesDir, "mnn_models")
            val mnnDir = File(mnnBase, modelName)
            android.util.Log.e("LLM_DEBUG", "MNN dir: ${mnnDir.absolutePath}, exists=${mnnDir.exists()}")
            if (!mnnDir.exists() || !mnnDir.isDirectory) {
                android.util.Log.e("LLM_DEBUG", "MNN dir not found")
                _llmReady.value = false
                return false
            }
            return try {
                val ok = llm.initialize(mnnDir)
                _llmReady.value = ok
                android.util.Log.e("LLM_DEBUG", "MNN init: $ok")
                ok
            } catch (t: Throwable) {
                android.util.Log.e("LLM_DEBUG", "MNN init failed: ${t.message}", t)
                _llmReady.value = false
                false
            }
        }
        
        // Спочатку завантажуємо нативну бібліотеку LLM
        if (!com.dynamicai.launcher.ocr.NativeLibManager.ensureLlmLoaded(getApplication())) {
            android.util.Log.e("LLM_DEBUG", "Failed to load LLM native lib")
            return false
        }
        android.util.Log.d("LLM_DEBUG", "Checking model file: ${downloader.modelFile.absolutePath}")
        android.util.Log.d("LLM_DEBUG", "Exists: ${downloader.modelFile.exists()}, Size: ${downloader.modelFile.length()} bytes")
        val model = com.dynamicai.launcher.llm.ModelRepository.AVAILABLE_MODELS.find { it.id == currentModelId }
            ?: com.dynamicai.launcher.llm.ModelRepository.AVAILABLE_MODELS.first()
        val modelFile = com.dynamicai.launcher.llm.ModelRepository.getModelFile(getApplication(), model)
        if (!modelFile.exists()) {
            android.util.Log.e("LLM_DEBUG", "Model NOT downloaded: ${model.name}")
            return false
        }
        return try {
            val ok = llm.initialize(modelFile)
            _llmReady.value = ok
            ok
        } catch (t: Throwable) {
            android.util.Log.e("LLM_DEBUG", "initialize failed: ${t.message}", t)
            _llmReady.value = false
            false
        }
    }

    private val _pendingChoice = MutableStateFlow<List<AppInfo>?>(null)
    val pendingChoice: StateFlow<List<AppInfo>?> = _pendingChoice.asStateFlow()

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private var pendingText: String = ""

    private val runtimePrefs = app.getSharedPreferences("dynamicai_runtime", android.content.Context.MODE_PRIVATE)
    private val _downloadTrigger = MutableStateFlow(0)
    val downloadTrigger: StateFlow<Int> = _downloadTrigger.asStateFlow()

    private val downloadReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            val action = intent?.action ?: return
            val modelId = intent.getStringExtra(ModelDownloadService.EXTRA_MODEL_ID)
            when (action) {
                ModelDownloadService.BROADCAST_COMPLETE -> {
                    android.util.Log.e("MNN_DL", "✅ Service reported complete: $modelId")
                    if (modelId != null) {
                        downloadingModelId = null
                        _downloadProgress.value = null
                        _downloadTrigger.value++
                        setCurrentModel(modelId)
                    }
                }
                ModelDownloadService.BROADCAST_ERROR -> {
                    val error = intent.getStringExtra("error") ?: "Unknown"
                    android.util.Log.e("MNN_DL", "❌ Service reported error: $error")
                    downloadingModelId = null
                    _downloadProgress.value = ModelDownloader.Progress.Error(error)
                    _downloadTrigger.value++
                }
            }
        }
    }

    private val pkgChangeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "apps_changed_at") {
            android.util.Log.e("AppListVM", "package changed, refreshing")
            refresh()
        }
    }

    init {
        try {
            val filter = android.content.IntentFilter().apply {
                addAction(ModelDownloadService.BROADCAST_COMPLETE)
                addAction(ModelDownloadService.BROADCAST_ERROR)
            }
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                app.registerReceiver(downloadReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
            } else {
                app.registerReceiver(downloadReceiver, filter)
            }
        } catch (t: Throwable) {
            android.util.Log.w("MNN_DL", "registerReceiver failed: ${t.message}")
        }
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
        startLlmAutoUnload()
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
                _response.value = "🧠 Завантажую LLM…"
                ensureLlmLoaded()
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
        try { getApplication<android.app.Application>().unregisterReceiver(downloadReceiver) } catch (_: Throwable) {}
        llm.close()
        tts.shutdown()
        super.onCleared()
    }

    fun setCurrentModel(modelId: String) {
        // SANITY CHECK: не дозволяємо активувати модель, якої немає на диску
        val isDownloaded = com.dynamicai.launcher.llm.ModelRepository.getDownloadedModels(getApplication()).any { it.id == modelId }
        if (!isDownloaded) {
            android.util.Log.e("LLM_DEBUG", "BLOCKED setCurrentModel: $modelId is not downloaded")
            _downloadProgress.value = com.dynamicai.launcher.llm.ModelDownloader.Progress.Error("Спочатку завантажте модель!")
            return
        }

        currentModelId = modelId
        prefs.edit().putString("current_model_id", modelId).apply()
        
        // Перезавантажуємо LLM з новою моделлю
        viewModelScope.launch {
            try {
                android.util.Log.e("LLM_DEBUG", "Перемикання на модель: $modelId")
                _llmReady.value = false          // скидаємо прапорець для UI
                llm.close()                       // звільняємо стару модель
                System.gc()                       // забираємо сміття
                kotlinx.coroutines.delay(200)     // коротка пауза для GC
                val ok = ensureLlmLoaded()
                android.util.Log.e("LLM_DEBUG", "Перемикання ${if (ok) "успішне" else "невдале"}: $modelId")
            } catch (t: Throwable) {
                android.util.Log.e("LLM_DEBUG", "setCurrentModel failed: ${t.message}", t)
                _llmReady.value = false
            }
        }
    }

    /** Завантажує будь-яку MNN-модель за ID (mnn-*) і активує її. */
    fun downloadMnnModelById(modelId: String) {
        val folderName = when (modelId) {
            "mnn-qwen3.5-0.8b" -> "qwen3.5-0.8b"
            "mnn-qwen3.5-2b" -> "qwen3.5-2b"
            "mnn-qwen3.5-4b" -> "qwen3.5-4b"
            "mnn-gemma-4-e2b" -> "gemma-4-e2b"
            "mnn-gemma-4-e4b" -> "gemma-4-e4b"
            "mnn-lfm2.5-1.2b" -> "lfm2.5-1.2b"
            "mnn-qwen3-0.6b" -> "qwen3-0.6b"
            "mnn-qwen3-1.7b" -> "qwen3-1.7b"
            "mnn-qwen2.5-1.5b" -> "qwen2.5-1.5b"
            else -> return
        }
        val repoName = when (modelId) {
            "mnn-qwen3.5-0.8b" -> "Qwen3.5-0.8B-MNN"
            "mnn-qwen3.5-2b" -> "Qwen3.5-2B-MNN"
            "mnn-qwen3.5-4b" -> "Qwen3.5-4B-MNN"
            "mnn-gemma-4-e2b" -> "gemma-4-E2B-it-MNN"
            "mnn-gemma-4-e4b" -> "gemma-4-E4B-it-MNN"
            "mnn-lfm2.5-1.2b" -> "LFM2.5-1.2B-Instruct-MNN"
            "mnn-qwen3-0.6b" -> "Qwen3-0.6B-MNN"
            "mnn-qwen3-1.7b" -> "Qwen3-1.7B-MNN"
            "mnn-qwen2.5-1.5b" -> "Qwen2.5-1.5B-Instruct-MNN"
            else -> return
        }

        downloadingModelId = modelId
        _downloadProgress.value = ModelDownloader.Progress.Downloading(0, 0, 0)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val baseDir = java.io.File(getApplication<android.app.Application>().filesDir, "mnn_models")
                val targetDir = java.io.File(baseDir, folderName)
                if (!targetDir.exists()) targetDir.mkdirs()

                targetDir.listFiles()?.forEach { f ->
                    if (f.name.endsWith(".opencl.weight") || f.name == "mnn_cachefile.bin") f.delete()
                }

                val isGemma = modelId.contains("gemma", ignoreCase = true)
                val isQwen35 = modelId.contains("qwen3.5", ignoreCase = true)
                val tokenizerName = if (isGemma) "tokenizer.mtok" else "tokenizer.txt"
                
                val files = linkedMapOf(
                    "config.json" to 50L,
                    "llm_config.json" to 50L,
                    "llm.mnn" to 100_000L,
                    tokenizerName to 100_000L,
                    "llm.mnn.weight" to 100_000_000L
                )
                
                // Додаємо мультимодальні файли, які MNN вимагає при старті
                if (isQwen35 || isGemma) {
                    files["visual.mnn"] = 100_000L
                    files["visual.mnn.weight"] = 1_000_000L
                }
                if (isGemma) {
                    files["audio.mnn"] = 100_000L
                }
                val correctUrl = "https://huggingface.co/taobao-mnn/$repoName/resolve/main"
                val downloader = ModelDownloader(getApplication())

                for ((file, minSize) in files) {
                    if (downloadingModelId != modelId) return@launch
                    val target = java.io.File(targetDir, file)
                    if (target.exists() && target.length() >= minSize) continue

                    val url = "$correctUrl/$file"
                    downloader.downloadUrl(url, "mnn_models/$folderName/$file").collect { progress ->
                        if (progress is ModelDownloader.Progress.Downloading) {
                            _downloadProgress.value = progress
                        } else if (progress is ModelDownloader.Progress.Error) {
                            throw Exception(progress.message)
                        }
                    }
                }
                _downloadProgress.value = null
                downloadingModelId = null
                
                // Вимикаємо зір для Qwen/Gemma, щоб MNN не вантажив visual.mnn
                try {
                    val configFile = java.io.File(targetDir, "config.json")
                    if (configFile.exists() && (modelId.contains("qwen3.5") || modelId.contains("gemma"))) {
                        val json = org.json.JSONObject(configFile.readText())
                        json.put("is_visual", false)
                        json.put("is_mrope", false)
                        configFile.writeText(json.toString())
                        android.util.Log.e("MNN_CONFIG", "Disabled vision in config.json for $modelId")
                    }
                    // Видаляємо jinja шаблон, який ламає C++ рушій
                    val llmConfigFile = java.io.File(targetDir, "llm_config.json")
                    if (llmConfigFile.exists()) {
                        val jsonLlm = org.json.JSONObject(llmConfigFile.readText())
                        if (jsonLlm.has("jinja")) {
                            jsonLlm.remove("jinja")
                        }
                        if (jsonLlm.has("system_prompt_template")) {
                            jsonLlm.remove("system_prompt_template")
                        }
                        if (jsonLlm.has("user_prompt_template")) {
                            jsonLlm.put("user_prompt_template", "<|im_start|>user\n%s<|im_end|>\n<|im_start|>assistant\n")
                        }
                        llmConfigFile.writeText(jsonLlm.toString())
                        android.util.Log.e("MNN_CONFIG", "Removed jinja and system prompt from llm_config.json for $modelId")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MNN_CONFIG", "Failed to patch config: ${e.message}")
                }
                
                _downloadTrigger.value = (_downloadTrigger.value ?: 0) + 1
            } catch (e: Exception) {
                _downloadProgress.value = ModelDownloader.Progress.Error(e.message ?: "Помилка")
                downloadingModelId = null
            }
        }
    }

    private fun cleanupOpenClWeights(targetDir: java.io.File) {
        try {
            targetDir.listFiles()?.forEach { f ->
                if (f.name.endsWith(".opencl.weight") || f.name == "mnn_cachefile.bin") {
                    val deleted = f.delete()
                    android.util.Log.e("MNN_CLEANUP", "${if (deleted) "🗑️" else "❌"} ${f.name} (${f.length()} bytes)")
                }
            }
        } catch (t: Throwable) {
            android.util.Log.w("MNN_CLEANUP", "cleanup failed: ${t.message}")
        }
    }

    fun clearMnnCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val baseDir = java.io.File(getApplication<android.app.Application>().filesDir, "mnn_models")
            var count = 0
            if (baseDir.exists()) {
                baseDir.walkTopDown().forEach { file ->
                    if (file.isFile && (file.name.endsWith(".opencl.weight") || file.name == "mnn_cachefile.bin")) {
                        if (file.delete()) count++
                    }
                }
            }
            android.util.Log.e("MNN_CLEANUP", "Manual cache clear: deleted $count files")
            withContext(Dispatchers.Main) {
                _downloadTrigger.value = (_downloadTrigger.value ?: 0) + 1
                _downloadProgress.value = null
            }
        }
    }


    private suspend fun downloadMnnModelByIdSuspending(repoName: String, folderName: String): Boolean =
        withContext(Dispatchers.IO) {
            val baseDir = java.io.File(getApplication<android.app.Application>().filesDir, "mnn_models")
            val targetDir = java.io.File(baseDir, folderName)
            if (!targetDir.exists()) targetDir.mkdirs()
            cleanupOpenClWeights(targetDir)

            // Обов'язкові файли (є у всіх MNN-моделей)
            val files = linkedMapOf(
                "config.json" to 50L,
                "llm_config.json" to 50L,
                "llm.mnn" to 100_000L,
                "tokenizer.txt" to 100_000L,
                "llm.mnn.weight" to 100_000_000L
            )
            // Опційні (llm.mnn.json є у Qwen2.5, але немає в Qwen3/3.5/Gemma4)
            val optionalFiles = listOf("llm.mnn.json")

            val baseUrl = "https://huggingface.co/taobao-mnn/$repoName/resolve/main"

            for ((file, minSize) in files) {
                val target = java.io.File(targetDir, file)
                if (target.exists() && target.length() >= minSize) {
                    android.util.Log.e("MNN_DL", "Skip $file (${target.length()} bytes)")
                    continue
                }
                val url = "$baseUrl/$file"
                android.util.Log.e("MNN_DL", "Downloading $file from $url")
                var ok = false
                downloader.downloadUrl(url, "mnn_models/$folderName/$file").collect { progress ->
                    when (progress) {
                        is ModelDownloader.Progress.Complete -> {
                            ok = true
                            android.util.Log.e("MNN_DL", "✅ $file done")
                        }
                        is ModelDownloader.Progress.Error ->
                            android.util.Log.e("MNN_DL", "❌ $file: ${progress.message}")
                        is ModelDownloader.Progress.Downloading ->
                            if (progress.percent % 20 == 0)
                                android.util.Log.e("MNN_DL", "⏳ $file: ${progress.percent}%")
                    }
                }
                if (!ok) return@withContext false
            }

            // Опційні файли (404 → пропускаємо)
            for (file in optionalFiles) {
                val target = java.io.File(targetDir, file)
                if (target.exists() && target.length() > 1000) {
                    android.util.Log.e("MNN_DL", "Skip optional $file")
                    continue
                }
                val url = "$baseUrl/$file"
                android.util.Log.e("MNN_DL", "Downloading optional $file")
                downloader.downloadUrl(url, "mnn_models/$folderName/$file").collect { progress ->
                    when (progress) {
                        is ModelDownloader.Progress.Complete ->
                            android.util.Log.e("MNN_DL", "✅ optional $file done")
                        is ModelDownloader.Progress.Error ->
                            android.util.Log.w("MNN_DL", "⚠️ optional $file: ${progress.message} (пропускаємо)")
                        is ModelDownloader.Progress.Downloading -> {}
                    }
                }
            }
            true
        }

    private suspend fun downloadMnnModelSuspending(): Boolean = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val baseDir = java.io.File(getApplication<android.app.Application>().filesDir, "mnn_models")
        val targetDir = java.io.File(baseDir, "qwen2.5-1.5b")
        if (!targetDir.exists()) targetDir.mkdirs()

        // Мінімальні очікувані розміри (щоб перекачати обірвані файли)
        val files = linkedMapOf(
            "config.json" to 100L,
            "llm_config.json" to 100L,
            "llm.mnn" to 500_000L,
            "llm.mnn.json" to 1_000_000L,
            "tokenizer.txt" to 1_000_000L,
            "llm.mnn.weight" to 800_000_000L   // останній — найбільший
        )
        val baseUrl = "https://huggingface.co/taobao-mnn/Qwen2.5-1.5B-Instruct-MNN/resolve/main"

        for ((file, minSize) in files) {
            val target = java.io.File(targetDir, file)
            if (target.exists() && target.length() >= minSize) {
                android.util.Log.e("MNN_DL", "Skip $file (${target.length()} bytes)")
                continue
            }
            val url = "$baseUrl/$file"
            android.util.Log.e("MNN_DL", "Downloading $file")
            var ok = false
            downloader.downloadUrl(url, "mnn_models/qwen2.5-1.5b/$file").collect { progress ->
                when (progress) {
                    is ModelDownloader.Progress.Complete -> {
                        ok = true
                        android.util.Log.e("MNN_DL", "✅ $file done (${target.length()} bytes)")
                    }
                    is ModelDownloader.Progress.Error ->
                        android.util.Log.e("MNN_DL", "❌ $file: ${progress.message}")
                    is ModelDownloader.Progress.Downloading ->
                        if (progress.percent % 10 == 0)
                            android.util.Log.e("MNN_DL", "⏳ $file: ${progress.percent}%")
                }
            }
            if (!ok) {
                android.util.Log.e("MNN_DL", "❌ Failed: $file")
                return@withContext false
            }
        }
        android.util.Log.e("MNN_DL", "🎉 All files OK in ${targetDir.absolutePath}")
        true
    }

    fun downloadModel(model: com.dynamicai.launcher.llm.LlmModel) {
        viewModelScope.launch {
            downloadingModelId = model.id
            _downloadProgress.value = null
            downloader.downloadUrl(model.url, model.fileName).collect { progress ->
                _downloadProgress.value = progress
                if (progress is com.dynamicai.launcher.llm.ModelDownloader.Progress.Complete) {
                    _downloadProgress.value = null
                    downloadingModelId = null
                    setCurrentModel(model.id)
                }
            }
        }
    }

    fun clearDownloadError() {
        _downloadProgress.value = null
        downloadingModelId = null
    }

    fun deleteModel(model: com.dynamicai.launcher.llm.LlmModel) {
        viewModelScope.launch {
            com.dynamicai.launcher.llm.ModelRepository.deleteModel(getApplication(), model)
            if (currentModelId == model.id) {
                currentModelId = "gemma-4-E2B-cpu" // Фолбек
                prefs.edit().putString("current_model_id", "gemma-4-E2B").apply()
            }
        }
    }

}
