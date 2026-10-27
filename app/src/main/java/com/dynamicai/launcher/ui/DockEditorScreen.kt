package com.dynamicai.launcher.ui

import com.dynamicai.launcher.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.dynamicai.launcher.model.AppInfo

@Composable
fun DockEditorScreen(
    apps: List<AppInfo>,
    dockPkgs: List<String>,
    hidden: Set<String>,
    onToggle: (String) -> Unit,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    val filtered = remember(apps, query, hidden) {
        val visible = apps.filter { it.packageName !in hidden }
        if (query.isBlank()) visible
        else visible.filter { it.label.contains(query, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background.copy(alpha = 0.95f))
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
            Text("Док (${dockPkgs.size})", color = AppTheme.colors.textPrimary, fontSize = 22.sp)
        }

        // Поле пошуку
        Text(
            "Обери застосунки для дока — вони з'являться і в лаунчері, і в плаваючому доку",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )

        BasicTextField(
            value = query,
            onValueChange = { query = it },
            textStyle = androidx.compose.ui.text.TextStyle(color = AppTheme.colors.textPrimary, fontSize = 18.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.textPrimary),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("Пошук застосунків…", color = Color.Gray, fontSize = 18.sp)
                }
                inner()
            }
        )

        HorizontalDivider(color = Color.DarkGray)

        // Список
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(filtered, key = { it.packageName }) { app ->
                val inDock = app.packageName in dockPkgs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(app.packageName) {
                            detectTapGestures { onToggle(app.packageName) }
                        }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        bitmap = app.bitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        app.label,
                        color = if (inDock) AppTheme.colors.accent else AppTheme.colors.textPrimary,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (inDock) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "У доку",
                            tint = AppTheme.colors.accent,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
