package com.dynamicai.launcher.ui

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette

/**
 * Витягує домінантний колір з іконки застосунку.
 * Кешує результат у мапі.
 */
object IconColors {

    private val cache = mutableMapOf<String, Color>()

    fun extract(packageName: String, icon: Drawable?): Color {
        cache[packageName]?.let { return it }

        if (icon == null) {
            return Color(0xFF2A2A2A).also { cache[packageName] = it }
        }

        return try {
            val bitmap = icon.toBitmap(96, 96)
            val palette = Palette.from(bitmap).generate()
            val color = palette.getVibrantColor(
                palette.getMutedColor(
                    palette.getDominantColor(0xFF2A2A2A.toInt())
                )
            )
            val result = Color(color)
            cache[packageName] = result
            result
        } catch (t: Throwable) {
            Color(0xFF2A2A2A).also { cache[packageName] = it }
        }
    }
}
