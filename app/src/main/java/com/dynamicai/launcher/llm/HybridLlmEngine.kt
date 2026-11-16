package com.dynamicai.launcher.llm

import android.content.Context
import java.io.File

/**
 * Хмарна версія: локальних рушіїв (LiteRT, MNN, llama.cpp) у збірці немає.
 * Клас лишається з тим самим API, щоб решта коду не змінювалась; він ніколи не стає «готовим»,
 * тож усі відповіді йдуть через CloudLlm (Gemini/Groq).
 */
@Suppress("UNUSED_PARAMETER")
class HybridLlmEngine(private val context: Context) : LlmEngine {
    override val isMnn: Boolean get() = false

    @Volatile var lastUsedAt: Long = 0L
        private set

    @Volatile var lastError: String = "Локальні моделі недоступні в хмарній версії"
        private set

    override val isReady: Boolean get() = false

    val activeEngineName: String get() = ""

    fun llamaInfo(): String = ""

    suspend fun initialize(path: File): Boolean = false

    override suspend fun complete(prompt: String): String? = null

    override suspend fun streamComplete(prompt: String, onToken: (String) -> Unit): String? = null

    fun close() {}
}
