package com.dynamicai.launcher.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Скріншот через Shizuku + shell команду screencap.
 * Читає PNG з stdout процесу (без проміжного файлу — SELinux не дає писати у app cache).
 */
class ScreenshotController(private val context: Context) {

    companion object {
        private const val TAG = "ScreenshotController"
    }

    @Volatile var lastError: String = ""

    suspend fun takeScreenshot(): Bitmap? = withContext(Dispatchers.IO) {
        lastError = ""
        try {
            if (!Shizuku.pingBinder()) {
                lastError = "Shizuku не запущено"
                return@withContext null
            }

            // screencap без -p → raw RGBA. З -p → PNG у stdout.
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
                arrayOf("screencap", "-p"),
                null,
                null
            ) as? rikka.shizuku.ShizukuRemoteProcess
                ?: run {
                    lastError = "newProcess returned null"
                    return@withContext null
                }

            val bytes = try {
                process.inputStream.readBytes()
            } catch (t: Throwable) {
                lastError = "read stdout: ${t.message}"
                return@withContext null
            } finally {
                try { process.waitFor() } catch (_: Throwable) {}
            }

            if (bytes.isEmpty()) {
                lastError = "empty stdout"
                return@withContext null
            }

            Log.d(TAG, "PNG bytes: ${bytes.size}")

            val bmp = try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (t: Throwable) {
                lastError = "decode: ${t.message}"
                null
            }

            if (bmp == null) {
                lastError = "decode returned null (size=${bytes.size})"
                Log.e(TAG, lastError)
            } else {
                Log.d(TAG, "Screenshot OK: ${bmp.width}x${bmp.height}")
            }
            bmp
        } catch (t: Throwable) {
            val cause = t.cause ?: t
            lastError = "${cause.javaClass.simpleName}: ${cause.message}"
            Log.e(TAG, "takeScreenshot failed: $lastError", cause)
            null
        }
    }
}
