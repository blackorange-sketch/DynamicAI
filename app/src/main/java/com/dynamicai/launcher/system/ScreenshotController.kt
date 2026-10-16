package com.dynamicai.launcher.system

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.File

/**
 * Скріншот через Shizuku (пряме копіювання фреймбуфера).
 * Fallback — Android MediaProjection (потребує дозволу).
 */
class ScreenshotController(private val context: Context) {

    companion object {
        private const val TAG = "ScreenshotController"
    }

    @Volatile var lastError: String = ""

    /**
     * Спроба зробити скріншот через Shizuku + shell команду screencap.
     * Використовує reflection до Shizuku.newProcess (прихований API).
     */
    suspend fun takeScreenshot(): File? = withContext(Dispatchers.IO) {
        lastError = ""
        try {
            if (!Shizuku.pingBinder()) {
                lastError = "Shizuku не запущено"
                return@withContext null
            }

            val outputFile = File(
                context.cacheDir,
                "screenshot_${System.currentTimeMillis()}.png"
            )

            // Викликаємо Shizuku.newProcess через reflection
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val newProcessMethod = shizukuClass.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true

            val process = newProcessMethod.invoke(
                null,
                arrayOf("screencap", "-p", outputFile.absolutePath),
                null,
                null
            ) as? rikka.shizuku.ShizukuRemoteProcess
                ?: run {
                    lastError = "newProcess returned null"
                    return@withContext null
                }

            val exit = process.waitFor()
            if (exit == 0 && outputFile.exists()) {
                Log.d(TAG, "Screenshot saved: ${outputFile.absolutePath}")
                outputFile
            } else {
                lastError = "screencap exit=$exit"
                null
            }
        } catch (t: Throwable) {
            val cause = t.cause ?: t
            lastError = "${cause.javaClass.simpleName}: ${cause.message}"
            Log.e(TAG, "takeScreenshot failed: $lastError", cause)
            null
        }
    }
}
