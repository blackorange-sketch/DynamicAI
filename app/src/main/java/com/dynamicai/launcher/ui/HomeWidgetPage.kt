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

/** Одна сторінка головного екрана: віджети (зі стеками) і їхні налаштування. */
@Composable
fun HomeWidgetPage(
    page: Int,
    pageCount: Int,
    placedWidgets: List<com.dynamicai.launcher.widget.PlacedWidget>,
    storage: com.dynamicai.launcher.widget.WidgetsStorage,
    weather: com.dynamicai.launcher.data.WeatherState,
    showWeather: Boolean,
    clockOnlyMode: Boolean,
    scrollable: Boolean,
    notesScreenOpen: Boolean,
    onWidgetsChanged: () -> Unit,
    onWeatherTap: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenMacros: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    var widgetSettingsFor by remember { mutableStateOf<com.dynamicai.launcher.widget.PlacedWidget?>(null) }
    val notesStorage = remember { com.dynamicai.launcher.note.NotesStorage(ctx) }
    var notesList by remember { mutableStateOf<List<com.dynamicai.launcher.note.Note>>(emptyList()) }
    LaunchedEffect(notesScreenOpen, page) {
        if (!notesScreenOpen) notesList = notesStorage.get()
    }

    val pageScrollable = scrollable && placedWidgets.none { it.page == page && it.size == "full" }
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(if (pageScrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        verticalArrangement = Arrangement.Top
    ) {
        val pageWidgets = placedWidgets.filter { it.page == page }
        val hasFull = pageWidgets.any { it.size == "full" }
        val baseWidgets = if (hasFull) pageWidgets.filter { it.size == "full" } else pageWidgets
        val clockOnly = if (clockOnlyMode) baseWidgets.filter { it.type == "weather_clock" } else emptyList()
        val visibleWidgets = if (clockOnly.isNotEmpty()) clockOnly else baseWidgets

        val renderOne: @Composable (com.dynamicai.launcher.widget.PlacedWidget) -> Unit = { widget ->
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.pointerInput(widget.id) {
                        detectTapGestures(
                            onLongPress = { widgetSettingsFor = widget }
                        )
                    }
                ) {
                    com.dynamicai.launcher.widget.RenderPlacedWidget(
                        widget = widget,
                        weather = weather,
                        showWeather = showWeather,
                        notes = notesList,
                        notesStorage = notesStorage,
                        onWeatherTap = onWeatherTap,
                        onSettingsLongPress = onOpenSettings,
                        onNotesToggle = { id -> notesStorage.toggle(id); notesList = notesStorage.get() },
                        onNotesDelete = { id -> notesStorage.remove(id); notesList = notesStorage.get() },
                        onNotesAdd = { text -> notesStorage.add(text); notesList = notesStorage.get() },
                        onOpenAllNotes = onOpenNotes,
                        onWidgetLongPress = { widgetSettingsFor = widget },
                        onOpenMacros = onOpenMacros,
                        onOpenVoice = {
                            ctx.startActivity(android.content.Intent(ctx, com.dynamicai.launcher.VoiceChatActivity::class.java))
                        }
                    )
                }
                if (widgetSettingsFor?.id == widget.id) {
                    com.dynamicai.launcher.widget.WidgetSettingsDialog(
                        widget = widget,
                        pageCount = pageCount,
                        onDismiss = { widgetSettingsFor = null },
                        onDelete = {
                            storage.remove(widget.id)
                            onWidgetsChanged()
                            widgetSettingsFor = null
                        },
                        onSizeChange = { newSize ->
                            storage.updateSize(widget.id, newSize)
                            onWidgetsChanged()
                        },
                        onMove = { newPage ->
                            storage.moveToPage(widget.id, newPage)
                            onWidgetsChanged()
                        },
                        onStackWithPrevious = {
                            storage.stackWithPrevious(widget.id)
                            onWidgetsChanged()
                            widgetSettingsFor = null
                        },
                        onUnstack = {
                            storage.unstack(widget.id)
                            onWidgetsChanged()
                            widgetSettingsFor = null
                        }
                    )
                }
        }
        // Віджети з однаковим group малюємо як стек на місці першого
        val slots = mutableListOf<MutableList<com.dynamicai.launcher.widget.PlacedWidget>>()
        for (w in visibleWidgets) {
            val s = if (w.group.isNotBlank() && w.size != "full") slots.firstOrNull { it.first().group == w.group } else null
            if (s != null) s.add(w) else slots.add(mutableListOf(w))
        }
        slots.forEachIndexed { i, s ->
            androidx.compose.foundation.layout.Box(Modifier.appearStagger(i)) {
                if (s.size == 1) renderOne(s[0])
                else com.dynamicai.launcher.widget.WidgetStack(s) { renderOne(it) }
            }
        }
    }
}
