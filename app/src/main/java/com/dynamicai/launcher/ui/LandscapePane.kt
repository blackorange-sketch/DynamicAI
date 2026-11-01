package com.dynamicai.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dynamicai.launcher.model.AppInfo
import com.dynamicai.launcher.ui.theme.AppTheme

/** Права панель ландшафтного режиму: усі додатки за алфавітом, завжди на екрані. */
@Composable
fun LandscapeAppPane(
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onLongClick: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val sorted = remember(apps) { apps.sortedBy { it.label.lowercase() } }
    LazyVerticalGrid(
        columns = gridCells(),
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 8.dp, end = 12.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(AppTheme.colors.textPrimary.copy(alpha = 0.06f)),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = gridGapH(),
        verticalArrangement = gridGapV()
    ) {
        items(sorted, key = { it.packageName }) { app ->
            AppGridItem(app = app, onLongClick = { onLongClick(app) }, onTap = { onLaunch(app) })
        }
    }
}
