package com.dynamicai.launcher.llm

import android.content.Context
import android.util.Log
import com.alibaba.mnnllm.android.llm.GenerateProgressListener
import com.alibaba.mnnllm.android.llm.LlmSession
import com.dynamicai.launcher.data.LauncherSettings
import com.dynamicai.launcher.data.SettingsStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Реалізація LlmEngine через MNN (libmnnllmapp.so + LlmSession).
 * Модель — це ПАПКА з файлами: llm.mnn, llm.mnn.json, llm.mnn.weight, llm_config.json, config.json, tokenizer.txt.
 */
class MnnLlmEngine(private val context: Context) : LlmEngine {

    companion object {
        private const val TAG = "MnnLlmEngine"

        /** Qwen 2.5 chat template (з llm_config.json) */
        private fun formatQwenPrompt(prompt: String): String {
            return "<|im_start|>user\n$prompt<|im_end|>\n<|im_start|>assistant\n"
        }
    }

    private var session: LlmSession? = null

    @Volatile
    var lastUsedAt: Long = 0L
        private set

    @Volatile
    var lastError: String = ""

    private var currentSettings: LauncherSettings = LauncherSettings()

    override val isReady: Boolean get() = session?.isReady() == true

    /**
     * @param modelDir папка з моделлю (містить llm.mnn тощо)
     */
    suspend fun initialize(modelDir: File): Boolean = withContext(Dispatchers.IO) {
        if (isReady) return@withContext true
        if (!modelDir.exists() || !modelDir.isDirectory) {
            lastError = "Папка моделі не знайдена: ${modelDir.absolutePath}"
            Log.e(TAG, lastError)
            return@withContext false
        }
        if (!File(modelDir, "llm.mnn").exists() || !File(modelDir, "llm.mnn.weight").exists()) {
            lastError = "У папці немає llm.mnn / llm.mnn.weight"
            Log.e(TAG, lastError)
            return@withContext false
        }

        // Логуємо вміст папки моделі
        Log.e(TAG, "=== Файли в ${modelDir.absolutePath} ===")
        try {
            modelDir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                Log.e(TAG, "  ${f.name}: ${f.length()} bytes")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "listFiles failed: ${t.message}")
        }

        // Звільняємо попередній engine
        try { session?.release() } catch (_: Throwable) {}
        session = null
        System.gc()

        currentSettings = SettingsStorage(context).load()

        // Мапимо вибір з UI → MNN backend_type + fallback
        val backends = when (currentSettings.llmBackend) {
            "gpu" -> listOf("opencl")
            "cpu" -> listOf("cpu")
            "npu" -> listOf("qnn")
            "auto", "" -> listOf("opencl", "cpu")
            else -> listOf("opencl", "cpu")
        }
        Log.e(TAG, "initialize: dir=${modelDir.absolutePath}, backends=$backends")

        for (backend in backends) {
            Log.e(TAG, "Спроба backend=$backend")
            try {
                val s = LlmSession(
                    modelId = modelDir.name,
                    configDir = modelDir,
                    backendType = backend,
                    maxNewTokens = 512
                )
                val ok = s.load()
                if (ok) {
                    session = s
                    lastUsedAt = System.currentTimeMillis()
                    lastError = ""
                    Log.e(TAG, "✅ MNN engine ready (backend=$backend)")
                    return@withContext true
                } else {
                    lastError = "LlmSession.load() failed (backend=$backend)"
                    Log.w(TAG, "❌ $lastError")
                }
            } catch (t: Throwable) {
                lastError = "backend=$backend exception: ${t.javaClass.simpleName}: ${t.message}"
                Log.w(TAG, "❌ $lastError", t)
            }
        }
        Log.e(TAG, "❌ Всі backend-и провалились")
        return@withContext false
    }

    override suspend fun complete(prompt: String): String? = withContext(Dispatchers.IO) {
        val s = session
        if (s == null || !s.isReady()) {
            lastError = "MNN engine not ready"
            return@withContext null
        }
        lastUsedAt = System.currentTimeMillis()
        val buffer = StringBuilder()
        val formattedPrompt = formatQwenPrompt(prompt)
        Log.e(TAG, "complete: formatted prompt (${formattedPrompt.length} chars), first 100: ${formattedPrompt.take(100)}")
        return@withContext try {
            s.generate(formattedPrompt, object : GenerateProgressListener {
                override fun onProgress(progress: String?): Boolean {
                    if (!progress.isNullOrEmpty()) {
                        buffer.append(progress)
                    }
                    return true  // продовжити генерацію
                }
            })
            Log.e(TAG, "complete: generated ${buffer.length} chars")
            buffer.toString()
        } catch (t: Throwable) {
            lastError = "complete failed: ${t.message}"
            Log.e(TAG, lastError, t)
            null
        }
    }

    override suspend fun streamComplete(
        prompt: String,
        onToken: (String) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        val s = session
        if (s == null || !s.isReady()) {
            lastError = "MNN engine not ready"
            return@withContext null
        }
        lastUsedAt = System.currentTimeMillis()
        val buffer = StringBuilder()
        val formattedPrompt = formatQwenPrompt(prompt)
        return@withContext try {
            s.generate(formattedPrompt, object : GenerateProgressListener {
                override fun onProgress(progress: String?): Boolean {
                    if (!progress.isNullOrEmpty()) {
                        buffer.append(progress)
                        try { onToken(progress) } catch (_: Throwable) {}
                    }
                    return true
                }
            })
            Log.e(TAG, "streamComplete: generated ${buffer.length} chars")
            buffer.toString()
        } catch (t: Throwable) {
            lastError = "streamComplete failed: ${t.message}"
            Log.e(TAG, lastError, t)
            if (buffer.isNotEmpty()) buffer.toString() else null
        }
    }

    fun close() {
        try {
            session?.release()
        } catch (t: Throwable) {
            Log.w(TAG, "close failed: ${t.message}")
        }
        session = null
    }
}
