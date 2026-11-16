package com.dynamicai.launcher.llm

import android.util.Log
import java.io.File

/** JNI-міст до llama.cpp (libdynllama.so, збирається з app/src/main/cpp). Рядки ходять як UTF-8 байти. */
object LlamaCppNative {
    private const val TAG = "LlamaCppNative"

    /** Колбек токенів. Повертає true, щоб зупинити генерацію. Ім'я методу шукає нативний код — не перейменовувати. */
    fun interface TokenCallback {
        fun onBytes(bytes: ByteArray): Boolean
    }

    /** Бібліотека зібрана з i8mm (Armv8.6+/Armv9). На старіших ядрах вона впала б з SIGILL, тож перевіряємо заздалегідь. */
    val cpuSupported: Boolean by lazy {
        try {
            File("/proc/cpuinfo").readLines()
                .filter { it.startsWith("Features") }
                .let { lines -> lines.isNotEmpty() && lines.all { l -> " i8mm" in l && " asimddp" in l } }
        } catch (_: Throwable) { false }
    }

    @Volatile private var loadState = 0  // 0 — ще ні, 1 — ок, -1 — помилка
    var loadError: String = ""
        private set

    fun ensureLoaded(): Boolean {
        if (loadState == 1) return true
        if (loadState == -1) return false
        synchronized(this) {
            if (loadState != 0) return loadState == 1
            if (!cpuSupported) {
                loadError = "Процесор не підтримує i8mm/dotprod — llama.cpp недоступний на цьому пристрої"
                loadState = -1
                return false
            }
            return try {
                System.loadLibrary("dynllama")
                loadState = 1
                true
            } catch (t: Throwable) {
                loadError = "libdynllama: ${t.message}"
                Log.e(TAG, loadError, t)
                loadState = -1
                false
            }
        }
    }

    external fun nativeLoad(path: ByteArray, nCtx: Int, nThreads: Int): Long
    external fun nativeGenerate(
        handle: Long, prompt: ByteArray, maxTokens: Int, temperature: Float,
        useTemplate: Boolean, callback: TokenCallback
    ): Int
    external fun nativeStop(handle: Long)
    external fun nativeInfo(handle: Long): ByteArray
    external fun nativeFree(handle: Long)
}
