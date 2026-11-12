package com.dynamicai.launcher.ui

import com.dynamicai.launcher.ui.theme.AppTheme
import com.dynamicai.launcher.ui.theme.DynamicAITheme
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dynamicai.launcher.AppListViewModel
import com.dynamicai.launcher.BuildConfig
import com.dynamicai.launcher.model.AppInfo
import com.dynamicai.launcher.suggestion.Suggestion
import com.dynamicai.launcher.suggestion.stableKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.material3.Checkbox
import com.dynamicai.launcher.folder.Folder

/** Поле назви папки з підказкою. */
@Composable
private fun FolderNameField(value: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = TextStyle(color = AppTheme.colors.textPrimary, fontSize = 16.sp),
        cursorBrush = SolidColor(AppTheme.colors.textPrimary),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text("Назва папки", color = AppTheme.colors.textMuted, fontSize = 16.sp)
            }
            inner()
        }
    )
}

/** Рядок дії в діалозі. */
@Composable
private fun DialogAction(label: String, color: Color = AppTheme.colors.textPrimary, onClick: () -> Unit) {
    Text(
        label,
        color = color,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    )
}

@Composable
private fun CancelButton(label: String = "Скасувати", onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(label, color = AppTheme.colors.textPrimary) }
}

/** Нова папка з одним застосунком (з меню застосунку). */
@Composable
fun CreateFolderDialog(app: AppInfo, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        modifier = Modifier.popIn(),
        onDismissRequest = onDismiss,
        title = { Text("Нова папка", color = AppTheme.colors.textPrimary) },
        text = {
            Column {
                Text("Застосунок: ${app.label}", color = AppTheme.colors.textMuted, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                FolderNameField(name) { name = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onCreate(name)
                onDismiss()
            }) { Text("Створити", color = AppTheme.colors.accent) }
        },
        dismissButton = { CancelButton(onClick = onDismiss) },
        containerColor = AppTheme.colors.surface
    )
}

/** Вибір папки, куди додати застосунок. */
@Composable
fun AddToFolderDialog(app: AppInfo, folders: List<Folder>, onPick: (Folder) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.popIn(),
        onDismissRequest = onDismiss,
        title = { Text("Додати ${app.label} до…", color = AppTheme.colors.textPrimary) },
        text = {
            Column {
                if (folders.isEmpty()) {
                    Text("Немає папок", color = AppTheme.colors.textMuted, fontSize = 14.sp)
                } else {
                    folders.forEach { f -> DialogAction(f.name) { onPick(f) } }
                }
            }
        },
        confirmButton = {},
        dismissButton = { CancelButton(onClick = onDismiss) },
        containerColor = AppTheme.colors.surface
    )
}

/** Довге натискання на застосунок у папці. */
@Composable
fun FolderAppMenuDialog(app: AppInfo, onRemove: () -> Unit, onLaunch: () -> Unit, onMore: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.popIn(),
        onDismissRequest = onDismiss,
        title = { Text(app.label, color = AppTheme.colors.textPrimary, fontSize = 18.sp) },
        text = {
            Column {
                DialogAction("Прибрати з папки", onClick = onRemove)
                DialogAction("Запустити", onClick = onLaunch)
                DialogAction("Більше дій…", AppTheme.colors.textSecondary, onMore)
            }
        },
        confirmButton = {},
        dismissButton = { CancelButton(onClick = onDismiss) },
        containerColor = AppTheme.colors.surface
    )
}

/** Довге натискання на заголовок папки. */
@Composable
fun FolderHeaderDialog(folder: Folder, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.popIn(),
        onDismissRequest = onDismiss,
        title = { Text(folder.name, color = AppTheme.colors.textPrimary, fontSize = 18.sp) },
        text = {
            Column {
                DialogAction("Видалити папку (застосунки залишаться)", AppTheme.colors.danger, onDelete)
            }
        },
        confirmButton = {},
        dismissButton = { CancelButton(onClick = onDismiss) },
        containerColor = AppTheme.colors.surface
    )
}

/** Нова папка з вибором застосунків (з головного меню). */
@Composable
fun NewFolderDialog(apps: List<AppInfo>, onCreate: (String, List<String>) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    AlertDialog(
        modifier = Modifier.popIn(),
        onDismissRequest = onDismiss,
        title = { Text("Нова папка", color = AppTheme.colors.textPrimary) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                FolderNameField(name) { name = it }
                Spacer(Modifier.height(12.dp))
                Text("Обери застосунки:", color = AppTheme.colors.textMuted, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                ) {
                    items(apps) { app ->
                        val toggle = {
                            selection = if (app.packageName in selection) selection - app.packageName
                            else selection + app.packageName
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = toggle)
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = app.packageName in selection,
                                onCheckedChange = { toggle() }
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                bitmap = (app.bitmap ?: app.icon.toBitmap(72, 72)).asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = Color.Unspecified
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, color = AppTheme.colors.textPrimary, fontSize = 15.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && selection.isNotEmpty()) onCreate(name, selection.toList())
                    onDismiss()
                },
                enabled = name.isNotBlank() && selection.isNotEmpty()
            ) { Text("Створити", color = AppTheme.colors.accent) }
        },
        dismissButton = { CancelButton(onClick = onDismiss) },
        containerColor = AppTheme.colors.surface
    )
}

/** Рядок застосунку з іконкою в діалозі. */
@Composable
private fun DialogAppRow(app: AppInfo, iconDp: Int, vPad: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = vPad.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            bitmap = (app.bitmap ?: app.icon.toBitmap(96, 96)).asImageBitmap(),
            contentDescription = app.label,
            modifier = Modifier.size(iconDp.dp),
            tint = Color.Unspecified
        )
        Spacer(Modifier.width((iconDp * 0.4f).dp))
        Text(app.label, color = AppTheme.colors.textPrimary, fontSize = 16.sp)
    }
}

/** Вміст відкритої папки. */
@Composable
fun OpenFolderDialog(
    folder: Folder,
    apps: List<AppInfo>,
    inDock: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onToggleDock: () -> Unit,
    onDismiss: () -> Unit
) {
    val folderApps = remember(apps, folder) { apps.filter { it.packageName in folder.packageNames } }
    AlertDialog(
        modifier = Modifier.popIn(),
        onDismissRequest = onDismiss,
        title = { Text(folder.name, color = AppTheme.colors.textPrimary, fontSize = 18.sp) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
            ) {
                items(folderApps, key = { it.packageName }) { app ->
                    DialogAppRow(app, iconDp = 40, vPad = 10) { onLaunch(app) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onToggleDock) {
                Text(if (inDock) "Прибрати з дока" else "Додати в док", color = AppTheme.colors.accent)
            }
        },
        dismissButton = { CancelButton("Закрити", onDismiss) },
        containerColor = AppTheme.colors.surface
    )
}

/** Кілька застосунків підійшли під команду — користувач обирає потрібний. */
@Composable
fun AppChoiceDialog(candidates: List<AppInfo>, onChosen: (AppInfo) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.popIn(),
        onDismissRequest = onDismiss,
        title = { Text("Обери застосунок", color = AppTheme.colors.textPrimary) },
        text = {
            Column {
                candidates.forEach { app -> DialogAppRow(app, iconDp = 36, vPad = 8) { onChosen(app) } }
            }
        },
        confirmButton = {},
        dismissButton = { CancelButton(onClick = onDismiss) },
        containerColor = AppTheme.colors.surface
    )
}
