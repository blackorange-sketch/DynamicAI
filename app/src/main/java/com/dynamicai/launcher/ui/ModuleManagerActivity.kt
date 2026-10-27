package com.dynamicai.launcher.ui

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
            .background(Brush.verticalGradient(listOf(Color(0xFF14141E), Color(0xFF050505))))
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🧩 Модулі", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("✕", color = Color.White.copy(alpha = 0.6f), fontSize = 24.sp, modifier = Modifier.padding(8.dp).clickable(onClick = onClose))
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
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(0.5.dp, if (isInstalled) Color(0xFF4CAF50).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(module.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(module.description, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))

                            if (isDownloading) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF64B5F6), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Завантаження...", color = Color(0xFF64B5F6), fontSize = 12.sp)
                                }
                            } else if (isInstalled) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Встановлено (${sizeMb} МБ)", color = Color(0xFF4CAF50), fontSize = 12.sp)
                                    Row {
                                        TextButton(onClick = { 
                                            scope.launch {
                                                downloadingModule = module
                                                ModuleManager.downloadAndInstall(ctx, module)
                                                downloadingModule = null
                                                refresh++
                                            }
                                        }) { Text("Оновити", color = Color(0xFF64B5F6), fontSize = 12.sp) }
                                        TextButton(onClick = { showDeleteDialog = module }) { Text("Видалити", color = Color(0xFFFF5252), fontSize = 12.sp) }
                                    }
                                }
                            } else {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Не встановлено", color = Color(0xFFFF9800), fontSize = 12.sp)
                                    TextButton(onClick = { 
                                        scope.launch {
                                            downloadingModule = module
                                            ModuleManager.downloadAndInstall(ctx, module)
                                            downloadingModule = null
                                            refresh++
                                        }
                                    }) { Text("Завантажити", color = Color(0xFF64B5F6), fontSize = 12.sp) }
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
                containerColor = Color(0xFF1A1A1A),
                title = { Text("Видалити модуль?", color = Color.White) },
                text = { Text("Ви впевнені? Це звільнить місце, але функція перестане працювати.", color = Color.White.copy(alpha = 0.7f)) },
                confirmButton = {
                    TextButton(onClick = {
                        ModuleManager.deleteModule(ctx, showDeleteDialog!!)
                        showDeleteDialog = null
                        refresh++
                    }) { Text("Видалити", color = Color(0xFFFF5252)) }
                },
                dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("Скасувати", color = Color.White.copy(alpha = 0.6f)) } }
            )
        }
    }
}
