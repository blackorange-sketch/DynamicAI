package com.dynamicai.launcher.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LiteRtLlmEngine(private val context: Context) : LlmEngine {

    private var engine: Engine? = null
    private var _isReady = false

    override val isReady: Boolean get() = _isReady

    suspend fun initialize(modelFile: File): Boolean = withContext(Dispatchers.IO) {
        if (_isReady) return@withContext true
        return@withContext try {
            val config = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.GPU(),
                cacheDir = context.cacheDir.path
            )
            val e = Engine(config)
            e.initialize()
            engine = e
            _isReady = true
            true
        } catch (e: Throwable) {
            // Fallback на CPU якщо GPU не підтримується
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
                true
            } catch (e2: Throwable) {
                _isReady = false
                false
            }
        }
    }

    override suspend fun complete(prompt: String): String? = withContext(Dispatchers.IO) {
        val e = engine ?: return@withContext null
        return@withContext try {
            e.createConversation().use { conversation ->
                conversation.sendMessage(prompt)
            }
        } catch (e: Throwable) {
            null
        }
    }

    fun close() {
        try {
            engine?.close()
        } catch (_: Throwable) {
        }
        engine = null
        _isReady = false
    }
}
