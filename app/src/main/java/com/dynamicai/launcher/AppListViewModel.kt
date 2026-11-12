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
import com.dynamicai.launcher.suggestion.stableKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.dynamicai.launcher.command.Command
import com.dynamicai.launcher.data.AppsCache
import com.dynamicai.launcher.llm.LiteRtLlmEngine
import com.dynamicai.launcher.llm.LlmModel
import com.dynamicai.launcher.llm.ModelRepository
import com.dynamicai.launcher.macro.FocusModes
import com.dynamicai.launcher.ocr.NativeLibManager
import com.dynamicai.launcher.ocr.ScreenTranslateOverlay
import com.dynamicai.launcher.ocr.ScreenTranslator
import com.dynamicai.launcher.suggestion.AppUsage
import com.dynamicai.launcher.suggestion.BrowserBridge
import com.dynamicai.launcher.suggestion.ContactLauncher
import com.dynamicai.launcher.suggestion.SearchHistory
import com.dynamicai.launcher.suggestion.SuggestionRunner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex

class AppListViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        @Volatile private var dockShownOnce = false
        @Volatile private var sharedLlm: HybridLlmEngine? = null
        @Volatile private var sharedChatEngine: ChatEngine? = null
        @Volatile private var sharedDownloader: ModelDownloader? = null
        /** Усі операції load/close/switch моделі йдуть по черзі — паралельний load MNN валить процес нативно. */
        private val modelOpMutex = Mutex()

        fun getSharedLlm(context: Context): HybridLlmEngine =
            sharedLlm ?: synchronized(this) {
                sharedLlm ?: HybridLlmEngine(context.applicationContext).also { sharedLlm = it }
            }

        fun getSharedChatEngine(context: Context): ChatEngine =
            sharedChatEngine ?: synchronized(this) {
                sharedChatEngine ?: ChatEngine(
                    context.applicationContext,
                    getSharedLlm(context),
                    WebSearchRepository()
                ).also { sharedChatEngine = it }
            }

        fun getSharedDownloader(context: Context): ModelDownloader =
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
    private val appsCache = AppsCache(app)
    val shizukuManager = ShizukuManager(app)
    private val weatherRepo by lazy { WeatherRepository(app) }

    val llm by lazy { getSharedLlm(app) }
    val tts by lazy { TtsManager(app) }
    val downloader by lazy { getSharedDownloader(app) }
    private val prefs = getApplication<Application>().getSharedPreferences("llm_prefs", Context.MODE_PRIVATE)
    var downloadingModelId by mutableStateOf<String?>(null)
    var currentModelId by mutableStateOf(prefs.getString("current_model_id", "gemma-4-E2B-cpu") ?: "gemma-4-E2B-cpu")

    private val _downloadProgress = MutableStateFlow<ModelDownloader.Progress?>(null)
    val downloadProgress: StateFlow<ModelDownloader.Progress?> = _downloadProgress.asStateFlow()
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

    // Статичний прапорець: активність може перестворюватись системою, але анімація появи — лише один раз за процес
    private val _dockAppeared = MutableStateFlow(dockShownOnce)
    val dockAppeared: StateFlow<Boolean> = _dockAppeared.asStateFlow()
    fun markDockAppeared() { dockShownOnce = true; _dockAppeared.value = true }

    private val _openNotesRequest = MutableStateFlow(false)
    val openNotesRequest: StateFlow<Boolean> = _openNotesRequest.asStateFlow()

    fun clearOpenNotes() { _openNotesRequest.value = false }

    private val _llmReady = MutableStateFlow(false)
    val llmReady: StateFlow<Boolean> = _llmReady.asStateFlow()

    // Публічний, але НЕ торкається llm поки не спитають

    private var llmUnloadJob: Job? = null
    
    /** Авто-вивантаження LLM через 3 хв неактивності. */
    fun startLlmAutoUnload() {
        llmUnloadJob?.cancel()
        llmUnloadJob = viewModelScope.launch {
            while (true) {
                delay(20_000L)
                try {
                    // Рушій спільний для всіх екранів, а прапорець — у кожної ViewModel окремо: синхронізуємо з реальним станом
                    if (_llmReady.value != llm.isReady && !_llmRestarting.value) _llmReady.value = llm.isReady
                    if (llm.isReady && llm.lastUsedAt > 0) {
                        val idle = System.currentTimeMillis() - llm.lastUsedAt
                        val limitMin = settings.value.llmUnloadMinutes
                        if (limitMin > 0 && idle > limitMin * 60_000L) {
                            Log.e("LlmUnload", "Auto-unloading LLM after ${idle / 1000}s idle")
                            llm.close()
                            _llmReady.value = false
                            MemLog.note(getApplication(), "LLM_UNLOAD idle=${idle / 1000}s")
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
    }


    fun checkLlmReady() {
        // _llmReady = true ТІЛЬКИ якщо engine реально ініціалізований
        if (llm.isReady) {
            _llmReady.value = true
        } else if (!_llmRestarting.value) {
            _llmReady.value = false
        }
    }

    private val _llmRestarting = MutableStateFlow(false)
    val llmRestarting: StateFlow<Boolean> = _llmRestarting.asStateFlow()

    /** Короткий статус для UI: результат останнього перезапуску рушія. */
    private val _llmStatusText = MutableStateFlow("")
    val llmStatusText: StateFlow<String> = _llmStatusText.asStateFlow()

    /**
     * Закриває рушій і завантажує його знову з поточними налаштуваннями (backend, точність, MTP...).
     * @param clearGpuCache також очистити кеш GPU (папки litert_* у cacheDir)
     */
    fun restartLlmEngine(clearGpuCache: Boolean = false) {
        if (_llmRestarting.value) return
        viewModelScope.launch {
            _llmRestarting.value = true
            _llmStatusText.value = "Закриття рушія…"
            _llmReady.value = false
            try {
                val ok = modelOpMutex.withLock {
                    withContext(Dispatchers.IO) {
                        try { llm.close() } catch (t: Throwable) {
                            Log.e("LLM_DEBUG", "close failed: ${t.message}")
                        }
                        if (clearGpuCache) {
                            val n = LiteRtLlmEngine.clearGpuCache(getApplication())
                            _llmStatusText.value = "Кеш GPU очищено ($n)"
                        }
                        System.gc()
                    }
                    delay(300)
                    _llmStatusText.value = "Завантаження моделі…"
                    ensureLlmLoadedLocked()
                }
                _llmStatusText.value = if (ok) "✓ Рушій перезапущено" else "✗ Не вдалося завантажити (див. logcat)"
            } catch (t: Throwable) {
                Log.e("LLM_DEBUG", "restartLlmEngine failed: ${t.message}", t)
                _llmStatusText.value = "✗ Помилка: ${t.message}"
                _llmReady.value = false
            } finally {
                _llmRestarting.value = false
            }
        }
    }

    /** Викликається лениво при першому реальному запиті LLM. */
    suspend fun ensureLlmLoaded(): Boolean = modelOpMutex.withLock { ensureLlmLoadedLocked() }

    private suspend fun ensureLlmLoadedLocked(): Boolean {
        if (llm.isReady) return true

        // === MNN special case ===
        if (currentModelId.startsWith("mnn-")) {
            val modelName = currentModelId.removePrefix("mnn-")
            val mnnBase = File(getApplication<Application>().filesDir, "mnn_models")
            val mnnDir = File(mnnBase, modelName)
            Log.e("LLM_DEBUG", "MNN dir: ${mnnDir.absolutePath}, exists=${mnnDir.exists()}")
            if (!mnnDir.exists() || !mnnDir.isDirectory) {
                Log.e("LLM_DEBUG", "MNN dir not found")
                _llmReady.value = false
                return false
            }
            return try {
                val ok = llm.initialize(mnnDir)
                _llmReady.value = ok
                Log.e("LLM_DEBUG", "MNN init: $ok")
                MemLog.note(getApplication(), "LLM_LOAD ok=$ok")
                ok
            } catch (t: Throwable) {
                Log.e("LLM_DEBUG", "MNN init failed: ${t.message}", t)
                _llmReady.value = false
                false
            }
        }
        
        // Спочатку завантажуємо нативну бібліотеку LLM
        if (!NativeLibManager.ensureLlmLoaded(getApplication())) {
            Log.e("LLM_DEBUG", "Failed to load LLM native lib")
            return false
        }
        Log.d("LLM_DEBUG", "Checking model file: ${downloader.modelFile.absolutePath}")
        Log.d("LLM_DEBUG", "Exists: ${downloader.modelFile.exists()}, Size: ${downloader.modelFile.length()} bytes")
        val model = ModelRepository.AVAILABLE_MODELS.find { it.id == currentModelId }
            ?: ModelRepository.AVAILABLE_MODELS.first()
        val modelFile = ModelRepository.getModelFile(getApplication(), model)
        if (!modelFile.exists()) {
            Log.e("LLM_DEBUG", "Model NOT downloaded: ${model.name}")
            return false
        }
        return try {
            val ok = llm.initialize(modelFile)
            _llmReady.value = ok
            MemLog.note(getApplication(), "LLM_LOAD ok=$ok")
            ok
        } catch (t: Throwable) {
            Log.e("LLM_DEBUG", "initialize failed: ${t.message}", t)
            _llmReady.value = false
            false
        }
    }

    private val _pendingChoice = MutableStateFlow<List<AppInfo>?>(null)
    val pendingChoice: StateFlow<List<AppInfo>?> = _pendingChoice.asStateFlow()

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private var pendingText: String = ""

    private val runtimePrefs = app.getSharedPreferences("dynamicai_runtime", Context.MODE_PRIVATE)
    private val _downloadTrigger = MutableStateFlow(0)
    val downloadTrigger: StateFlow<Int> = _downloadTrigger.asStateFlow()

    /** ID моделей, які реально є на диску (повністю). Оновлюється при кожному `_downloadTrigger`. */
    private val _downloadedModelIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedModelIds: StateFlow<Set<String>> = _downloadedModelIds.asStateFlow()

    fun refreshDownloadedModels() {
        viewModelScope.launch(Dispatchers.IO) {
            _downloadedModelIds.value = ModelRepository
                .getDownloadedModels(getApplication()).map { it.id }.toSet()
        }
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val modelId = intent.getStringExtra(ModelDownloadService.EXTRA_MODEL_ID)
            when (action) {
                ModelDownloadService.BROADCAST_COMPLETE -> {
                    Log.e("MNN_DL", "✅ Service reported complete: $modelId")
                    if (modelId != null) {
                        downloadingModelId = null
                        _downloadProgress.value = null
                        _downloadTrigger.value++
                        setCurrentModel(modelId)
                    }
                }
                ModelDownloadService.BROADCAST_ERROR -> {
                    val error = intent.getStringExtra("error") ?: "Unknown"
                    Log.e("MNN_DL", "❌ Service reported error: $error")
                    downloadingModelId = null
                    _downloadProgress.value = ModelDownloader.Progress.Error(error)
                    _downloadTrigger.value++
                }
            }
        }
    }

    private val pkgChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "apps_changed_at") {
            Log.e("AppListVM", "package changed, refreshing")
            refresh()
        }
    }

    init {
        // Список завантажених моделей оновлюється автоматично після будь-якої зміни (завантаження, видалення, очищення)
        viewModelScope.launch { downloadTrigger.collect { refreshDownloadedModels() } }
        try {
            val filter = IntentFilter().apply {
                addAction(ModelDownloadService.BROADCAST_COMPLETE)
                addAction(ModelDownloadService.BROADCAST_ERROR)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                app.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                app.registerReceiver(downloadReceiver, filter)
            }
        } catch (t: Throwable) {
            Log.w("MNN_DL", "registerReceiver failed: ${t.message}")
        }
        runtimePrefs.registerOnSharedPreferenceChangeListener(pkgChangeListener)
        Log.d("APPINIT", "init start, RSS before everything")
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
        FocusModes.init(app)
        viewModelScope.launch { FocusModes.active.collect { updateQuery("") } }
        // Фонове завантаження моделі: перший запит не чекатиме ~15 с на старт рушія
        // Усі питання йдуть у хмару — локальну модель наперед не вантажимо
        if (settingsStorage.load().llmPreload && !chatEngine.cloudOnly()) {
            viewModelScope.launch {
                delay(4000)
                try { ensureLlmLoaded() } catch (t: Throwable) { Log.e("LLM_DEBUG", "preload failed: ${t.message}") }
            }
        }
        Log.d("APPINIT", "init done")
    }

    private var lastPackageSig = 0

    fun refreshIfStale() {
        val last = runtimePrefs.getLong("apps_refreshed_at", 0L)
        if (System.currentTimeMillis() - last > 60_000L) {
            // Повне перезавантаження (усі іконки) — лише якщо набір додатків справді змінився
            viewModelScope.launch {
                val sig = try { repo.packageSignature() } catch (_: Throwable) { 0 }
                if (sig != 0 && sig == lastPackageSig && _apps.value.isNotEmpty()) {
                    runtimePrefs.edit().putLong("apps_refreshed_at", System.currentTimeMillis()).apply()
                } else {
                    lastPackageSig = sig
                    refresh()
                }
            }
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

    private var queryJob: Job? = null
    private var lastQueryText = ""

    fun updateQuery(query: String) {
        if (query.isNotBlank()) lastQueryText = query
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            // Затримка 70 мс при наборі + обчислення поза головним потоком (раніше — на кожен символ у Main)
            if (query.isNotBlank()) delay(35)
            val focusHidden = FocusModes.hiddenFor(FocusModes.active.value, _apps.value)
            val visibleApps = _apps.value.filter {
                // Додатки з папок теж шукаються й показуються у списку — приховані лише ті, що в «Прихованих»/режимі фокусу
                it.packageName !in _hidden.value && it.packageName !in focusHidden
            }
            val contactsNow = _contacts.value
            val flash = executor.flashlightOn.value
            _suggestions.value = withContext(Dispatchers.Default) {
                suggestionProvider.build(query, visibleApps, contactsNow, flash)
            }
            // Підказки пошуку з інтернету — окремо й пізніше, щоб не гальмувати список (лише якщо ввімкнено)
            val q = query.trim()
            if (q.length >= 3 && _settings.value.webSuggest) {
                delay(250)
                val web = withContext(Dispatchers.IO) {
                    BrowserBridge.webSuggestions(q)
                }
                if (web.isNotEmpty()) {
                    val extra = web.map {
                        Suggestion.Extra("web", "🔎", it, "Підказка пошуку",
                            BrowserBridge.searchUrl(it), 11)
                    }
                    _suggestions.value = (_suggestions.value + extra)
                        .distinctBy { it.stableKey() }
                        .sortedByDescending { it.score }
                }
            }
        }
    }

    fun loadContacts() {
        viewModelScope.launch {
            _contacts.value = contactRepo.loadAll()
            updateQuery("")
        }
    }

    fun launch(pkg: String, smooth: Boolean = false) {
        try { AppUsage.bump(getApplication(), pkg) } catch (_: Throwable) {}
        repo.launchApp(pkg, smooth)
    }

    fun toggleDock(pkg: String) {
        dockStorage.toggle(pkg)
        _dockPkgs.value = dockStorage.getDockApps()
        refreshWeather()
    }

    fun moveDock(id: String, delta: Int) {
        dockStorage.move(id, delta)
        _dockPkgs.value = dockStorage.getDockApps()
    }

    fun executeSuggestion(s: Suggestion) {
        if (lastQueryText.isNotBlank()) {
            SearchHistory.add(getApplication(), lastQueryText)
            lastQueryText = ""
        }
        when (s) {
            is Suggestion.App -> launch(s.app.packageName)
            is Suggestion.Action -> {
                val result = executor.execute(s.command, _apps.value)
                handleResult(result, s.label)
            }
            is Suggestion.Setting -> {
                val result = executor.execute(
                    Command.OpenSettings(s.route),
                    _apps.value
                )
                handleResult(result, s.label)
            }
            is Suggestion.Contact -> executeContact(s)
            is Suggestion.Extra -> {
                if (!SuggestionRunner.run(getApplication(), s) && s.kind == "note") {
                    _openNotesRequest.value = true
                }
            }
        }
    }

    private fun executeContact(s: Suggestion.Contact) {
        val result = ContactLauncher.run(
            getApplication(), s, _contacts.value, _apps.value, executor
        )
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
            _folders.value = folderStorage.get()
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

    /**
     * Автопапки за категоріями (Ігри, Соц. мережі, Медіа, Інструменти, Навігація): беруться лише
     * застосунки, які ще не лежать у папках і не приховані; категорії з менш ніж 3 застосунками пропускаються.
     * Якщо папка з такою назвою вже є — до неї дописуються нові застосунки.
     */
    fun createAutoFolders() {
        val labels = linkedMapOf(
            "games" to "Ігри", "social" to "Соц. мережі", "media" to "Медіа",
            "tools" to "Інструменти", "maps" to "Навігація"
        )
        val inFolders = _folders.value.flatMap { it.packageNames }.toSet()
        val free = _apps.value.filter { it.packageName !in _hidden.value && it.packageName !in inFolders }
        var created = 0
        var filled = 0
        try {
            for ((cat, name) in labels) {
                val pkgs = free.filter { it.category == cat }.map { it.packageName }
                val existing = _folders.value.firstOrNull { it.name == name }
                if (existing != null) {
                    pkgs.forEach { folderStorage.addApp(existing.id, it) }
                    filled += pkgs.size
                } else if (pkgs.size >= 3) {
                    folderStorage.create(name, pkgs); created++
                }
            }
            _folders.value = folderStorage.get()
            updateQuery("")
            _response.value = if (created + filled == 0) "Немає категорій з 3+ вільними застосунками"
                else "Створено папок: $created, додано застосунків: $filled"
        } catch (t: Throwable) {
            _response.value = "Помилка автопапок: ${t.message}"
        }
    }

    fun folderOf(pkg: String): Folder? = folderStorage.folderOf(pkg)

    private var weatherJob: Job? = null

    fun startWeatherAutoRefresh(screenOnCheck: () -> Boolean) {
        weatherJob?.cancel()
        weatherRepo.loadCachedWeather()?.let { _weather.value = it }
        weatherJob = viewModelScope.launch {
            // Перше завантаження
            _weather.value = weatherRepo.fetch()

            while (true) {
                delay(60 * 60 * 1000L)  // 1 година
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

    fun isCommand(text: String) = parser.parse(text) !is Command.Raw

    fun executeCommand(text: String) {
        if (text.isBlank()) return
        SearchHistory.add(getApplication(), text)
        val cmd = parser.parse(text)

        // Питання → ChatEngine (LLM + Web Search)
        if (cmd is Command.AskQuestion) {
            viewModelScope.launch {
                val answer = if (chatEngine.cloudPreferred(cmd.query)) {
                    _response.value = "🔍 Шукаю…"
                    val r = chatEngine.simpleAnswer(cmd.query, onStatus = { st -> _response.value = "🔍 $st" })
                    chatEngine.takeSources()   // у цьому вікні джерела не показуємо — скидаємо
                    if (r == ChatEngine.CLOUD_FAILED) {
                        _response.value = "🧠 Завантажую LLM…"
                        ensureLlmLoaded()
                        chatEngine.answer(cmd.query)
                    } else r
                } else {
                    _response.value = "🧠 Завантажую LLM…"
                    ensureLlmLoaded()
                    _response.value = "🔍 Шукаю…"
                    chatEngine.answer(cmd.query)
                }
                _response.value = answer
                val settings = settingsStorage.load()
                if (settings.useTts) {
                    tts.speak(answer)
                }
            }
            return
        }

        if (cmd is Command.Raw) {
            val asApp = executor.resolveOpenApp(text, _apps.value)
            if (asApp is CommandResult.Success || asApp is CommandResult.ChooseApp) {
                handleResult(asApp, text)
                return
            }
            // Перевіряємо toggle + готовність LLM (через файл, БЕЗ engine)
            val settings = settingsStorage.load()
            val cloudAgent = chatEngine.cloudAgentEligible(text)
            val localOk = settings.useLlm && downloader.isDownloaded && !chatEngine.cloudOnly()
            if (!cloudAgent && !localOk) {
                _response.value = "Не розумію команду: \"$text\""
                return
            }

            viewModelScope.launch {
                if (cloudAgent) {
                    _response.value = "☁️ Виконую…"
                    val r = chatEngine.cloudAgent(text, "")
                    if (r != ChatEngine.CLOUD_FAILED) {
                        _response.value = r
                        if (settings.useTts) tts.speak(r)
                        return@launch
                    }
                    if (!localOk) {
                        _response.value = "Хмара недоступна. Не розумію команду: \"$text\""
                        return@launch
                    }
                }
                _response.value = "🧠 Думаю… (5-15 сек)"
                try {
                    ensureLlmLoaded()
                    // Той самий агент і той самий набір інструментів, що й у чаті та в хмарному режимі
                    val r = chatEngine.answerWithAgent(text)
                    _response.value = r
                    if (!r.startsWith("Помилка") && r != "LLM не активна") {
                        historyStorage.add(text)
                        _history.value = historyStorage.get()
                        if (settings.useTts) tts.speak(r)
                    }
                } catch (t: Throwable) {
                    _response.value = "Помилка LLM: ${t.message}"
                }
            }
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
                val translator = ScreenTranslator(getApplication(), llm)
                val blocks = translator.captureAndTranslate(
                    ocrLangs = "ukr+eng+pol",
                    targetLang = targetLang
                ) { stage ->
                    _response.value = "🌐 $stage"
                }
                if (blocks.isEmpty()) {
                    _response.value = "Не вдалось розпізнати текст (перевір чи завантажено OCR-мови в налаштуваннях)"
                    return@launch
                }
                ScreenTranslateOverlay.show(
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
        try { getApplication<Application>().unregisterReceiver(downloadReceiver) } catch (_: Throwable) {}
        llm.close()
        tts.shutdown()
        super.onCleared()
    }

    fun setCurrentModel(modelId: String) {
        // SANITY CHECK: не дозволяємо активувати модель, якої немає на диску
        val isDownloaded = ModelRepository.getDownloadedModels(getApplication()).any { it.id == modelId }
        if (!isDownloaded) {
            Log.e("LLM_DEBUG", "BLOCKED setCurrentModel: $modelId is not downloaded")
            _downloadProgress.value = ModelDownloader.Progress.Error("Спочатку завантажте модель!")
            return
        }

        currentModelId = modelId
        prefs.edit().putString("current_model_id", modelId).apply()
        
        // Перезавантажуємо LLM з новою моделлю
        viewModelScope.launch {
            try {
                Log.e("LLM_DEBUG", "Перемикання на модель: $modelId")
                _llmReady.value = false          // скидаємо прапорець для UI
                val ok = modelOpMutex.withLock {
                    // close() чекає кінця поточної генерації; робимо це поза головним потоком
                    withContext(Dispatchers.IO) { llm.close(); System.gc() }
                    delay(300)
                    ensureLlmLoadedLocked()
                }
                Log.e("LLM_DEBUG", "Перемикання ${if (ok) "успішне" else "невдале"}: $modelId")
            } catch (t: Throwable) {
                Log.e("LLM_DEBUG", "setCurrentModel failed: ${t.message}", t)
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
                val baseDir = File(getApplication<Application>().filesDir, "mnn_models")
                val targetDir = File(baseDir, folderName)
                if (!targetDir.exists()) targetDir.mkdirs()

                targetDir.listFiles()?.forEach { f ->
                    if (f.name.endsWith(".opencl.weight") || f.name.endsWith(".static") || f.name == "mnn_cachefile.bin") f.delete()
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
                    files["audio.mnn.weight"] = 100_000_000L
                    // Gemma 4: per-layer embeddings (1.37 ГБ) — без цього файлу MNN падає нативно
                    files["ple_embeddings_int4.bin"] = 1_000_000_000L
                }
                val correctUrl = "https://huggingface.co/taobao-mnn/$repoName/resolve/main"
                val downloader = ModelDownloader(getApplication())

                for ((file, minSize) in files) {
                    if (downloadingModelId != modelId) return@launch
                    val target = File(targetDir, file)
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
                
                // Конфіги моделі (config.json / llm_config.json) НЕ патчимо: оригінал Qwen3.5 має
                // is_mrope=true і jinja-шаблон з eos/bos. Примусове is_mrope=false та видалення jinja
                // ламали генерацію (1 токен / 0 токенів). Зір вимикається в runtime-конфігу
                // (LlmSession.load -> "is_visual":false), файли моделі лишаються оригінальними.
                Log.e("MNN_CONFIG", "Model configs left untouched for $modelId")

                _downloadTrigger.value = (_downloadTrigger.value ?: 0) + 1
            } catch (e: Exception) {
                _downloadProgress.value = ModelDownloader.Progress.Error(e.message ?: "Помилка")
                downloadingModelId = null
            }
        }
    }

    private fun cleanupOpenClWeights(targetDir: File) {
        try {
            targetDir.listFiles()?.forEach { f ->
                if (f.name.endsWith(".opencl.weight") || f.name == "mnn_cachefile.bin") {
                    val deleted = f.delete()
                    Log.e("MNN_CLEANUP", "${if (deleted) "🗑️" else "❌"} ${f.name} (${f.length()} bytes)")
                }
            }
        } catch (t: Throwable) {
            Log.w("MNN_CLEANUP", "cleanup failed: ${t.message}")
        }
    }

    fun clearMnnCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val baseDir = File(getApplication<Application>().filesDir, "mnn_models")
            var count = 0
            if (baseDir.exists()) {
                baseDir.walkTopDown().forEach { file ->
                    if (file.isFile && (file.name.endsWith(".opencl.weight") || file.name == "mnn_cachefile.bin")) {
                        if (file.delete()) count++
                    }
                }
            }
            Log.e("MNN_CLEANUP", "Manual cache clear: deleted $count files")
            withContext(Dispatchers.Main) {
                _downloadTrigger.value = (_downloadTrigger.value ?: 0) + 1
                _downloadProgress.value = null
            }
        }
    }


    fun downloadModel(model: LlmModel) {
        viewModelScope.launch {
            downloadingModelId = model.id
            _downloadProgress.value = null
            downloader.downloadUrl(model.url, model.fileName).collect { progress ->
                _downloadProgress.value = progress
                if (progress is ModelDownloader.Progress.Complete) {
                    _downloadProgress.value = null
                    downloadingModelId = null
                    _downloadTrigger.value++
                    setCurrentModel(model.id)
                }
            }
        }
    }

    fun clearDownloadError() {
        _downloadProgress.value = null
        downloadingModelId = null
    }

    fun deleteModel(model: LlmModel) {
        // Не видаляємо модель, яка зараз завантажується
        if (downloadingModelId == model.id) return
        viewModelScope.launch {
            val wasCurrent = currentModelId == model.id
            if (wasCurrent) {
                // Звільняємо файли моделі перед видаленням
                _llmReady.value = false
                try { llm.close() } catch (_: Throwable) {}
            }
            ModelRepository.deleteModel(getApplication(), model)
            if (wasCurrent) {
                currentModelId = "gemma-4-E2B-cpu" // Фолбек
                prefs.edit().putString("current_model_id", "gemma-4-E2B-cpu").apply()
            }
            // Одразу оновлюємо UI налаштувань
            _downloadTrigger.value++
        }
    }

}
