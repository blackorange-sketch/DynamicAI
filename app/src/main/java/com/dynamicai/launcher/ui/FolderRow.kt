package com.dynamicai.launcher.ui

import com.dynamicai.launcher.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.dynamicai.launcher.folder.Folder
import com.dynamicai.launcher.model.AppInfo

@Composable
fun FolderRow(
    folder: Folder,
    apps: List<AppInfo>,
    iconSizeDp: Int,
    onLaunch: (AppInfo) -> Unit,
    onLongPressApp: (AppInfo, Folder) -> Unit,
    onLongPressHeader: (Folder) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Заголовок папки
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onLongPressHeader(folder) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppTheme.colors.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = AppTheme.colors.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                folder.name,
                color = AppTheme.colors.textPrimary,
                fontSize = 16.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${apps.size}",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(4.dp))

        // Іконки в рядок
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            apps.take(5).forEach { app ->
                FolderAppIcon(
                    app = app,
                    sizeDp = iconSizeDp,
                    onTap = { onLaunch(app) },
                    onLongPress = { onLongPressApp(app, folder) }
                )
            }
        }
    }
}

@Composable
private fun FolderAppIcon(
    app: AppInfo,
    sizeDp: Int,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            bitmap = app.bitmap!!.asImageBitmap(),
            contentDescription = app.label,
            tint = Color.Unspecified,
            modifier = Modifier.size(sizeDp.dp)
        )
    }
}
