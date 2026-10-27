package com.dynamicai.launcher.ui

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

object BackdropBlur {

    @Volatile private var cached: ImageBitmap? = null
    @Volatile private var cachedWidth = 0

    fun get(ctx: Context, targetWidth: Int): ImageBitmap? {
        if (cached != null && cachedWidth >= targetWidth) return cached

        val bitmap = loadBitmap(ctx) ?: run {
            android.util.Log.w("BackdropBlur", "ALL METHODS FAILED — wallpaper is null")
            return null
        }

        val scale = if (bitmap.width > 720) 720f / bitmap.width else 1f
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else bitmap

        cached = scaled.asImageBitmap()
        cachedWidth = targetWidth
        return cached
    }

    private fun loadBitmap(ctx: Context): Bitmap? {
        val wm = WallpaperManager.getInstance(ctx.applicationContext)

        // Спосіб 1: getWallpaperFile(FLAG_SYSTEM) — найнадійніший, API 5+
        try {
            val file = wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)
            if (file != null) {
                val b = BitmapFactory.decodeFileDescriptor(file.fileDescriptor)
                val desc = if (b != null) "${b.width}x${b.height}" else "NULL"
                android.util.Log.d("BackdropBlur", "getWallpaperFile(SYSTEM) -> $desc")
                if (b != null) return b
            } else {
                android.util.Log.d("BackdropBlur", "getWallpaperFile(SYSTEM) -> null file")
            }
        } catch (e: Throwable) {
            android.util.Log.w("BackdropBlur", "getWallpaperFile(SYSTEM) failed: ${e.message}")
        }

        // Спосіб 2: getWallpaperFile(FLAG_LOCK)
        try {
            val file = wm.getWallpaperFile(WallpaperManager.FLAG_LOCK)
            if (file != null) {
                val b = BitmapFactory.decodeFileDescriptor(file.fileDescriptor)
                val desc = if (b != null) "${b.width}x${b.height}" else "NULL"
                android.util.Log.d("BackdropBlur", "getWallpaperFile(LOCK) -> $desc")
                if (b != null) return b
            } else {
                android.util.Log.d("BackdropBlur", "getWallpaperFile(LOCK) -> null file")
            }
        } catch (e: Throwable) {
            android.util.Log.w("BackdropBlur", "getWallpaperFile(LOCK) failed: ${e.message}")
        }

        // Спосіб 3: getDrawable() — старий API 5+
        try {
            val drawable = wm.getDrawable()
            if (drawable != null && drawable.intrinsicWidth > 0) {
                val w = (drawable.intrinsicWidth / 4).coerceAtLeast(200)
                val h = (drawable.intrinsicHeight / 4).coerceAtLeast(200)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                drawable.setBounds(0, 0, w, h)
                drawable.draw(c)
                android.util.Log.d("BackdropBlur", "getDrawable() -> ${w}x$h")
                return bmp
            } else {
                android.util.Log.d("BackdropBlur", "getDrawable() -> null or 0x0")
            }
        } catch (e: Throwable) {
            android.util.Log.w("BackdropBlur", "getDrawable() failed: ${e.message}")
        }

        return null
    }

    fun clear() {
        cached = null
        cachedWidth = 0
    }
}
