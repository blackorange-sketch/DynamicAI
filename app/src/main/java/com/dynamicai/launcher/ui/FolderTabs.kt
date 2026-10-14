package com.dynamicai.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
            enter = fadeIn(animationSpec = tween(180)) +
                    expandVertically(
                        animationSpec = tween(220),
                        expandFrom = Alignment.Bottom
                    ),
            exit = fadeOut(animationSpec = tween(120)) +
                    shrinkVertically(
                        animationSpec = tween(180),
                        shrinkTowards = Alignment.Bottom
                    )
        ) {
            val cols = remember(iconSizeDp) {
                GridCells.Adaptive(minSize = (iconSizeDp + 28).dp)
            }
            LazyVerticalGrid(
                columns = cols,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xCC0F0F0F))
                    .border(
                        width = 0.5.dp,
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                gridItems(folderApps, key = { it.packageName }) { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(app.packageName) {
                                detectTapGestures { onLaunchApp(app) }
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size((iconSizeDp + 12).dp)
                                .clip(RoundedCornerShape((iconSizeDp / 2 + 6).dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.10f),
                                            Color.White.copy(alpha = 0.03f)
                                        )
                                    )
                                )
                                .border(
                                    width = 0.5.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.22f),
                                            Color.White.copy(alpha = 0.05f)
                                        )
                                    ),
                                    shape = RoundedCornerShape((iconSizeDp / 2 + 6).dp)
                                )
                        ) {
                            androidx.compose.material3.Icon(
                                bitmap = app.bitmap!!.asImageBitmap(),
                                contentDescription = app.label,
                                modifier = Modifier.size(iconSizeDp.dp),
                                tint = Color.Unspecified
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = app.label,
                            color = Color.White,
                            fontSize = (fontSizeSp - 3).coerceAtLeast(10).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp)
                        )
                    }
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
        color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
        fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.verticalGradient(
                    if (selected) listOf(
                        Color(0xFF64B5F6).copy(alpha = 0.6f),
                        Color(0xFF2196F3).copy(alpha = 0.3f)
                    ) else listOf(
                        Color(0xE61F1F1F),
                        Color(0xCC0D0D0D)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    if (selected) listOf(
                        Color(0xFF64B5F6).copy(alpha = 0.7f),
                        Color(0xFF64B5F6).copy(alpha = 0.2f)
                    ) else listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.05f)
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
