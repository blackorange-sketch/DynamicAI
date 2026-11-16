package com.dynamicai.launcher

/**
 * Редакція збірки. Гілка feature/cloud-only ставить CLOUD_ONLY = true і прибирає локальні рушії (LiteRT, MNN, llama.cpp):
 * усі відповіді й керування телефоном — через Gemini/Groq.
 */
object Edition {
    const val CLOUD_ONLY = true
}
