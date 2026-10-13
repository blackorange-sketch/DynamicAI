package com.dynamicai.launcher.llm

class NoOpLlmEngine : LlmEngine {
    override val isReady: Boolean = false
    override suspend fun complete(prompt: String): String? = null
}
