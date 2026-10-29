package com.dynamicai.launcher.llm

import android.content.Context
import android.util.Log
import com.alibaba.mnnllm.android.llm.GenerateProgressListener
import com.alibaba.mnnllm.android.llm.LlmSession
import com.dynamicai.launcher.data.LauncherSettings
import com.dynamicai.launcher.data.SettingsStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.concurrent.withLock

/**
 * Реалізація LlmEngine через MNN (libmnnllmapp.so + LlmSession).
 * Модель — це ПАПКА з файлами: llm.mnn, llm.mnn.json, llm.mnn.weight, llm_config.json, config.json, tokenizer.txt.
 */
class MnnLlmEngine(private val context: Context) : LlmEngine {
    override val isMnn: Boolean get() = true

    companion object {
        private const val TAG = "MnnLlmEngine"

        /**
         * НЕ обгортаємо промпт у chat template — MNN сам застосовує `prompt_template`
         * з llm_config.json. Просто додаємо /no_think для Qwen3 thinking mode.
         */
        /** Qwen3 з /no_think повертає порожній блок <think></think> — прибираємо його (інакше він потрапляє в чат і в озвучення). */
        private val THINK_REGEX = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)
        internal fun stripThinking(text: String): String {
            var t = THINK_REGEX.replace(text, "")
            // Обірваний блок без закриття (досягнуто ліміт токенів у "роздумах")
            val open = t.indexOf("<think>")
            if (open >= 0) t = t.substring(0, open)
            return t.trim()
        }

