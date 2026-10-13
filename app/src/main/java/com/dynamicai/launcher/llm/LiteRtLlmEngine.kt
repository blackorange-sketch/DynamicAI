package com.dynamicai.launcher.llm

import android.content.Context
import java.io.File

/**
 * Заглушка. Реальну реалізацію буде додано після підбору сумісної версії LiteRT-LM.
 * Наразі поводиться як NoOpLlmEngine — LLM неактивна.
 */
class LiteRtLlmEngine(@Suppress("UNUSED_PARAMETER") private val context: Context) : LlmEngine {
    override val isReady: Boolean = false
    override suspend fun complete(prompt: String): String? = null
    suspend fun initialize(modelFile: File): Boolean = false
    fun close() {}
}
