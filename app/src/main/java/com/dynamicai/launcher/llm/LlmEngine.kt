package com.dynamicai.launcher.llm

/**
 * Інтерфейс локальної LLM. Реалізації:
 *  - NoOpLlmEngine — заглушка (зараз)
 *  - MediaPipeLlmEngine — Gemma 4 E2B (наступний крок)
 */
interface LlmEngine {
    val isMnn: Boolean get() = false
    val isReady: Boolean
    suspend fun complete(prompt: String): String?
    suspend fun streamComplete(prompt: String, onToken: (String) -> Unit): String? {
        // Fallback: якщо стрімінг не підтримується, просто повертаємо повну відповідь
        val result = complete(prompt)
        if (result != null) onToken(result)
        return result
    }
}
