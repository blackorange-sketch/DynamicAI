package com.dynamicai.launcher.llm

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Гібридний LLM-рушій: для .litertlm-файлу використовує LiteRT,
 * для папки з llm.mnn — MNN, для .gguf-файлу — llama.cpp.
 */
class HybridLlmEngine(private val context: Context) : LlmEngine {
    override val isMnn: Boolean get() = activeKind == Kind.MNN || activeKind == Kind.LLAMA

    enum class Kind { NONE, LITERT, MNN, LLAMA }

    private var liteRt: LiteRtLlmEngine? = null
    private var mnn: MnnLlmEngine? = null
    private var llama: LlamaCppLlmEngine? = null
    private var activeKind: Kind = Kind.NONE

    @Volatile var lastUsedAt: Long = 0L
        private set

    @Volatile var lastError: String = ""
        private set

    override val isReady: Boolean
        get() = when (activeKind) {
            Kind.LITERT -> liteRt?.isReady == true
            Kind.MNN -> mnn?.isReady == true
            Kind.LLAMA -> llama?.isReady == true
            Kind.NONE -> false
        }

    /** Тип рушія, яким потрібно відкрити цей шлях. */
    private fun kindFor(path: File): Kind = when {
        path.isDirectory -> Kind.MNN
        path.name.endsWith(".gguf", ignoreCase = true) -> Kind.LLAMA
        else -> Kind.LITERT
    }

    val activeEngineName: String get() = when (activeKind) {
        Kind.LITERT -> "LiteRT"; Kind.MNN -> "MNN"; Kind.LLAMA -> "llama.cpp"; Kind.NONE -> ""
    }

    /** Діагностика llama.cpp (модель, контекст, можливості CPU) або порожньо. */
    fun llamaInfo(): String = if (activeKind == Kind.LLAMA) llama?.info().orEmpty() else ""

    suspend fun initialize(path: File): Boolean {
        // Закриваємо попередній engine, якщо тип змінюється
        val want = kindFor(path)
        if (activeKind != Kind.NONE && activeKind != want) {
            close()
        }

        if (isReady) return true

        if (want == Kind.LLAMA) {
            if (llama == null) llama = LlamaCppLlmEngine(context)
            val ok = llama!!.initialize(path)
            if (ok) {
                activeKind = Kind.LLAMA
                lastUsedAt = llama!!.lastUsedAt
                Log.e(TAG, "✅ Active: llama.cpp")
            } else {
                lastError = llama!!.lastError
            }
            return ok
        }

        return if (path.isDirectory) {
            if (mnn == null) mnn = MnnLlmEngine(context)
            val ok = mnn!!.initialize(path)
            if (ok) {
                activeKind = Kind.MNN
                lastUsedAt = mnn!!.lastUsedAt
                Log.e(TAG, "✅ Active: MNN")
            } else {
                lastError = mnn!!.lastError
            }
            ok
        } else {
            if (liteRt == null) liteRt = LiteRtLlmEngine(context)
            val ok = liteRt!!.initialize(path)
            if (ok) {
                activeKind = Kind.LITERT
                lastUsedAt = liteRt!!.lastUsedAt
                Log.e(TAG, "✅ Active: LiteRT")
            } else {
                lastError = liteRt!!.lastError
            }
            ok
        }
    }

    override suspend fun complete(prompt: String): String? {
        lastUsedAt = System.currentTimeMillis()
        return when (activeKind) {
            Kind.LITERT -> liteRt?.complete(prompt)
            Kind.MNN -> mnn?.complete(prompt)
            Kind.LLAMA -> llama?.complete(prompt)
            Kind.NONE -> null
        }
    }

    override suspend fun streamComplete(prompt: String, onToken: (String) -> Unit): String? {
        lastUsedAt = System.currentTimeMillis()
        return when (activeKind) {
            Kind.LITERT -> liteRt?.streamComplete(prompt, onToken)
            Kind.MNN -> mnn?.streamComplete(prompt, onToken)
            Kind.LLAMA -> llama?.streamComplete(prompt, onToken)
            Kind.NONE -> null
        }
    }

    fun close() {
        try {
            when (activeKind) {
                Kind.LITERT -> liteRt?.close()
                Kind.MNN -> mnn?.close()
                Kind.LLAMA -> llama?.close()
                Kind.NONE -> {}
            }
        } catch (t: Throwable) {
            Log.w(TAG, "close failed: ${t.message}")
        }
        activeKind = Kind.NONE
    }

    companion object {
        private const val TAG = "HybridLlmEngine"
    }
}
