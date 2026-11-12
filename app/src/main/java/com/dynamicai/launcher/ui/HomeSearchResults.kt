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
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dynamicai.launcher.folder.Folder
import com.dynamicai.launcher.macro.FocusModes
import com.dynamicai.launcher.macro.MacroLauncher
import com.dynamicai.launcher.macro.MacroScreen
import com.dynamicai.launcher.macro.MacroStorage
import com.dynamicai.launcher.macro.NotificationDigest
import com.dynamicai.launcher.overlay.FloatingDockService
import com.dynamicai.launcher.suggestion.AppUsage
import com.dynamicai.launcher.suggestion.SearchHistory
import com.dynamicai.launcher.system.StatusBarController
import com.dynamicai.launcher.widget.AddWidgetDialog
import com.dynamicai.launcher.widget.PlacedWidget
import com.dynamicai.launcher.widget.WidgetsStorage
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Порожній результат пошуку з підказкою. */
@Composable
private fun NothingFound(query: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Нічого не знайдено за \"$query\"", color = AppTheme.colors.textMuted, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Спробуй: ліхтарик · wifi · Аня",
            color = AppTheme.colors.textMuted.copy(alpha = 0.7f), fontSize = 13.sp
        )
    }
}

/** Рядок результату, що не є застосунком: дія, налаштування, додаткова підказка або контакт. */
@Composable
private fun SuggestionRow(s: Suggestion, onClick: () -> Unit) {
    when (s) {
        is Suggestion.Action -> SuggestionActionRow(s, onClick)
        is Suggestion.Setting -> SuggestionSettingRow(s, onClick)
        is Suggestion.Extra -> SuggestionExtraRow(s, onClick)
        is Suggestion.Contact -> SuggestionContactRow(s, onClick)
        is Suggestion.App -> Unit
    }
}

/**
 * Результати пошуку й список застосунків головного екрана.
 * Набраний запит — сітка (застосунки плитками, решта рядками); порожній запит — алфавітний список.
 */
@Composable
fun HomeSearchResults(
    visible: Boolean,
    query: String,
    suggestions: List<Suggestion>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    dockPkgs: List<String>,
    showLabels: Boolean,
    onExecute: (Suggestion) -> Unit,
    onAppTap: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val fire: (Suggestion) -> Unit = { s ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onExecute(s)
    }
    val bottomPad = if (dockPkgs.isNotEmpty()) 110.dp else 8.dp
    Box(modifier = modifier) {
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(110)),
            exit = fadeOut(tween(90)),
            modifier = Modifier.fillMaxSize()
        ) {
            if (query.isNotBlank()) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = gridCells(),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = bottomPad),
                    horizontalArrangement = gridGapH(),
                    verticalArrangement = gridGapV()
                ) {
                    if (suggestions.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { NothingFound(query) }
                    }
                    // Застосунки — плитками зверху, решта результатів — рядками знизу
                    val ordered = suggestions.filterIsInstance<Suggestion.App>() +
                        suggestions.filter { it !is Suggestion.App }
                    items(
                        ordered,
                        key = { it.stableKey() },
                        span = { s -> GridItemSpan(if (s is Suggestion.App) 1 else maxLineSpan) }
                    ) { s ->
                        if (s is Suggestion.App) {
                            AppGridItem(
                                app = s.app,
                                onLongClick = { onAppLongClick(s.app) },
                                onTap = { onAppTap(s.app) }
                            )
                        } else {
                            SuggestionRow(s) { fire(s) }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = bottomPad)
                ) {
                    // Без animateItem: при швидкому наборі рядки мають змінюватись різко, а не «пливти»
                    items(suggestions, key = { it.stableKey() }) { s ->
                        Box {
                            if (s is Suggestion.App) {
                                AppRow(
                                    app = s.app,
                                    inDock = s.app.packageName in dockPkgs,
                                    showLabel = showLabels,
                                    onClick = { onAppTap(s.app) },
                                    onLongClick = { onAppLongClick(s.app) },
                                    highlight = query
                                )
                            } else {
                                SuggestionRow(s) { fire(s) }
                            }
                        }
                    }
                }
            }
        }
    }
}
