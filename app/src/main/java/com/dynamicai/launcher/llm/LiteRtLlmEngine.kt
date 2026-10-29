package com.dynamicai.launcher.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.ActivationDataType
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LiteRtLlmEngine(private val context: Context) : LlmEngine {

    companion object {
        private const val TAG = "LiteRtLlmEngine"
    }

    private var engine: Engine? = null
    private var _isReady = false
    @Volatile var lastUsedAt: Long = 0L
        private set

    override val isReady: Boolean get() = _isReady

    @Volatile var lastError: String = ""

    private var currentSettings: com.dynamicai.launcher.data.LauncherSettings =
        com.dynamicai.launcher.data.LauncherSettings()

    /**
     * Експериментальні параметри GPU без перезбірки. Файл (необов'язковий):
     *   <externalFilesDir>/litert_overrides.json
     *   {"activation":"FLOAT32","mtp":false,"separate_cache":true,"clear_cache":true}
     * activation: "FLOAT32" | "FLOAT16" | відсутнє = за замовчуванням бібліотеки
     * mtp: true/false = примусово вмикає/вимикає speculative decoding; відсутнє = за замовчуванням
     * separate_cache: окрема папка кешу для комбінації модель+точність+mtp (старий кеш не впливає)
     * clear_cache: очистити цю папку кешу перед запуском
     */
    private data class LiteRtOverrides(
        val activation: String? = null,
        val mtp: Boolean? = null,
        val separateCache: Boolean = false,
        val clearCache: Boolean = false
    )

    private fun readOverrides(): LiteRtOverrides {
        return try {
            val f = File(context.getExternalFilesDir(null), "litert_overrides.json")
            if (!f.exists()) return LiteRtOverrides()
            val o = org.json.JSONObject(f.readText())
            LiteRtOverrides(
                activation = o.optString("activation", "").uppercase().ifBlank { null },
                mtp = if (o.has("mtp")) o.optBoolean("mtp") else null,
                separateCache = o.optBoolean("separate_cache", false),
                clearCache = o.optBoolean("clear_cache", false)
            ).also { Log.e("LITERT_CFG", "litert_overrides.json: $it") }
        } catch (t: Throwable) {
            Log.e("LITERT_CFG", "litert_overrides.json read failed: ${t.message}")
            LiteRtOverrides()
        }
    }

    @OptIn(ExperimentalApi::class)
    private fun applySpeculativeDecoding(enabled: Boolean) {
        try {
            ExperimentalFlags.enableSpeculativeDecoding = enabled
            Log.e("LITERT_CFG", "enableSpeculativeDecoding=$enabled")
        } catch (t: Throwable) {
            Log.e("LITERT_CFG", "enableSpeculativeDecoding failed: ${t.message}")
        }
    }

    suspend fun initialize(modelFile: File): Boolean = withContext(Dispatchers.IO) {
        if (_isReady) return@withContext true
        if (!modelFile.exists()) {
            lastError = "Файл моделі не знайдено"
            return@withContext false
        }
// Спроби різних API
        // Завантажуємо актуальні налаштування
        currentSettings = com.dynamicai.launcher.data.SettingsStorage(context).load()
        Log.e(TAG, "⚙️ Backend=${currentSettings.llmBackend}, Temp=${currentSettings.llmTemperature}")

        // Звільняємо стару модель перед завантаженням нової (щоб не було OOM)
        freeOldEngineBeforeLoad(modelFile)

        val attempts = mutableListOf<Pair<String, () -> Boolean>>()

        // Спроба 1: GPU (через системний OpenCL, з дозволами в маніфесті)
        attempts.add("Backend.GPU()" to {
            val ov = readOverrides()
            ov.mtp?.let { applySpeculativeDecoding(it) }
            val cachePath = if (ov.separateCache) {
                val tag = "litert_${modelFile.nameWithoutExtension}_${ov.activation ?: "default"}_mtp${ov.mtp ?: "default"}"
                File(context.cacheDir, tag).also {
                    if (ov.clearCache) it.deleteRecursively()
                    it.mkdirs()
                }.path
            } else {
                if (ov.clearCache) {
                    context.cacheDir.listFiles()?.forEach { f -> if (f.name.startsWith("litert_")) f.deleteRecursively() }
                }
                context.cacheDir.path
            }
            Log.e("LITERT_CFG", "GPU: activation=${ov.activation ?: "default"} mtp=${ov.mtp ?: "default"} cacheDir=$cachePath model=${modelFile.name}")
            val config = when (ov.activation) {
                "FLOAT32" -> EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.GPU(),
                    activationDataType = ActivationDataType.FLOAT32,
                    cacheDir = cachePath
                )
                "FLOAT16" -> EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.GPU(),
                    activationDataType = ActivationDataType.FLOAT16,
                    cacheDir = cachePath
                )
                else -> EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.GPU(),
                    cacheDir = cachePath
                )
            }
            val e = Engine(config)
            e.initialize()
            engine = e
            _isReady = true
            lastUsedAt = System.currentTimeMillis()
            Log.e(TAG, "✅ GPU backend OK")
            true
        })

        // Спроба 2: CPU (fallback)
        attempts.add("Backend.CPU()" to {
            val config = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.path
            )
            val e = Engine(config)
            e.initialize()
            engine = e
            _isReady = true
            lastUsedAt = System.currentTimeMillis()
            true
        })

        // Фільтруємо спроби згідно з налаштуванням
        val filteredAttempts = attempts.filter { (name, _) ->
            when (currentSettings.llmBackend) {
                "npu" -> name.contains("NPU")
                "gpu" -> name.contains("GPU")
                "cpu" -> name.contains("CPU")
                else -> true  // auto — пробуємо все
            }
        }
        Log.e(TAG, "🎯 Спроби: ${filteredAttempts.map { it.first }}")

        for ((name, block) in filteredAttempts) {
            try {
                block()
                lastError = ""
                Log.d(TAG, "✅ Init OK with $name")
                return@withContext true
            } catch (t: Throwable) {
                Log.e(TAG, "$name failed: ${t.message}", t)
                lastError = "$name: ${t.message}"
            }
        }

        _isReady = false
        false
    }

    override suspend fun streamComplete(prompt: String, onToken: (String) -> Unit): String? = withContext(Dispatchers.IO) {
        lastUsedAt = System.currentTimeMillis()
        val e = engine ?: return@withContext null
        val t0 = System.currentTimeMillis()
        var firstTokenAt = 0L
        try {
            // Створюємо SamplerConfig з низькою температурою
            val samplerConfig = com.google.ai.edge.litertlm.SamplerConfig(
                seed = 0,
                temperature = currentSettings.llmTemperature.toDouble(),
                topK = 40,
                topP = 0.9
            )
            val conversationConfig = com.google.ai.edge.litertlm.ConversationConfig(
                samplerConfig = samplerConfig
            )
            
            e.createConversation(conversationConfig).use { conversation ->
                // Спробувати знайти метод sendMessageAsync через reflection
                val asyncMethod = conversation.javaClass.methods.firstOrNull {
                    it.name == "sendMessageAsync" && it.parameterTypes.size == 2
                }

                if (asyncMethod != null) {
                    // LiteRT-LM підтримує стрімінг
                    val fullText = StringBuilder()
                    val callback = { text: String ->
                        if (firstTokenAt == 0L) firstTokenAt = System.currentTimeMillis()
                        fullText.append(text)
                        onToken(text)
                        Unit
                    }
                    asyncMethod.invoke(conversation, prompt, callback)
                    val now = System.currentTimeMillis()
                    Log.e(TAG, "⏱ stream: firstToken=${if (firstTokenAt > 0) firstTokenAt - t0 else -1}ms total=${now - t0}ms chars=${fullText.length}")
                    return@withContext fullText.toString()
                } else {
                    // Fallback: без стрімінгу
                    val message = conversation.sendMessage(prompt)
                    val result = extractText(message) ?: message?.toString() ?: ""
                    if (result.isNotBlank()) onToken(result)
                    Log.e(TAG, "⏱ sendMessage (no stream): total=${System.currentTimeMillis() - t0}ms chars=${result.length}")
                    return@withContext result
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "streamComplete failed: ${t.message}", t)
            lastError = "stream exception: ${t.message}"
            // Fallback на звичайний complete
            return@withContext complete(prompt)
        }
    }

    override suspend fun complete(prompt: String): String? = withContext(Dispatchers.IO) {
        lastUsedAt = System.currentTimeMillis()
        val e = engine ?: return@withContext null
        return@withContext try {
            val samplerConfig = com.google.ai.edge.litertlm.SamplerConfig(
                seed = 0, temperature = currentSettings.llmTemperature.toDouble(), topK = 40, topP = 0.9
            )
            val conversationConfig = com.google.ai.edge.litertlm.ConversationConfig(
                samplerConfig = samplerConfig
            )
            val tc0 = System.currentTimeMillis()
            e.createConversation(conversationConfig).use { conversation ->
                // LiteRT-LM сам застосовує chat template з metadata моделі
                val message = conversation.sendMessage(prompt)
                Log.e(TAG, "⏱ complete: total=${System.currentTimeMillis() - tc0}ms")
                if (message == null) {
                    lastError = "sendMessage → null"
                    return@withContext null
                }

                // Спробувати різні способи витягнути текст
                val result = extractText(message)
                if (result.isNullOrBlank()) {
                    lastError = "message class=${message.javaClass.name}, str='${message.toString()}'"
                    return@withContext message.toString()
                }
                lastError = ""
                result
            }
        } catch (t: Throwable) {
            Log.e(TAG, "complete failed: ${t.message}", t)
            lastError = "exception: ${t.javaClass.simpleName}: ${t.message}"
            null
        }
    }

    private fun extractText(message: Any): String? {
        // Спробувати методи у різних варіантах
        val methods = listOf(
            "getText", "getContent", "text", "content", "getMessage"
        )
        for (name in methods) {
            try {
                val m = message.javaClass.getMethod(name)
                val v = m.invoke(message)
                if (v is String && v.isNotBlank()) return v
            } catch (_: Throwable) {}
        }
        // Спробувати поля
        val fields = listOf("text", "content", "message")
        for (name in fields) {
            try {
                val f = message.javaClass.getField(name)
                val v = f.get(message)
                if (v is String && v.isNotBlank()) return v
            } catch (_: Throwable) {}
        }
        return null
    }

    /**
     * Звільняє пам'ять перед завантаженням нової моделі:
     * 1. Закриває старий engine (якщо є)
     * 2. Очищає посилання
     * 3. Викликає GC
     * 4. Логує вільну RAM
     */
    private fun freeOldEngineBeforeLoad(newModelFile: File) {
        try {
            engine?.let {
                try {
                    it.close()
                    Log.e(TAG, "♻️ Старий engine закрито")
                } catch (t: Throwable) {
                    Log.w(TAG, "close() failed: ${t.message}")
                }
            }
            engine = null
            _isReady = false

            // Очищаємо .tmp/.part файли (залишки від завантажень)
            try {
                context.cacheDir.listFiles()?.forEach { f ->
                    if (f.name.endsWith(".tmp") || f.name.endsWith(".part")) {
                        f.delete()
                        Log.e(TAG, "🗑️ Видалено: ${f.name}")
                    }
                }
            } catch (_: Throwable) {}

            // GC + коротка пауза
            System.gc()
            Thread.sleep(150)

            // Логуємо РЕАЛЬНУ фізичну RAM через ActivityManager
            val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val availMb = memInfo.availMem / 1024 / 1024
            val totalMb = memInfo.totalMem / 1024 / 1024
            val thresholdMb = memInfo.threshold / 1024 / 1024
            val modelMb = newModelFile.length() / 1024 / 1024
            val realFreeMb = availMb - thresholdMb  // скільки реально можна використати

            Log.e(TAG, "📊 Фізична RAM: avail=${availMb}MB, total=${totalMb}MB, threshold=${thresholdMb}MB, model=${modelMb}MB")
            Log.e(TAG, "📊 Реально доступно: ${realFreeMb}MB")

            if (modelMb > realFreeMb) {
                Log.w(TAG, "⚠️ Модель (${modelMb}MB) може не влізти — доступно лише ${realFreeMb}MB")
            } else {
                Log.e(TAG, "✅ RAM достатньо: треба ${modelMb}MB, є ${realFreeMb}MB")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "freeOldEngineBeforeLoad failed: ${t.message}")
        }
    }

    fun close() {
        try {
            engine?.close()
        } catch (_: Throwable) {}
        engine = null
        _isReady = false
    }
}
