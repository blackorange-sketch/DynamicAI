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

    override val isReady: Boolean get() = _isReady

    @Volatile var lastError: String = ""

    suspend fun initialize(modelFile: File): Boolean = withContext(Dispatchers.IO) {
        if (_isReady) return@withContext true
        if (!modelFile.exists()) {
            lastError = "Файл моделі не знайдено"
            return@withContext false
        }

        // Спробуємо GPU
        try {
            val config = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.GPU(),
                cacheDir = context.cacheDir.path
            )
            val e = Engine(config)
            e.initialize()
            engine = e
            _isReady = true
            lastError = ""
            Log.d(TAG, "✅ GPU init OK")
            return@withContext true
        } catch (t: Throwable) {
            Log.e(TAG, "GPU init failed: ${t.message}", t)
            lastError = "GPU: ${t.message}"
        }

        // Fallback CPU
        try {
            val config = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.path
            )
            val e = Engine(config)
            e.initialize()
            engine = e
            _isReady = true
            lastError = ""
            Log.d(TAG, "✅ CPU init OK")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "CPU init failed: ${t.message}", t)
            lastError = "CPU: ${t.message}"
            _isReady = false
            false
        }
    }

    override suspend fun complete(prompt: String): String? = withContext(Dispatchers.IO) {
        val e = engine ?: return@withContext null
        return@withContext try {
            e.createConversation().use { conversation ->
                val message = conversation.sendMessage(prompt)
                // Message → String
                message?.toString()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "complete failed: ${t.message}", t)
            null
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
