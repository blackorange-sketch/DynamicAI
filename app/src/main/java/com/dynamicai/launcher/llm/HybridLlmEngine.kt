package com.dynamicai.launcher.llm

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Гібридний LLM-рушій: для .litertlm-файлу використовує LiteRT,
 * для папки з llm.mnn — MNN.
 */
class HybridLlmEngine(private val context: Context) : LlmEngine {

    enum class Kind { NONE, LITERT, MNN }

    private var liteRt: LiteRtLlmEngine? = null
    private var mnn: MnnLlmEngine? = null
    private var activeKind: Kind = Kind.NONE

    @Volatile var lastUsedAt: Long = 0L
        private set

    @Volatile var lastError: String = ""
        private set

    override val isReady: Boolean
        get() = when (activeKind) {
            Kind.LITERT -> liteRt?.isReady == true
            Kind.MNN -> mnn?.isReady == true
            Kind.NONE -> false
        }

    suspend fun initialize(path: File): Boolean {
        // Закриваємо попередній engine, якщо тип змінюється
        if (path.isDirectory && activeKind == Kind.LITERT) {
            liteRt?.close(); activeKind = Kind.NONE
        }
        if (!path.isDirectory && activeKind == Kind.MNN) {
            mnn?.close(); activeKind = Kind.NONE
        }

        if (isReady) return true

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
            Kind.NONE -> null
        }
    }

    override suspend fun streamComplete(prompt: String, onToken: (String) -> Unit): String? {
        lastUsedAt = System.currentTimeMillis()
        return when (activeKind) {
            Kind.LITERT -> liteRt?.streamComplete(prompt, onToken)
            Kind.MNN -> mnn?.streamComplete(prompt, onToken)
            Kind.NONE -> null
        }
    }

    fun close() {
        try {
            when (activeKind) {
                Kind.LITERT -> liteRt?.close()
                Kind.MNN -> mnn?.close()
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
