package com.alibaba.mnnllm.android.llm

import android.util.Log
import java.io.File

class LlmSession(
    private val modelId: String,
    private val configDir: File,
    private val backendType: String = "cpu",
    private val maxNewTokens: Int = 512,
    /**
     * Необов'язкові перевизначення runtime-конфігу MNN (precision, memory, thread_num, tmp_path...).
     * Ключі верхнього рівня застосовуються завжди; об'єкт під ключем "opencl"/"cpu" — лише для цього backend.
     */
    private val overrides: org.json.JSONObject? = null,
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
    private var keepHistory: Boolean = false // ВИМКНЕНО: історія псує генерацію

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
            val merged = org.json.JSONObject()
                .put("backend_type", backendType)
                .put("thread_num", 4)
                .put("precision", "normal")
                .put("memory", "normal")
                .put("max_new_tokens", maxNewTokens)
                .put("use_mmap", true)
                .put("is_visual", false)
            overrides?.let { o ->
                val keys = o.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k == "clean_cache" || o.get(k) is org.json.JSONObject) continue
                    merged.put(k, o.get(k))
                }
                o.optJSONObject(backendType)?.let { b ->
                    val bk = b.keys()
                    while (bk.hasNext()) { val k = bk.next(); merged.put(k, b.get(k)) }
                }
            }
            val mergedConfig = merged.toString()
            val configJson = """{"is_r1":false,"mmap_dir":"${configDir.absolutePath}","keep_history":false,"use_mmap":true,"is_visual":false}"""
            Log.e(TAG, "initNative: configPath=${configDir.absolutePath}")
            Log.e(TAG, "mergedConfig=$mergedConfig")
            Log.e(TAG, "configJson=$configJson")
            // Нативна частина конкатенує configPath + "tokenizer.txt" без слеша,
            // тому треба передати шлях із завершальним "/"
            val configPathWithSlash = if (configDir.absolutePath.endsWith("/")) {
                configDir.absolutePath
            } else {
                configDir.absolutePath + "/"
            }
            Log.e(TAG, "initNative: configPath=$configPathWithSlash")
            nativePtr = initNative(configPathWithSlash, emptyList(), mergedConfig, configJson)
            Log.e(TAG, "initNative → nativePtr=$nativePtr")
            nativePtr != 0L
        } catch (t: Throwable) {
            Log.e(TAG, "load failed: ${t.message}", t)
            false
        }
    }

    fun generate(prompt: String, listener: GenerateProgressListener) {
        if (nativePtr == 0L) {
            Log.e(TAG, "generate: nativePtr is 0")
            return
        }
        try {
            // submitNative синхронний — повертає метрики (prefill_time, decode_len тощо).
            // Текст генерується через listener.onProgress().
            val metrics = submitNative(nativePtr, prompt, keepHistory, listener)
            Log.e(TAG, "submitNative metrics: $metrics")
        } catch (t: Throwable) {
            Log.e(TAG, "generate failed: ${t.message}", t)
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
