package com.dynamicai.launcher.ui

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
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
                Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White)
            }
            Text("Папки", color = Color.White, fontSize = 22.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, "Нова папка", tint = Color(0xFF64B5F6))
            }
        }

        if (folders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Немає папок. Створи першу — тап на 📁 вгорі",
                    color = Color.White.copy(alpha = 0.5f),
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
            title = { Text("Нова папка", color = Color.White) },
            text = {
                androidx.compose.foundation.text.BasicTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
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
                ) { Text("Створити", color = Color(0xFF64B5F6)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; newFolderName = "" }) {
                    Text("Скасувати", color = Color.White)
                }
            },
            containerColor = Color(0xFF1A1A1A)
        )
    }

    // Діалог вибору застосунку для додавання
    addingToFolder?.let { folder ->
        val availableApps = apps.filter { it.packageName !in folder.packageNames }
        AlertDialog(
            onDismissRequest = { addingToFolder = null },
            title = { Text("Додати до «${folder.name}»", color = Color.White) },
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
                                    bitmap = app.icon.toBitmap(72, 72).asImageBitmap(),
                                    contentDescription = app.label,
                                    modifier = Modifier.size(36.dp),
                                    tint = Color.Unspecified
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(app.label, color = Color.White, fontSize = 15.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { addingToFolder = null }) {
                    Text("Скасувати", color = Color.White)
                }
            },
            containerColor = Color(0xFF1A1A1A)
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
                        Color(0xF02A2A2A),
                        Color(0xE01A1A1A)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.08f)
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
                tint = Color(0xFF64B5F6),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(folder.name, color = Color.White, fontSize = 16.sp)
                Text(
                    "${folder.packageNames.size} застосунків",
                    color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Видалити", tint = Color(0xFFFF6B6B), modifier = Modifier.size(20.dp))
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
                        color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp,
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
                                bitmap = app.icon.toBitmap(72, 72).asImageBitmap(),
                                contentDescription = app.label,
                                modifier = Modifier.size(32.dp),
                                tint = Color.Unspecified
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onRemoveApp(app.packageName) }) {
                                Icon(Icons.Default.Remove, "Прибрати", tint = Color(0xFFFF6B6B), modifier = Modifier.size(18.dp))
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
                        .background(Color.White.copy(alpha = 0.06f))
                        .pointerInput(Unit) {
                            detectTapGestures { onAddApp() }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, "Додати", tint = Color(0xFF64B5F6), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Додати застосунок", color = Color(0xFF64B5F6), fontSize = 13.sp)
                }
            }
        }
    }
}
