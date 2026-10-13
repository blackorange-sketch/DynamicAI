package com.dynamicai.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.dynamicai.launcher.command.FuzzyMatcher
import com.dynamicai.launcher.model.AppInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
    visible: Boolean
) {
    var query by remember { mutableStateOf("") }
    var filtered by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var showOverlay by remember { mutableStateOf(false) }
    var releaseJob by remember { mutableStateOf<Job?>(null) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(apps, query) {
        filtered = if (query.isBlank()) apps
        else apps
            .map { it to FuzzyMatcher.score(it.label, query) }
            .filter { it.second >= 35 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    val letters = remember(filtered, query) {
        if (query.isNotBlank()) emptyList()
        else filtered.map { it.label.firstOrNull()?.uppercaseChar() ?: '#' }
            .distinct()
            .sorted()
    }

    LaunchedEffect(activeLetter) {
        if (activeLetter != null) showOverlay = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0D0D))
                .pointerInput(Unit) {
                    // Свайп вниз закриває drawer
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount > 40) onDismiss()
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 64.dp)
            ) {
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 20.sp),
                            cursorBrush = SolidColor(Color.White),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                if (filtered.isNotEmpty()) {
                                    onLaunch(filtered.first())
                                    onDismiss()
                                }
                            }),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            decorationBox = { inner ->
                                if (query.isEmpty()) {
                                    Text("Усі застосунки…", color = Color.Gray, fontSize = 20.sp)
                                }
                                inner()
                            }
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Закрити", tint = Color.White)
                    }
                }

                HorizontalDivider(color = Color.DarkGray)

                // Ручка для свайпу вниз
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.Gray.copy(alpha = 0.5f))
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppDrawerRow(app) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLaunch(app)
                            onDismiss()
                        }
                    }
                }
            }

            if (letters.isNotEmpty() && query.isBlank()) {
                AlphabetSidebar(
                    letters = letters,
                    activeLetter = activeLetter,
                    onLetterSelected = { letter ->
                        releaseJob?.cancel()
                        activeLetter = letter
                        showOverlay = true
                        scrollJob?.cancel()
                        scrollJob = scope.launch {
                            val idx = filtered.indexOfFirst {
                                (it.label.firstOrNull()?.uppercaseChar() ?: '#') == letter
                            }
                            if (idx >= 0) {
                                val current = listState.firstVisibleItemIndex
                                val d = kotlin.math.abs(idx - current)
                                when {
                                    d > 60 -> { listState.scrollToItem(idx - 5); listState.animateScrollToItem(idx) }
                                    d > 20 -> { listState.scrollToItem(idx - 3); listState.animateScrollToItem(idx) }
                                    else -> listState.animateScrollToItem(idx)
                                }
                            }
                        }
                    },
                    onLetterReleased = { isTap ->
                        releaseJob?.cancel()
                        releaseJob = scope.launch {
                            delay(if (isTap) 350 else 100)
                            showOverlay = false
                            delay(180)
                            activeLetter = null
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                )
            }

            if (showOverlay && activeLetter != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(120.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Text(activeLetter?.toString() ?: "", color = Color.White, fontSize = 64.sp)
                }
            }
        }
    }
}

@Composable
private fun AppDrawerRow(app: AppInfo, onTap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(app.packageName) {
                androidx.compose.foundation.gestures.detectTapGestures { onTap() }
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(
            bitmap = androidx.core.graphics.drawable.toBitmap(app.icon, 96, 96).asImageBitmap(),
            contentDescription = app.label,
            modifier = Modifier.size(44.dp),
            tint = Color.Unspecified
        )
        Spacer(Modifier.width(16.dp))
        Text(app.label, color = Color.White, fontSize = 18.sp)
    }
}
