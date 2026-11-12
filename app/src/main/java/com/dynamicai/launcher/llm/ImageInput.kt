package com.dynamicai.launcher.llm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Готує зображення для хмарної моделі: зменшує до ~1568 px по довшій стороні й стискає в JPEG. */
object ImageInput {
    private const val MAX_SIDE = 1568

    suspend fun loadJpeg(ctx: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val longSide = maxOf(bounds.outWidth, bounds.outHeight)
            if (longSide <= 0) return@withContext null
            var sample = 1
            while (longSide / sample > MAX_SIDE * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@withContext null
            toJpeg(bmp)
        } catch (_: Throwable) { null }
    }

    fun toJpeg(src: Bitmap): ByteArray {
        val longSide = maxOf(src.width, src.height)
        val bmp = if (longSide > MAX_SIDE) {
            val k = MAX_SIDE.toFloat() / longSide
            Bitmap.createScaledBitmap(src, (src.width * k).toInt().coerceAtLeast(1), (src.height * k).toInt().coerceAtLeast(1), true)
        } else src
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return out.toByteArray()
    }
}
