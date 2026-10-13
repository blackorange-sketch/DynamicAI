package com.dynamicai.launcher.llm

/**
 * Інтерфейс локальної LLM. Реалізації:
 *  - NoOpLlmEngine — заглушка (зараз)
 *  - MediaPipeLlmEngine — Gemma 4 E2B (наступний крок)
 */
interface LlmEngine {
    val isReady: Boolean
    suspend fun complete(prompt: String): String?
}
