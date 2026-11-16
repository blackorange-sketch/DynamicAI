package com.dynamicai.launcher.llm

import android.content.Context
import android.util.Log
import com.dynamicai.launcher.data.SettingsStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Рушій llama.cpp: будь-яка модель у форматі GGUF (Qwen, Gemma, Llama, Phi…), рахує на CPU.
 * Промпт отримує «сирим», як MNN, і сам обгортає його в chat template моделі.
 */
class LlamaCppLlmEngine(private val context: Context) : LlmEngine {
    /** Та сама семантика промпту, що й у MNN: без ручних <start_of_turn>, шаблон застосовує рушій. */
    override val isMnn: Boolean get() = true

    companion object {
        private const val TAG = "LlamaCppEngine"
    }

    @Volatile private var handle: Long = 0L
    private var modelPath: String = ""
    private val nativeLock = ReentrantLock()
    private val initMutex = Mutex()

    @Volatile var lastUsedAt: Long = 0L
        private set
    @Volatile var lastError: String = ""

    override val isReady: Boolean get() = handle != 0L

    suspend fun initialize(file: File): Boolean = withContext(Dispatchers.IO) {
        initMutex.withLock {
            if (isReady && modelPath == file.absolutePath) return@withLock true
            close()
            if (!file.exists() || file.length() < 1_000_000L) {
                lastError = "Файл моделі не знайдено: ${file.name}"
                return@withLock false
            }
            if (!isGguf(file)) {
                lastError = "${file.name} — не GGUF-файл"
                return@withLock false
            }
            if (!LlamaCppNative.ensureLoaded()) {
                lastError = LlamaCppNative.loadError
                return@withLock false
            }
            val s = SettingsStorage(context).load()
            val threads = s.llamaThreads.coerceIn(1, 8)
            val ctx = s.llamaContext.coerceIn(1024, 16384)
            val t0 = System.currentTimeMillis()
            Log.e(TAG, "Loading ${file.name} (${file.length() / 1_000_000} MB), threads=$threads, ctx=$ctx")
            val h = try {
                nativeLock.withLock { LlamaCppNative.nativeLoad(file.absolutePath.toByteArray(), ctx, threads) }
            } catch (t: Throwable) {
                lastError = "nativeLoad: ${t.message}"
                Log.e(TAG, lastError, t)
                0L
            }
            if (h == 0L) {
                if (lastError.isEmpty()) lastError = "Не вдалося завантажити ${file.name} (див. logcat: llama.cpp)"
                return@withLock false
            }
            handle = h
            modelPath = file.absolutePath
            lastUsedAt = System.currentTimeMillis()
            lastError = ""
            Log.e(TAG, "✅ llama.cpp ready in ${System.currentTimeMillis() - t0} ms: ${info().lineSequence().firstOrNull()}")
            true
        }
    }

    /** Модель + можливості CPU (для діагностики в налаштуваннях). */
    fun info(): String = try {
        String(LlamaCppNative.nativeInfo(handle), Charsets.UTF_8)
    } catch (_: Throwable) { "" }

    private fun isGguf(file: File): Boolean = try {
        file.inputStream().use { val b = ByteArray(4); it.read(b) == 4 && String(b, Charsets.US_ASCII) == "GGUF" }
    } catch (_: Throwable) { false }

    private fun formatPrompt(prompt: String): String {
        val name = File(modelPath).name.lowercase()
        // Qwen3 (не 2507-Instruct) за замовчуванням «думає» — вимикаємо, як і в MNN
        return if (name.contains("qwen3") && !name.contains("instruct-2507")) "$prompt\n/no_think" else prompt
    }

    private fun generate(prompt: String, onToken: ((String) -> Unit)?, job: Job?): String? {
        val h = handle
        if (h == 0L) { lastError = "llama.cpp не готовий"; return null }
        lastUsedAt = System.currentTimeMillis()
        val s = SettingsStorage(context).load()
        val buffer = StringBuilder()
        return try {
            val n = nativeLock.withLock {
                if (handle != h) return null
                LlamaCppNative.nativeGenerate(
                    h, formatPrompt(prompt).toByteArray(Charsets.UTF_8),
                    s.maxResponseTokens.coerceIn(32, 2048), s.llmTemperature, true
                ) { bytes ->
                    val piece = String(bytes, Charsets.UTF_8)
                    buffer.append(piece)
                    if (onToken != null) try { onToken(piece) } catch (_: Throwable) {}
                    // Скасована корутина (користувач закрив чат / нове питання) — зупиняємо генерацію
                    job?.isActive == false
                }
            }
            if (n < 0) {
                lastError = "nativeGenerate error $n"
                Log.e(TAG, lastError)
                if (buffer.isEmpty()) return null
            }
            Log.e(TAG, "generated $n tokens, ${buffer.length} chars")
            MnnLlmEngine.stripThinking(buffer.toString())
        } catch (t: Throwable) {
            lastError = "generate failed: ${t.message}"
            Log.e(TAG, lastError, t)
            if (buffer.isNotEmpty()) MnnLlmEngine.stripThinking(buffer.toString()) else null
        }
    }

    override suspend fun complete(prompt: String): String? = withContext(Dispatchers.IO) { generate(prompt, null, currentCoroutineContext()[Job]) }

    override suspend fun streamComplete(prompt: String, onToken: (String) -> Unit): String? =
        withContext(Dispatchers.IO) { generate(prompt, onToken, currentCoroutineContext()[Job]) }

    /** Перериває поточну генерацію (можна викликати з будь-якого потоку). */
    fun stop() {
        val h = handle
        if (h != 0L) try { LlamaCppNative.nativeStop(h) } catch (_: Throwable) {}
    }

    fun close() {
        stop()
        nativeLock.withLock {
            val h = handle
            handle = 0L
            modelPath = ""
            if (h != 0L) try { LlamaCppNative.nativeFree(h) } catch (t: Throwable) { Log.w(TAG, "free failed: ${t.message}") }
        }
    }
}
