package com.dynamicai.launcher.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WallpaperUtils {

    suspend fun setStaticWallpaper(ctx: Context, uri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val input = ctx.contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(Exception("Не вдалось відкрити файл"))
                val bitmap = BitmapFactory.decodeStream(input)?.let { decodeDownsampled(it, 2000) }
                    ?: return@withContext Result.failure(Exception("Не вдалось декодувати зображення"))
                input.close()

                val wm = WallpaperManager.getInstance(ctx)
                wm.setBitmap(bitmap)
                Result.success(Unit)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    private fun decodeDownsampled(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxDim && h <= maxDim) return src
        val scale = maxDim.toFloat() / maxOf(w, h)
        val newW = (w * scale).toInt()
        val newH = (h * scale).toInt()
        return Bitmap.createScaledBitmap(src, newW, newH, true)
    }
}
