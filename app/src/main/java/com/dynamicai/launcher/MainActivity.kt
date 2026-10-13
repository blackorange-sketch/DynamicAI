package com.dynamicai.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dynamicai.launcher.model.AppInfo

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                HomeScreen()
            }
        }
    }
}

@Composable
fun HomeScreen(vm: AppListViewModel = viewModel()) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var filtered by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    LaunchedEffect(apps, query) {
        filtered = if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 16.dp)
            .statusBarsPadding()
    ) {
        Spacer(Modifier.height(24.dp))

        BasicTextField(
            value = query,
            onValueChange = { query = it },
            textStyle = TextStyle(color = Color.White, fontSize = 20.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("Пошук застосунків…", color = Color.Gray, fontSize = 20.sp)
                }
                inner()
            }
        )

        HorizontalDivider(color = Color.DarkGray)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(filtered, key = { it.packageName }) { app ->
                AppRow(app) { vm.launch(app.packageName) }
            }
        }
    }
}

@Composable
fun AppRow(app: AppInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
            contentDescription = app.label,
            modifier = Modifier.size(44.dp),
            tint = Color.Unspecified
        )
        Spacer(Modifier.width(16.dp))
        Text(app.label, color = Color.White, fontSize = 18.sp)
    }
}
