package com.dynamicai.launcher.model

import android.graphics.Bitmap
import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val isSystem: Boolean,
    val bitmap: Bitmap? = null,
    val category: String = "other"
)
