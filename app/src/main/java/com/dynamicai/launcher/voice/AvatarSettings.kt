package com.dynamicai.launcher.voice

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

object AvatarSettings {
    private const val PREFS_NAME = "avatar_prefs"
    private const val KEY_SCALE = "scale"
    private const val KEY_OFFSET_Y = "offset_y"
    private const val KEY_OFFSET_X = "offset_x"

    var scale by mutableFloatStateOf(1.0f)
    var offsetY by mutableFloatStateOf(0f)
    var offsetX by mutableFloatStateOf(0f)

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        scale = prefs.getFloat(KEY_SCALE, 1.0f)
        offsetY = prefs.getFloat(KEY_OFFSET_Y, 0f)
        offsetX = prefs.getFloat(KEY_OFFSET_X, 0f)
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_SCALE, scale)
            .putFloat(KEY_OFFSET_Y, offsetY)
            .putFloat(KEY_OFFSET_X, offsetX)
            .apply()
    }
}
