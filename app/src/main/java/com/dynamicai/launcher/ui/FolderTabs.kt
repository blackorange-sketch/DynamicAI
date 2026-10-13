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
    onSelectFolder: (Folder?) -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    if (folders.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        // ==== Таби ====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Таб "Усі"
            FolderTab(
                label = "Усі",
                icon = null,
                selected = selectedFolderId == null,
                onTap = { onSelectFolder(null) }
            )

            folders.forEach { folder ->
                val firstApp = apps.firstOrNull { it.packageName in folder.packageNames }
                FolderTab(
                    label = folder.name,
                    icon = firstApp?.icon,
                    selected = selectedFolderId == folder.id,
                    onTap = { onSelectFolder(folder) }
                )
            }
        }

        // ==== Список застосунків ====
        AnimatedVisibility(
            visible = selectedFolderId != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val folder = folders.firstOrNull { it.id == selectedFolderId }
            if (folder != null) {
                val folderApps = apps.filter { it.packageName in folder.packageNames }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .padding(top = 4.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
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
                            Text(app.label, color = Color.White, fontSize = 17.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderTab(
    label: String,
    icon: android.graphics.drawable.Drawable?,
    selected: Boolean,
    onTap: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color(0xFF2196F3) else Color(0xFF1A1A1A))
            .pointerInput(label) {
                detectTapGestures { onTap() }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(
                bitmap = icon.toBitmap(64, 64).asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.Unspecified
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            color = Color.White,
            fontSize = 13.sp
        )
    }
}
