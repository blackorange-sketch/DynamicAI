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

    suspend fun initialize(modelFile: File): Boolean = withContext(Dispatchers.IO) {
        if (_isReady) return@withContext true
        if (!modelFile.exists()) {
            lastError = "Файл моделі не знайдено"
            return@withContext false
        }

        // Reflection — досліджуємо API
        val info = StringBuilder()
        val classesToProbe = listOf(
            "com.google.ai.edge.litertlm.ConversationConfig",
            "com.google.ai.edge.litertlm.SessionConfig",
            "com.google.ai.edge.litertlm.SamplerConfig",
            "com.google.ai.edge.litertlm.Session",
            "com.google.ai.edge.litertlm.Engine",
            "com.google.ai.edge.litertlm.Conversation"
        )
        for (className in classesToProbe) {
            try {
                val clazz = Class.forName(className)
                info.append("=== $className ===\n")
                info.append("Fields: ")
                info.append(clazz.declaredFields.map { it.name }.joinToString())
                info.append("\n")
                info.append("Methods: ")
                info.append(clazz.declaredMethods.map { it.name }.joinToString())
                info.append("\n")
                info.append("Constructors: ")
                info.append(clazz.declaredConstructors.map { c -> c.parameterTypes.joinToString(",") { it.simpleName } }.joinToString(" | "))
                info.append("\n\n")
            } catch (t: Throwable) {
                info.append("=== $className ===\n")
                info.append("NOT FOUND: ${t.message}\n\n")
            }
        }
        Log.e(TAG, info.toString())

        // Спроби різних API
        val attempts = mutableListOf<Pair<String, () -> Boolean>>()

        // Спроба 1: NPU (Hexagon HTP через QNN)
        attempts.add("Backend.NPU()" to {
            try {
                val config = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir),
                    cacheDir = context.cacheDir.path
                )
                val e = Engine(config)
                e.initialize()
                engine = e
                _isReady = true
                lastUsedAt = System.currentTimeMillis()
                Log.e(TAG, "✅ NPU backend OK")
                true
            } catch (t: Throwable) {
                Log.e(TAG, "❌ NPU backend failed: ${t.message}")
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

        for ((name, block) in attempts) {
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
                temperature = 0.2, // Прибирає галюцинації
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
                seed = 0, temperature = 0.2, topK = 40, topP = 0.9
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

    fun close() {
        try {
            engine?.close()
        } catch (_: Throwable) {}
        engine = null
        _isReady = false
    }
}
