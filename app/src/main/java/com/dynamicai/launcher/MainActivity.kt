package com.dynamicai.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.dynamicai.launcher.ui.AlphabetSidebar
import com.dynamicai.launcher.ui.Dock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val dockPkgs by vm.dockPkgs.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var filtered by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var showOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(apps, query) {
        filtered = if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    val letters = remember(filtered) {
        filtered.map { it.label.firstOrNull()?.uppercaseChar() ?: '#' }
            .distinct()
            .sorted()
    }

    val dockApps = remember(dockPkgs, apps) {
        apps.filter { it.packageName in dockPkgs }
    }

    // Ховаємо оверлей через 700 мс після останньої взаємодії
    LaunchedEffect(activeLetter) {
        if (activeLetter != null) {
            showOverlay = true
            delay(700)
            showOverlay = false
            delay(300)
            activeLetter = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 64.dp) // правий відступ під сайдбар
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
                        Text("Пошук або команда…", color = Color.Gray, fontSize = 20.sp)
                    }
                    inner()
                }
            )

            HorizontalDivider(color = Color.DarkGray)

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = if (dockApps.isNotEmpty() && query.isBlank()) 96.dp else 8.dp
                )
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        inDock = app.packageName in dockPkgs,
                        onClick = { vm.launch(app.packageName) },
                        onLongClick = { vm.toggleDock(app.packageName) }
                    )
                }
            }
        }

        if (letters.isNotEmpty() && query.isBlank()) {
            AlphabetSidebar(
                letters = letters,
                activeLetter = activeLetter,
                onLetterSelected = { letter ->
                    activeLetter = letter
                    scrollJob?.cancel()
                    scrollJob = scope.launch {
                        val idx = filtered.indexOfFirst {
                            (it.label.firstOrNull()?.uppercaseChar() ?: '#') == letter
                        }
                        if (idx >= 0) {
                            val current = listState.firstVisibleItemIndex
                            val distance = kotlin.math.abs(idx - current)
                            when {
                                // Дуже далеко — миттєвий стрибок майже до цілі
                                distance > 60 -> {
                                    listState.scrollToItem(idx - 5)
                                    listState.animateScrollToItem(idx)
                                }
                                // Середньо — стрибок ближче, плавна зупинка
                                distance > 20 -> {
                                    listState.scrollToItem(idx - 3)
                                    listState.animateScrollToItem(idx)
                                }
                                // Близько — повністю плавно
                                else -> listState.animateScrollToItem(idx)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )
        }

        // Оверлей з великою літерою
        AnimatedVisibility(
            visible = showOverlay && activeLetter != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Text(
                    text = activeLetter?.toString() ?: "",
                    color = Color.White,
                    fontSize = 64.sp
                )
            }
        }

        if (dockApps.isNotEmpty() && query.isBlank()) {
            Dock(
                apps = dockApps,
                onLaunch = { vm.launch(it) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    app: AppInfo,
    inDock: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
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
        Text(
            text = app.label,
            color = if (inDock) Color(0xFF64B5F6) else Color.White,
            fontSize = 18.sp
        )
    }
}
