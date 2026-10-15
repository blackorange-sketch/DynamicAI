package com.dynamicai.launcher.ui

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

/**
 * Раз на процес витягує шпалеру і розмиває.
 * Кеш у пам'яті — без повторного читання.
 */
object BackdropBlur {

    @Volatile private var cached: ImageBitmap? = null
    @Volatile private var cachedWidth = 0

    fun get(ctx: Context, targetWidth: Int): ImageBitmap? {
        if (cached != null && cachedWidth >= targetWidth) return cached
        return try {
            val wm = WallpaperManager.getInstance(ctx.applicationContext)
            val drawable = wm.drawable ?: return null
            val srcW = drawable.intrinsicWidth.coerceAtLeast(1)
            val srcH = drawable.intrinsicHeight.coerceAtLeast(1)
            val scale = (targetWidth.toFloat() / srcW).coerceAtMost(0.5f)  // зменшуємо для швидкості
            val w = (srcW * scale).toInt().coerceAtLeast(200)
            val h = (srcH * scale).toInt().coerceAtLeast(200)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(c)

            val blurred = if (Build.VERSION.SDK_INT >= 31) {
                applyRenderEffect(bmp, radius = 40f)
            } else {
                bmp  // без блюра, просто шпалера знизу
            }
            cached = blurred.asImageBitmap()
            cachedWidth = targetWidth
            cached
        } catch (_: Throwable) { null }
    }

    @androidx.annotation.RequiresApi(31)
    private fun applyRenderEffect(src: Bitmap, radius: Float): Bitmap {
        return try {
            val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            // RenderEffect blur не працює на Bitmap напряму — потрібен RenderNode
            // Спрощений варіант: downscale + upscale = імітація blur
            val small = Bitmap.createScaledBitmap(src, src.width / 6, src.height / 6, true)
            val scaled = Bitmap.createScaledBitmap(small, src.width, src.height, true)
            canvas.drawBitmap(scaled, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            if (small != src) small.recycle()
            if (scaled != src) scaled.recycle()
            out
        } catch (_: Throwable) { src }
    }

    fun clear() {
        cached = null
        cachedWidth = 0
    }
}
