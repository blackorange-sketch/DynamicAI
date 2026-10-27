package com.dynamicai.launcher.ui

import com.dynamicai.launcher.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
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
fun FolderManagerScreen(
    folders: List<Folder>,
    apps: List<AppInfo>,
    onAddApp: (folderId: String, pkg: String) -> Unit,
    onRemoveApp: (folderId: String, pkg: String) -> Unit,
    onDeleteFolder: (folderId: String) -> Unit,
    onCreateFolder: (name: String) -> Unit,
    onClose: () -> Unit
) {
    var expandedFolderId by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var addingToFolder by remember { mutableStateOf<Folder?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .statusBarsPadding()
    ) {
        // Заголовок
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = AppTheme.colors.textPrimary)
            }
            Text("Папки", color = AppTheme.colors.textPrimary, fontSize = 22.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, "Нова папка", tint = AppTheme.colors.accent)
            }
        }

        if (folders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Немає папок. Створи першу — тап на 📁 вгорі",
                    color = AppTheme.colors.textSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(folders, key = { it.id }) { folder ->
                    FolderCard(
                        folder = folder,
                        apps = apps.filter { it.packageName in folder.packageNames },
                        expanded = expandedFolderId == folder.id,
                        onToggleExpand = {
                            expandedFolderId = if (expandedFolderId == folder.id) null else folder.id
                        },
                        onAddApp = { addingToFolder = folder },
                        onRemoveApp = { pkg -> onRemoveApp(folder.id, pkg) },
                        onDelete = { onDeleteFolder(folder.id) }
                    )
                }
            }
        }
    }

    // Діалог створення папки
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; newFolderName = "" },
            title = { Text("Нова папка", color = AppTheme.colors.textPrimary) },
            text = {
                androidx.compose.foundation.text.BasicTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = AppTheme.colors.textPrimary, fontSize = 16.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.textPrimary),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppTheme.colors.textPrimary.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        if (newFolderName.isEmpty()) {
                            Text("Назва папки", color = Color.Gray, fontSize = 16.sp)
                        }
                        inner()
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            onCreateFolder(newFolderName)
                            showCreateDialog = false
                            newFolderName = ""
                        }
                    }
                ) { Text("Створити", color = AppTheme.colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; newFolderName = "" }) {
                    Text("Скасувати", color = AppTheme.colors.textPrimary)
                }
            },
            containerColor = AppTheme.colors.surface
        )
    }

    // Діалог вибору застосунку для додавання
    addingToFolder?.let { folder ->
        val availableApps = apps.filter { it.packageName !in folder.packageNames }
        AlertDialog(
            onDismissRequest = { addingToFolder = null },
            title = { Text("Додати до «${folder.name}»", color = AppTheme.colors.textPrimary) },
            text = {
                if (availableApps.isEmpty()) {
                    Text("Немає застосунків для додавання", color = Color.Gray, fontSize = 14.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                    ) {
                        items(availableApps, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(app.packageName) {
                                        detectTapGestures {
                                            onAddApp(folder.id, app.packageName)
                                            addingToFolder = null
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    bitmap = app.bitmap!!.asImageBitmap(),
                                    contentDescription = app.label,
                                    modifier = Modifier.size(36.dp),
                                    tint = Color.Unspecified
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(app.label, color = AppTheme.colors.textPrimary, fontSize = 15.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { addingToFolder = null }) {
                    Text("Скасувати", color = AppTheme.colors.textPrimary)
                }
            },
            containerColor = AppTheme.colors.surface
        )
    }
}

@Composable
private fun FolderCard(
    folder: Folder,
    apps: List<AppInfo>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onAddApp: () -> Unit,
    onRemoveApp: (String) -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        AppTheme.colors.surface.copy(alpha = 0.94f),
                        AppTheme.colors.surface.copy(alpha = 0.88f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        AppTheme.colors.textPrimary.copy(alpha = 0.35f),
                        AppTheme.colors.textPrimary.copy(alpha = 0.08f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        // Заголовок папки
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(folder.id) {
                    detectTapGestures { onToggleExpand() }
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = AppTheme.colors.accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(folder.name, color = AppTheme.colors.textPrimary, fontSize = 16.sp)
                Text(
                    "${folder.packageNames.size} застосунків",
                    color = AppTheme.colors.textSecondary, fontSize = 11.sp
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Видалити", tint = AppTheme.colors.danger, modifier = Modifier.size(20.dp))
            }
        }

        // Розгорнутий список
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                // Іконки застосунків
                if (apps.isEmpty()) {
                    Text(
                        "Порожня папка",
                        color = AppTheme.colors.textSecondary, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                } else {
                    apps.forEach { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                bitmap = app.bitmap!!.asImageBitmap(),
                                contentDescription = app.label,
                                modifier = Modifier.size(32.dp),
                                tint = Color.Unspecified
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, color = AppTheme.colors.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onRemoveApp(app.packageName) }) {
                                Icon(Icons.Default.Delete, "Прибрати", tint = AppTheme.colors.danger, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Кнопка додати
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppTheme.colors.textPrimary.copy(alpha = 0.06f))
                        .pointerInput(Unit) {
                            detectTapGestures { onAddApp() }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, "Додати", tint = AppTheme.colors.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Додати застосунок", color = AppTheme.colors.accent, fontSize = 13.sp)
                }
            }
        }
    }
}