        private fun formatPrompt(prompt: String, modelId: String): String {
            return if (modelId.contains("qwen3-", ignoreCase = true)) {
                // /no_think для вимкнення thinking mode Qwen3
                "$prompt\n/no_think"
            } else {
                prompt
            }
        }
    }

    private var session: LlmSession? = null

    /** Захищає нативну сесію: release() під час generate() або паралельний load() = SIGSEGV. */
    private val sessionLock = java.util.concurrent.locks.ReentrantLock()

    @Volatile
    var lastUsedAt: Long = 0L
        private set

    @Volatile
    var lastError: String = ""

    private var currentSettings: LauncherSettings = LauncherSettings()
    private var currentModelName: String = ""
    private val initializeMutex = kotlinx.coroutines.sync.Mutex()

    override val isReady: Boolean get() = session?.isReady() == true

    /**
     * @param modelDir папка з моделлю (містить llm.mnn тощо)
     */
    suspend fun initialize(modelDir: File): Boolean = withContext(Dispatchers.IO) {
        initializeMutex.withLock {
            _initializeInternal(modelDir)
        }
    }

    private suspend fun _initializeInternal(modelDir: File): Boolean = withContext(Dispatchers.IO) {
        if (isReady && currentModelName == modelDir.name) return@withContext true
        
        // Якщо завантажена інша модель — примусово звільняємо її
        if (isReady && currentModelName != modelDir.name) {
            Log.e(TAG, "Switching model: $currentModelName -> ${modelDir.name}. Releasing old session.")
            sessionLock.withLock {
                try { session?.release() } catch (_: Throwable) {}
                session = null
            }
            System.gc()
        }

        if (!modelDir.exists() || !modelDir.isDirectory) {
            lastError = "Папка моделі не знайдена: ${modelDir.absolutePath}"
            Log.e(TAG, lastError)
            return@withContext false
        }
        if (!File(modelDir, "llm.mnn").exists() || !File(modelDir, "llm.mnn.weight").exists()) {
            lastError = "У папці немає llm.mnn / llm.mnn.weight"
            Log.e(TAG, lastError)
            return@withContext false
        }
        if (modelDir.name.contains("gemma", ignoreCase = true)) {
            val missing = listOf("ple_embeddings_int4.bin", "audio.mnn.weight", "visual.mnn.weight")
                .filter { !File(modelDir, it).exists() }
            if (missing.isNotEmpty()) {
                lastError = "Gemma: бракує файлів $missing — завантажте модель повторно"
                Log.e(TAG, lastError)
                return@withContext false
            }
        }

        // Нативна бібліотека не ловить винятків nlohmann::json: поле неправильного типу = SIGABRT і краш усього лаунчера.
        // Тому спершу перевіряємо конфіги тут і повертаємо зрозумілу помилку.
        validateConfigs(modelDir)?.let { err ->
            lastError = err
            Log.e(TAG, "Config check failed: $err")
            return@withContext false
        }

        // Логуємо вміст папки моделі
        Log.e(TAG, "=== Файли в ${modelDir.absolutePath} ===")
        try {
            modelDir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                Log.e(TAG, "  ${f.name}: ${f.length()} bytes")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "listFiles failed: ${t.message}")
        }

        // Необов'язкові перевизначення конфігу для експериментів без перезбірки:
        //   adb shell "echo '{...}' > /sdcard/Android/data/<pkg>/files/mnn_overrides.json"
        val overrides: org.json.JSONObject? = try {
            val f = File(context.getExternalFilesDir(null), "mnn_overrides.json")
            if (f.exists()) org.json.JSONObject(f.readText()).also {
                Log.e(TAG, "mnn_overrides.json: $it")
            } else null
        } catch (t: Throwable) {
            Log.e(TAG, "mnn_overrides.json read failed: ${t.message}")
            null
        }

        // OpenCL-кеш (mnn_cachefile.bin / *.opencl.weight) за замовчуванням ЗБЕРІГАЄМО — він прискорює prefill.
        // Видаляємо лише якщо файл > 256 МБ. "clean_cache": true — видаляти завжди, false — ніколи.
        // УВАГА: файли кешу працюють парою (X_0.opencl.weight + X_sync.opencl.weight). Видаляти їх можна
        // ТІЛЬКИ разом, інакше MNN вважає кеш валідним і мапить порожній файл → краш у Llm::forwardRaw.
        try {
            val mode = overrides?.opt("clean_cache") as? Boolean
            val all = modelDir.listFiles().orEmpty()
            // *.static — кеш mmap (пара X_0.static + X_sync.static); після краша лишається 1 ГБ + 0 байт → наступний запуск падає
            val ocl = all.filter { it.name.endsWith(".opencl.weight") || it.name.endsWith(".static") }
            val tune = all.filter { it.name == "mnn_cachefile.bin" }
            val cacheFiles = ocl + tune
            val hasWeight = ocl.any { !it.name.contains("_sync") && it.length() > 0 }
            val hasSync = ocl.any { it.name.contains("_sync") }
            // Пара неповна (маркер без ваг АБО ваги без маркера — залишок після краша) → кеш невалідний
            val orphanSync = hasWeight != hasSync
            // За замовчуванням ЧИСТИМО кеш при кожному запуску (перевірено: працює стабільно, завантаження 1–3 с).
            // "clean_cache": false в mnn_overrides.json — зберігати кеш (прискорює завантаження, але ризик після краша).
            val clean = mode != false || orphanSync
            Log.e("MNN_CLEANUP", "cache files=${cacheFiles.map { "${it.name}:${it.length()}" }} orphan=$orphanSync clean=$clean")
            if (clean) {
                cacheFiles.forEach { f ->
                    val len = f.length()
                    val deleted = f.delete()
                    Log.e("MNN_CLEANUP", "🗑️ ${if (deleted) "Deleted" else "Failed to delete"} ${f.name} ($len bytes)")
                }
            }
        } catch (t: Throwable) {
            Log.e("MNN_CLEANUP", "Cleanup failed: ${t.message}")
        }

        // libmnnllmapp.so шукає лише tokenizer.txt, а Gemma постачається як tokenizer.mtok.
        // Експеримент: копія під ім'ям tokenizer.txt (спрацює, якщо libMNN визначає формат за вмістом).
        try {
            val txt = File(modelDir, "tokenizer.txt")
            val mtok = File(modelDir, "tokenizer.mtok")
            if (!txt.exists() && mtok.exists()) {
                mtok.copyTo(txt)
                Log.e(TAG, "tokenizer.mtok → tokenizer.txt copied (${txt.length()} bytes)")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "tokenizer copy failed: ${t.message}")
        }

        // Звільняємо попередній engine
        sessionLock.withLock {
            try { session?.release() } catch (_: Throwable) {}
            session = null
        }
        System.gc()

        currentSettings = SettingsStorage(context).load()
        currentModelName = modelDir.name
        Log.e(TAG, "currentModelName=$currentModelName")

        // Мапимо вибір з UI → MNN backend_type + fallback
        val isGemma = modelDir.name.contains("gemma", ignoreCase = true)
        val backends = if (isGemma) {
            // Gemma 4 на OpenCL не працює навіть в офіційному MNN Chat 0.8.3 → лише CPU
            Log.e(TAG, "ℹ️ Gemma → backend cpu (OpenCL не підтримується в цій збірці MNN)")
            listOf("cpu")
        } else when (currentSettings.llmBackend) {
            "gpu" -> listOf("opencl")
            "cpu" -> listOf("cpu")
            // QNN видалено (не працює на SM8550) — NPU fallback на auto
            "npu" -> listOf("opencl", "cpu")
            "auto", "" -> listOf("opencl", "cpu")
            else -> listOf("opencl", "cpu")
        }
        Log.e(TAG, "initialize: dir=${modelDir.absolutePath}, backends=$backends")

        for (backend in backends) {
            Log.e(TAG, "Спроба backend=$backend")
            try {
                val s = LlmSession(
                    modelId = modelDir.name,
                    configDir = modelDir,
                    backendType = backend,
                    maxNewTokens = 512,
                    overrides = overrides
                )
                val ok = s.load()
                if (ok) {
                    session = s
                    lastUsedAt = System.currentTimeMillis()
                    lastError = ""
                    Log.e(TAG, "✅ MNN engine ready (backend=$backend)")
                    return@withContext true
                } else {
                    lastError = "LlmSession.load() failed (backend=$backend)"
                    Log.w(TAG, "❌ $lastError")
                }
            } catch (t: Throwable) {
                lastError = "backend=$backend exception: ${t.javaClass.simpleName}: ${t.message}"
                Log.w(TAG, "❌ $lastError", t)
            }
        }
        Log.e(TAG, "❌ Всі backend-и провалились")
        return@withContext false
    }

    override suspend fun complete(prompt: String): String? = withContext(Dispatchers.IO) {
        val s = session
        if (s == null || !s.isReady()) {
            lastError = "MNN engine not ready"
            return@withContext null
        }
        lastUsedAt = System.currentTimeMillis()
        val buffer = StringBuilder()
        val formattedPrompt = formatPrompt(prompt, currentModelName)
        Log.e(TAG, "complete: formatted prompt (${formattedPrompt.length} chars), first 100: ${formattedPrompt.take(100)}")
        return@withContext try {
            sessionLock.withLock {
                s.generate(formattedPrompt, object : GenerateProgressListener {
                    override fun onProgress(progress: String?): Boolean {
                        if (!progress.isNullOrEmpty()) {
                            buffer.append(progress)
                        }
                        return false  // MNN: true = ЗУПИНИТИ генерацію (return true обривав відповідь після 1 токена)
                    }
                })
            }
            Log.e(TAG, "complete: generated ${buffer.length} chars, text='${buffer.take(200)}'")
            stripThinking(buffer.toString())
        } catch (t: Throwable) {
            lastError = "complete failed: ${t.message}"
            Log.e(TAG, lastError, t)
            null
        }
    }

    override suspend fun streamComplete(
        prompt: String,
        onToken: (String) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        val s = session
        if (s == null || !s.isReady()) {
            lastError = "MNN engine not ready"
            return@withContext null
        }
        lastUsedAt = System.currentTimeMillis()
        val buffer = StringBuilder()
        val formattedPrompt = formatPrompt(prompt, currentModelName)
        return@withContext try {
            sessionLock.withLock {
                s.generate(formattedPrompt, object : GenerateProgressListener {
                    override fun onProgress(progress: String?): Boolean {
                        if (!progress.isNullOrEmpty()) {
                            buffer.append(progress)
                            try { onToken(progress) } catch (_: Throwable) {}
                        }
                        return false  // true = зупинити генерацію
                    }
                })
            }
            Log.e(TAG, "streamComplete: generated ${buffer.length} chars")
            stripThinking(buffer.toString())
        } catch (t: Throwable) {
            lastError = "streamComplete failed: ${t.message}"
            Log.e(TAG, lastError, t)
            if (buffer.isNotEmpty()) stripThinking(buffer.toString()) else null
        }
    }

    /** Повертає опис проблеми або null, якщо конфіги виглядають коректно. Також логує їх вміст для діагностики. */
    private fun validateConfigs(modelDir: File): String? {
        val stringKeys = listOf(
            "llm_model", "llm_weight", "tokenizer_file", "model_type", "attention_type", "attention_mask",
            "prompt_template", "system_prompt", "assistant_prompt_template", "user_prompt_template",
            "chat_template", "bos", "eos"
        )
        for (name in listOf("config.json", "llm_config.json")) {
            val f = File(modelDir, name)
            if (!f.exists()) {
                if (name == "llm_config.json") return "Немає $name — завантажте модель повторно"
                continue
            }
            val text = try { f.readText() } catch (t: Throwable) { return "$name не читається: ${t.message}" }
            Log.e(TAG, "$name (${f.length()} bytes): ${text.take(1500).replace("\n", " ")}")
            val obj = try { org.json.JSONObject(text) } catch (t: Throwable) {
                return "$name пошкоджений (не JSON) — завантажте модель повторно"
            }
            fun check(o: org.json.JSONObject, path: String): String? {
                val keys = o.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = o.opt(k)
                    if (k in stringKeys && v != null && v !is String) {
                        return "$name: поле «$path$k» має бути рядком, а є ${if (v == org.json.JSONObject.NULL) "null" else v.javaClass.simpleName}"
                    }
                    if (k in stringKeys && v == org.json.JSONObject.NULL) return "$name: поле «$path$k» = null"
                    if (v is org.json.JSONObject) check(v, "$path$k.")?.let { return it }
                }
                return null
            }
            check(obj, "")?.let { return it }
        }
        return null
    }

    fun close() {
        // Чекає завершення поточної генерації, щоб не звільнити сесію посеред generate()
        sessionLock.withLock {
            try {
                session?.release()
            } catch (t: Throwable) {
                Log.w(TAG, "close failed: ${t.message}")
            }
            session = null
        }
    }
}
