package com.dynamicai.launcher.llm

import android.content.Context
import java.io.File

/**
 * Підготовлена заглушка. Реальні виклики LiteRT-LM API буде додано,
 * коли користувач буде готовий тестувати. Наразі:
 *  - isReady = false
 *  - initialize() повертає false
 *  - complete() повертає null
 *
 * Це дозволяє тримати весь архітектурний каркас (Agent, ViewModel, UI)
 * готовим, не блокуючи збірку.
 */
class LiteRtLlmEngine(@Suppress("UNUSED_PARAMETER") private val context: Context) : LlmEngine {
    override val isReady: Boolean = false
    override suspend fun complete(prompt: String): String? = null
    suspend fun initialize(modelFile: File): Boolean = false
    fun close() {}
}
