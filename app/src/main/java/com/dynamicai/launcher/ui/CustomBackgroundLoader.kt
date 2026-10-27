package com.dynamicai.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CustomBackgroundLoader {

    private const val BG_FILE = "custom_bg.img"

    /**
     * Копіює зображення з URI у внутрішнє сховище додатку.
     * Повертає шлях з маркером версії (для примусового перезавантаження).
     */
    suspend fun saveFromUri(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(ctx.filesDir, BG_FILE)
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            android.util.Log.d("CustomBg", "saved ${file.length()} bytes to ${file.absolutePath}")
            "${file.absolutePath}?v=${System.currentTimeMillis()}"
        } catch (e: Throwable) {
            android.util.Log.e("CustomBg", "saveFromUri failed: ${e.message}", e)
            null
        }
    }

    suspend fun clear(ctx: Context) = withContext(Dispatchers.IO) {
        try { File(ctx.filesDir, BG_FILE).delete() } catch (_: Throwable) {}
    }

    suspend fun loadFromFile(filePath: String, targetWidth: Int = 720): ImageBitmap? =
        withContext(Dispatchers.IO) {
            if (filePath.isBlank()) return@withContext null
            try {
                val cleanPath = filePath.substringBefore("?v=")
                val file = File(cleanPath)
                if (!file.exists()) {
                    android.util.Log.w("CustomBg", "file not found: $cleanPath")
                    return@withContext null
                }
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: run {
                    android.util.Log.w("CustomBg", "decodeFile failed: $cleanPath")
                    return@withContext null
                }
                val scale = if (bitmap.width > targetWidth) targetWidth.toFloat() / bitmap.width else 1f
                val scaled = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * scale).toInt(),
                        (bitmap.height * scale).toInt(),
                        true
                    )
                } else bitmap
                android.util.Log.d("CustomBg", "loaded ${scaled.width}x${scaled.height}")
                scaled.asImageBitmap()
            } catch (e: Throwable) {
                android.util.Log.e("CustomBg", "loadFromFile failed: ${e.message}", e)
                null
            }
        }
}
