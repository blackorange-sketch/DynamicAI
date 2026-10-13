package com.dynamicai.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
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
        // ==== Список застосунків (зверху) ====
        AnimatedVisibility(
            visible = selectedFolder != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .padding(bottom = 4.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(folderApps, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.06f),
                                        Color.White.copy(alpha = 0.015f)
                                    )
                                )
                            )
                            .pointerInput(app.packageName) {
                                detectTapGestures { onLaunchApp(app) }
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Glass-рамка навколо іконки
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size((iconSizeDp + 8).dp)
                                .clip(RoundedCornerShape((iconSizeDp / 2 + 4).dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.08f),
                                            Color.White.copy(alpha = 0.02f)
                                        )
                                    )
                                )
                                .border(
                                    width = 0.5.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.2f),
                                            Color.White.copy(alpha = 0.04f)
                                        )
                                    ),
                                    shape = RoundedCornerShape((iconSizeDp / 2 + 4).dp)
                                )
                        ) {
                            androidx.compose.material3.Icon(
                                bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
                                contentDescription = app.label,
                                modifier = Modifier.size(iconSizeDp.dp),
                                tint = Color.Unspecified
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, color = Color.White, fontSize = fontSizeSp.sp)
                    }
                }
            }
        }

        // ==== Таби (знизу) ====
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
