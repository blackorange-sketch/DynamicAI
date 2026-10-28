package com.alibaba.mnnllm.android.llm

import android.util.Log
import java.io.File

/**
 * Мінімальна версія LlmSession — JNI-обгортка для libmnnllmapp.so.
 *
 * Клас має бути САМЕ у пакеті com.alibaba.mnnllm.android.llm з САМЕ такою назвою —
 * інакше JNI-функції Java_com_alibaba_mnnllm_android_llm_LlmSession_* не знайдуться.
 */
class LlmSession(
    private val modelId: String,
    private val configDir: File,
    private val backendType: String = "cpu",   // "cpu" | "opencl" | "vulkan"
    private val maxNewTokens: Int = 512,
) {
    companion object {
        private const val TAG = "MnnLlmSession"
        init {
            try {
                System.loadLibrary("c++_shared")
                System.loadLibrary("MNN")
                System.loadLibrary("MNN_CL")
                System.loadLibrary("llm")
                System.loadLibrary("mnnllmapp")
                Log.e(TAG, "✅ MNN libraries loaded")
            } catch (t: Throwable) {
                Log.e(TAG, "❌ Failed to load MNN libs: ${t.message}", t)
            }
        }
    }

    private var nativePtr: Long = 0L
    private var keepHistory: Boolean = true

    // === JNI METHODS (реалізовані в libmnnllmapp.so) ===
    private external fun initNative(
        configPath: String?,
        history: List<String>?,
        mergedConfigStr: String?,
        configJsonStr: String?
    ): Long

    private external fun submitNative(
        instanceId: Long,
        input: String,
        keepHistory: Boolean,
        listener: GenerateProgressListener
    ): HashMap<String, Any>

    private external fun releaseNative(instanceId: Long)
    private external fun resetNative(instanceId: Long)

    // === PUBLIC API ===

    fun load(): Boolean {
        if (nativePtr != 0L) return true
        return try {
            val mergedConfig = """{"backend_type":"$backendType","thread_num":4,"precision":"low","memory":"low","max_new_tokens":$maxNewTokens}"""
            val configJson = """{"is_r1":false,"mmap_dir":"","keep_history":$keepHistory}"""

            Log.e(TAG, "initNative: configPath=${configDir.absolutePath}")
            Log.e(TAG, "mergedConfig=$mergedConfig")
            Log.e(TAG, "configJson=$configJson")

            nativePtr = initNative(configDir.absolutePath, null, mergedConfig, configJson)
            Log.e(TAG, "initNative → nativePtr=$nativePtr")
            nativePtr != 0L
        } catch (t: Throwable) {
            Log.e(TAG, "load failed: ${t.message}", t)
            false
        }
    }

    fun generate(prompt: String, listener: GenerateProgressListener): String? {
        if (nativePtr == 0L) {
            Log.e(TAG, "generate: nativePtr is 0, call load() first")
            return null
        }
        return try {
            val result = submitNative(nativePtr, prompt, keepHistory, listener)
            Log.e(TAG, "submitNative result keys=${result.keys}")
            (result["text"] as? String) ?: (result["result"] as? String) ?: result.toString()
        } catch (t: Throwable) {
            Log.e(TAG, "generate failed: ${t.message}", t)
            null
        }
    }

    fun release() {
        try {
            if (nativePtr != 0L) {
                releaseNative(nativePtr)
                nativePtr = 0L
            }
        } catch (t: Throwable) {
            Log.e(TAG, "release failed: ${t.message}")
        }
    }

    fun isReady(): Boolean = nativePtr != 0L
}
