package com.dynamicai.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.dynamicai.launcher.data.LauncherSettings

/**
 * ЄДИНЕ джерело вигляду іконок у всьому лаунчері (список, шторка, папки, пошук, оверлей, док).
 * Усі екрани читають значення лише звідси (LocalGridStyle), а не з окремих параметрів.
 */
@Immutable
data class GridStyle(
    val listIconDp: Int = 48,
    val dockIconDp: Int = 48,
    val gridIconDp: Int = 56,
    val fontSp: Int = 18,
    val platePadDp: Int = 6,
    val gapHDp: Int = 4,
    val gapVDp: Int = 12,
    val listGapDp: Int = 6,
    val dockGapDp: Int = 6,
    val dockCornerDp: Int = 26,
    val dockOpacity: Float = 1f
) {
    /** Ширина клітинки = підкладка + місце під підпис; відступ між клітинками = gapHDp точно. */
    val cellWidthDp: Int get() = gridIconDp + platePadDp + 14
}

val LocalGridStyle = compositionLocalOf { GridStyle() }

fun LauncherSettings.toGridStyle(): GridStyle =
    if (uniformIcons) GridStyle(
        listIconDp = menuIconSizeDp, dockIconDp = menuIconSizeDp, gridIconDp = menuIconSizeDp,
        fontSp = fontSizeSp, platePadDp = iconPlatePadDp, gapHDp = gridGapHDp, gapVDp = gridGapVDp, listGapDp = listGapDp,
        dockGapDp = dockGapDp, dockCornerDp = dockCornerDp, dockOpacity = dockOpacityPercent / 100f
    ) else GridStyle(
        listIconDp = iconSizeDp, dockIconDp = dockIconSizeDp, gridIconDp = menuIconSizeDp,
        fontSp = fontSizeSp, platePadDp = iconPlatePadDp, gapHDp = gridGapHDp, gapVDp = gridGapVDp, listGapDp = listGapDp,
        dockGapDp = dockGapDp, dockCornerDp = dockCornerDp, dockOpacity = dockOpacityPercent / 100f
    )

@Composable
fun gridCells(): GridCells = GridCells.FixedSize(LocalGridStyle.current.cellWidthDp.dp)

@Composable
fun gridGapH(): Arrangement.Horizontal =
    Arrangement.spacedBy(LocalGridStyle.current.gapHDp.dp, Alignment.CenterHorizontally)

@Composable
fun gridGapV(): Arrangement.Vertical = Arrangement.spacedBy(LocalGridStyle.current.gapVDp.dp)

/** Швидкість анімацій у відсотках (0 = без анімацій). */
val LocalAnimSpeed = compositionLocalOf { 100 }

/** Тривалість базової анімації з урахуванням налаштування швидкості. */
@Composable
fun animMs(baseMs: Int): Int {
    val speed = LocalAnimSpeed.current
    return if (speed <= 0) 0 else (baseMs * 100f / speed).toInt()
}
