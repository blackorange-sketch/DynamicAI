package com.dynamicai.launcher.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.kyant.backdrop.Backdrop

/**
 * Глобальний Backdrop для всього лаунчера.
 * Дозволяє будь-якому елементу (док, пошук, папки) брати розмитий фон.
 */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }
