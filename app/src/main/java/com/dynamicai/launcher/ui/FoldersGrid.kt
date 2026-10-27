package com.dynamicai.launcher.ui

import com.dynamicai.launcher.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
fun FoldersGrid(
    folders: List<Folder>,
    apps: List<AppInfo>,
    iconSizeDp: Int,
    onOpenFolder: (Folder) -> Unit,
    onLongPress: (Folder) -> Unit,
    modifier: Modifier = Modifier
) {
    if (folders.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        folders.forEach { folder ->
            FolderCard(
                folder = folder,
                apps = apps.filter { it.packageName in folder.packageNames },
                iconSizeDp = iconSizeDp,
                onTap = { onOpenFolder(folder) },
                onLongPress = { onLongPress(folder) }
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun FolderCard(
    folder: Folder,
    apps: List<AppInfo>,
    iconSizeDp: Int,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.surface)
            .pointerInput(folder.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() }
                )
            }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                folder.name,
                color = AppTheme.colors.textPrimary,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${apps.size}",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            apps.take(4).forEach { app ->
                androidx.compose.material3.Icon(
                    bitmap = app.bitmap!!.asImageBitmap(),
                    contentDescription = app.label,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(iconSizeDp.dp)
                )
            }
            if (apps.size > 4) {
                Box(
                    modifier = Modifier
                        .size(iconSizeDp.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppTheme.colors.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+${apps.size - 4}", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}
