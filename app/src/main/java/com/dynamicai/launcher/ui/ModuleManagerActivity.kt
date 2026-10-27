package com.dynamicai.launcher.ui

import com.dynamicai.launcher.ui.theme.AppTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.data.AppModule
import com.dynamicai.launcher.data.ModuleManager
import kotlinx.coroutines.launch

class ModuleManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ModuleManagerScreen(onClose = { finish() })
        }
    }
}

@Composable
fun ModuleManagerScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    var downloadingModule by remember { mutableStateOf<AppModule?>(null) }
    var showDeleteDialog by remember { mutableStateOf<AppModule?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AppTheme.colors.backdropStart, AppTheme.colors.backdropEnd)))
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🧩 Модулі", color = AppTheme.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("✕", color = AppTheme.colors.textSecondary, fontSize = 24.sp, modifier = Modifier.padding(8.dp).clickable(onClick = onClose))
            }
            Spacer(Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(ModuleManager.modules) { module ->
                    val isInstalled = ModuleManager.isInstalled(ctx, module)
                    val sizeMb = ModuleManager.getModuleSizeMb(ctx, module)
                    val isDownloading = downloadingModule?.id == module.id

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppTheme.colors.textPrimary.copy(alpha = 0.05f))
                            .border(0.5.dp, if (isInstalled) AppTheme.colors.success.copy(alpha = 0.3f) else AppTheme.colors.glassBorder.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(module.name, color = AppTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(module.description, color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))

                            if (isDownloading) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AppTheme.colors.accent, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Завантаження...", color = AppTheme.colors.accent, fontSize = 12.sp)
                                }
                            } else if (isInstalled) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Встановлено (${sizeMb} МБ)", color = AppTheme.colors.success, fontSize = 12.sp)
                                    Row {
                                        TextButton(onClick = { 
                                            scope.launch {
                                                downloadingModule = module
                                                ModuleManager.downloadAndInstall(ctx, module)
                                                downloadingModule = null
                                                refresh++
                                            }
                                        }) { Text("Оновити", color = AppTheme.colors.accent, fontSize = 12.sp) }
                                        TextButton(onClick = { showDeleteDialog = module }) { Text("Видалити", color = AppTheme.colors.danger, fontSize = 12.sp) }
                                    }
                                }
                            } else {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Не встановлено", color = AppTheme.colors.warning, fontSize = 12.sp)
                                    TextButton(onClick = { 
                                        scope.launch {
                                            downloadingModule = module
                                            ModuleManager.downloadAndInstall(ctx, module)
                                            downloadingModule = null
                                            refresh++
                                        }
                                    }) { Text("Завантажити", color = AppTheme.colors.accent, fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showDeleteDialog != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                containerColor = AppTheme.colors.surface,
                title = { Text("Видалити модуль?", color = AppTheme.colors.textPrimary) },
                text = { Text("Ви впевнені? Це звільнить місце, але функція перестане працювати.", color = AppTheme.colors.textPrimary.copy(alpha = 0.7f)) },
                confirmButton = {
                    TextButton(onClick = {
                        ModuleManager.deleteModule(ctx, showDeleteDialog!!)
                        showDeleteDialog = null
                        refresh++
                    }) { Text("Видалити", color = AppTheme.colors.danger) }
                },
                dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("Скасувати", color = AppTheme.colors.textSecondary) } }
            )
        }
    }
}
