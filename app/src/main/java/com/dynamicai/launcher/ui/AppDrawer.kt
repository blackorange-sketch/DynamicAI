package com.dynamicai.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.dynamicai.launcher.command.FuzzyMatcher
import com.dynamicai.launcher.model.AppInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    backgroundAlphaPercent: Int,
    iconSizeDp: Int,
    fontSizeSp: Int,
    onLaunch: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
    visible: Boolean
) {
    var query by remember { mutableStateOf("") }
    var filtered by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    val haptic = LocalHapticFeedback.current
    val gridState = rememberLazyGridState()
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
        else filtered
            .mapNotNull { it.label.firstOrNull()?.uppercaseChar() }
            .filter { it.isLetter() }
            .distinct()
            .sorted()
    }

    LaunchedEffect(activeLetter) {
        if (activeLetter != null) showOverlay = true
    }

    // Кількість колонок — залежить від розміру іконки
    val columns = remember(iconSizeDp) {
        GridCells.Adaptive(minSize = (iconSizeDp + 24).dp)
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = kotlin.math.max(0.9f, backgroundAlphaPercent / 100f)))
                .pointerInput(Unit) {
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
                    .padding(start = 12.dp, end = 48.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // Пошук — LiquidSearchBar
                LiquidSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = {
                        if (filtered.isNotEmpty()) {
                            onLaunch(filtered.first())
                            onDismiss()
                        }
                    },
                    onVoice = {
                        // Голос у меню — не реалізовано
                    },
                    onClose = onDismiss,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                HorizontalDivider(color = Color.DarkGray)

                // Ручка для свайпу
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

                // Сітка застосунків
                LazyVerticalGrid(
                    state = gridState,
                    columns = columns,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppGridItem(app, iconSizeDp, fontSizeSp) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLaunch(app)
                            onDismiss()
                        }
                    }
                }
            }

            // Стрічка букв
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
                                val current = gridState.firstVisibleItemIndex
                                val d = kotlin.math.abs(idx - current)
                                when {
                                    d > 60 -> { gridState.scrollToItem((idx - 5).coerceAtLeast(0)); gridState.animateScrollToItem(idx) }
                                    d > 20 -> { gridState.scrollToItem((idx - 3).coerceAtLeast(0)); gridState.animateScrollToItem(idx) }
                                    else -> gridState.animateScrollToItem(idx)
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
private fun AppGridItem(
    app: AppInfo,
    iconSizeDp: Int,
    fontSizeSp: Int,
    onTap: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(app.packageName) {
                detectTapGestures { onTap() }
            }
            .padding(vertical = 6.dp)
    ) {
        // Glass-обгортка навколо іконки
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size((iconSizeDp + 16).dp)
                .clip(RoundedCornerShape((iconSizeDp / 2 + 8).dp))
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.02f)
                        )
                    )
                )
                .border(
                    width = 0.5.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.2f),
                            Color.White.copy(alpha = 0.04f)
                        )
                    ),
                    shape = RoundedCornerShape((iconSizeDp / 2 + 8).dp)
                )
        ) {
            Icon(
                bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier.size(iconSizeDp.dp),
                tint = Color.Unspecified
            )
        }
        if (fontSizeSp > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = app.label,
                color = Color.White,
                fontSize = fontSizeSp.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
