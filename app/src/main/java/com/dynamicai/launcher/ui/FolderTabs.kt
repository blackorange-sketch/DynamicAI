package com.dynamicai.launcher.ui

import com.dynamicai.launcher.ui.theme.AppTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.folder.Folder
import com.dynamicai.launcher.model.AppInfo

@Composable
fun FolderTabs(
    folders: List<Folder>,
    apps: List<AppInfo>,
    selectedFolderId: String?,
    iconSizeDp: Int,
    fontSizeSp: Int,
    onSelectFolder: (Folder) -> Unit,
    onLongPressFolder: (Folder) -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    if (folders.isEmpty()) return

    val selectedFolder = folders.firstOrNull { it.id == selectedFolderId }
    val folderApps: List<AppInfo> = remember(selectedFolder, apps) {
        if (selectedFolder == null) emptyList()
        else apps.filter { it.packageName in selectedFolder.packageNames }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = selectedFolder != null,
            enter = expandVertically(
                animationSpec = tween(200),
                expandFrom = Alignment.Bottom
            ),
            exit = shrinkVertically(
                animationSpec = tween(180),
                shrinkTowards = Alignment.Bottom
            )
        ) {
            val cols = gridCellsForIcons(iconSizeDp)
            LazyVerticalGrid(
                columns = cols,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(AppTheme.colors.glassBackgroundAlt)
                    .border(
                        width = 0.5.dp,
                        color = AppTheme.colors.textPrimary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = gridGapH(),
                verticalArrangement = Arrangement.spacedBy(LocalGridGapV.current.dp)
            ) {
                gridItems(folderApps, key = { it.packageName }) { app ->
                    // Та сама плитка, що у шторці: однакові підкладка, шрифт і відступи
                    AppGridItem(app, iconSizeDp, fontSizeSp, onTap = { onLaunchApp(app) })
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            folders.forEach { folder ->
                FolderTab(
                    label = folder.name,
                    selected = folder.id == selectedFolderId,
                    onTap = { onSelectFolder(folder) },
                    onLongPress = { onLongPressFolder(folder) }
                )
            }
        }
    }
}

@Composable
private fun FolderTab(
    label: String,
    selected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Text(
        text = label,
        color = if (selected) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary,
        fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.verticalGradient(
                    if (selected) listOf(
                        AppTheme.colors.accent.copy(alpha = 0.6f),
                        AppTheme.colors.accentDark.copy(alpha = 0.3f)
                    ) else listOf(
                        AppTheme.colors.glassBackground,
                        AppTheme.colors.glassBackgroundAlt
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    if (selected) listOf(
                        AppTheme.colors.accent.copy(alpha = 0.7f),
                        AppTheme.colors.accent.copy(alpha = 0.2f)
                    ) else listOf(
                        AppTheme.colors.glassBorder,
                        AppTheme.colors.textPrimary.copy(alpha = 0.05f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .pointerInput(label) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() }
                )
            }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
