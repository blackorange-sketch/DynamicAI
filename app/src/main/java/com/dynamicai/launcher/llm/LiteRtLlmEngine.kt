package com.dynamicai.launcher.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
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

        // Спроба 1: NPU (Hexagon HTP через QNN)
        attempts.add("Backend.NPU()" to {
            try {
                val config = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir),
                    cacheDir = context.cacheDir.path
                )
                // Спроба встановити HTPPerformanceMode = BURST через рефлексію
                try {
                    val qualcommOptionsClass = Class.forName("com.google.ai.edge.litertlm.CompiledModel\$QualcommOptions")
                    val htpPerformanceModeClass = Class.forName("com.google.ai.edge.litertlm.CompiledModel\$QualcommOptions\$HtpPerformanceMode")
                    val burstEnum = htpPerformanceModeClass.enumConstants.first { it.toString() == "BURST" }
                    val qualcommOptionsConstructor = qualcommOptionsClass.getConstructor(htpPerformanceModeClass)
                    val qualcommOptions = qualcommOptionsConstructor.newInstance(burstEnum)
                    
                    // Намагаємось встановити qualcommOptions в EngineConfig
                    val engineConfigClass = config.javaClass
                    val field = engineConfigClass.getDeclaredField("qualcommOptions")
                    field.isAccessible = true
                    field.set(config, qualcommOptions)
                    android.util.Log.e(TAG, "✅ Встановлено HTPPerformanceMode = BURST")
                } catch (t: Throwable) {
                    android.util.Log.w(TAG, "Не вдалося встановити HTPPerformanceMode: ${t.message}")
                }
                val e = Engine(config)
                e.initialize()
                engine = e
                _isReady = true
                lastUsedAt = System.currentTimeMillis()
                Log.e(TAG, "✅ NPU backend OK")
                true
            } catch (t: Throwable) {
                Log.e(TAG, "❌ NPU backend failed: ${t.message}")
                _isReady = false  // Гарантовано скидаємо прапорець
                false
            }
        })

        // Спроба 2: GPU (через системний OpenCL, з дозволами в маніфесті)
        attempts.add("Backend.GPU()" to {
            val config = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.GPU(),
                cacheDir = context.cacheDir.path
            )
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
                        fullText.append(text)
                        onToken(text)
                        Unit
                    }
                    asyncMethod.invoke(conversation, prompt, callback)
                    return@withContext fullText.toString()
                } else {
                    // Fallback: без стрімінгу
                    val message = conversation.sendMessage(prompt)
                    val result = extractText(message) ?: message?.toString() ?: ""
                    if (result.isNotBlank()) onToken(result)
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
            e.createConversation(conversationConfig).use { conversation ->
                // LiteRT-LM сам застосовує chat template з metadata моделі
                val message = conversation.sendMessage(prompt)
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

            // Логуємо RAM
            val rt = Runtime.getRuntime()
            val totalMb = rt.totalMemory() / 1024 / 1024
            val freeMb = rt.freeMemory() / 1024 / 1024
            val usedMb = totalMb - freeMb
            val maxMb = rt.maxMemory() / 1024 / 1024
            val modelMb = newModelFile.length() / 1024 / 1024
            val freeInMax = maxMb - usedMb

            Log.e(TAG, "📊 RAM: used=${usedMb}MB / max=${maxMb}MB, free_in_max=${freeInMax}MB, model=${modelMb}MB")

            if (modelMb > freeInMax) {
                Log.w(TAG, "⚠️ Модель (${modelMb}MB) більша за доступну RAM (${freeInMax}MB) — можливий OOM")
            } else {
                Log.e(TAG, "✅ RAM достатньо: треба ${modelMb}MB, є ${freeInMax}MB")
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
