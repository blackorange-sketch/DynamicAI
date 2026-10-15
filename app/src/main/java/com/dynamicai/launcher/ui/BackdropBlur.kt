package com.dynamicai.launcher.ui

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

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
            // Зменшуємо у 4 рази — швидко + blur сам згладить
            val w = (srcW / 4).coerceAtLeast(200)
            val h = (srcH / 4).coerceAtLeast(200)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(c)
            cached = bmp.asImageBitmap()
            cachedWidth = targetWidth
            cached
        } catch (_: Throwable) { null }
    }

    fun clear() {
        cached = null
        cachedWidth = 0
    }
}
