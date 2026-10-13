package com.dynamicai.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
        // ==== Таби (без "Усі") ====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            folders.forEach { folder ->
                FolderTab(
                    label = folder.name,
                    selected = folder.id == selectedFolderId,
                    onTap = { onSelectFolder(folder) }
                )
            }
        }

        // ==== Заголовок обраної папки ====
        AnimatedVisibility(
            visible = selectedFolder != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (selectedFolder != null) {
                Text(
                    text = selectedFolder.name,
                    color = Color.White,
                    fontSize = 24.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 8.dp, bottom = 6.dp)
                )
            }
        }

        // ==== Список застосунків ====
        AnimatedVisibility(
            visible = selectedFolder != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(top = 2.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(folderApps, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(app.packageName) {
                                detectTapGestures { onLaunchApp(app) }
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(
                            bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
                            contentDescription = app.label,
                            modifier = Modifier.size(iconSizeDp.dp),
                            tint = Color.Unspecified
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(app.label, color = Color.White, fontSize = fontSizeSp.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderTab(
    label: String,
    selected: Boolean,
    onTap: () -> Unit
) {
    Text(
        text = label,
        color = if (selected) Color.White else Color.Gray,
        fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color(0xFF2196F3) else Color(0xFF1A1A1A))
            .pointerInput(label) {
                detectTapGestures { onTap() }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
