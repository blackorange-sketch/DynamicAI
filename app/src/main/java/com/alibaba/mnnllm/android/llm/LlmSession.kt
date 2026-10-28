package com.alibaba.mnnllm.android.llm

import android.util.Log
import java.io.File

class LlmSession(
    private val modelId: String,
    private val configDir: File,
    private val backendType: String = "cpu",
    private val maxNewTokens: Int = 512,
) {
    companion object {
        private const val TAG = "MnnLlmSession"

        init {
            Log.e(TAG, "🔵 init block STARTED")
            try {
                System.loadLibrary("c++_shared")
                Log.e(TAG, "✅ c++_shared loaded")
                System.loadLibrary("MNN")
                Log.e(TAG, "✅ MNN loaded")
                System.loadLibrary("mnnllmapp")
                Log.e(TAG, "✅ mnnllmapp loaded")
                Log.e(TAG, "✅ ALL MNN libraries loaded")
            } catch (t: Throwable) {
                Log.e(TAG, "❌ Failed to load MNN libs: ${t.message}", t)
            }
        }
    }

    private var nativePtr: Long = 0L
    private var keepHistory: Boolean = true

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
        if (nativePtr == 0L) return null
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
