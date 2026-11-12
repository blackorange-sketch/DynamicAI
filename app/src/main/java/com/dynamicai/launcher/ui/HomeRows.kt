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

@Composable
fun AppContextMenu(
    app: AppInfo,
    inDock: Boolean,
    onDismiss: () -> Unit,
    onToggleDock: () -> Unit,
    onHide: () -> Unit,
    onInfo: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.popIn(),
        onDismissRequest = onDismiss,
        title = { Text(app.label, color = AppTheme.colors.textPrimary, fontSize = 18.sp) },
        text = {
            Column {
                MenuItem(if (inDock) "Прибрати з дока" else "Додати в док", onToggleDock)
                MenuItem("Приховати зі списку", onHide)
                MenuItem("Інформація про застосунок", onInfo)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати", color = AppTheme.colors.textPrimary) } },
        containerColor = AppTheme.colors.surface
    )
}

@Composable
internal fun MenuItem(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = AppTheme.colors.textPrimary,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    )
}

@Composable
fun SuggestionActionRow(s: Suggestion.Action, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        AppTheme.colors.textPrimary.copy(alpha = 0.08f),
                        AppTheme.colors.textPrimary.copy(alpha = 0.02f)
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassCircleIcon(Icons.Default.Star, AppTheme.colors.accent)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(s.label, color = AppTheme.colors.textPrimary, fontSize = 16.sp)
            s.subtitle?.let { Text(it, color = com.dynamicai.launcher.ui.theme.AppTheme.colors.textMuted, fontSize = 12.sp) }
        }
    }
}

@Composable
fun SuggestionExtraRow(s: Suggestion.Extra, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        AppTheme.colors.textPrimary.copy(alpha = if (s.kind == "ask") 0.04f else 0.08f),
                        AppTheme.colors.textPrimary.copy(alpha = 0.02f)
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(AppTheme.colors.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) { Text(s.icon, fontSize = 20.sp) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(s.label, color = AppTheme.colors.textPrimary, fontSize = 16.sp, maxLines = 1)
            Text(s.subtitle, color = AppTheme.colors.textMuted, fontSize = 12.sp)
        }
    }
}

@Composable
fun SuggestionSettingRow(s: Suggestion.Setting, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        AppTheme.colors.textPrimary.copy(alpha = 0.08f),
                        AppTheme.colors.textPrimary.copy(alpha = 0.02f)
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassCircleIcon(Icons.Default.Settings, AppTheme.colors.textMuted)
        Spacer(Modifier.width(14.dp))
        Text(s.label, color = AppTheme.colors.textPrimary, fontSize = 16.sp)
    }
}

@Composable
fun SuggestionContactRow(s: Suggestion.Contact, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        AppTheme.colors.textPrimary.copy(alpha = 0.08f),
                        AppTheme.colors.textPrimary.copy(alpha = 0.02f)
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassCircleIcon(Icons.Default.Person, AppTheme.colors.successLight)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(s.name, color = AppTheme.colors.textPrimary, fontSize = 16.sp)
            Text(s.subtitle, color = com.dynamicai.launcher.ui.theme.AppTheme.colors.textMuted, fontSize = 12.sp)
        }
    }
}

@Composable
fun AppRow(
    app: AppInfo,
    inDock: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    highlight: String = ""
) {
    val style = LocalGridStyle.current
    val iconSizeDp = style.listIconDp
    val fontSizeSp = style.fontSp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = (style.listGapDp / 2).dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        AppTheme.colors.textPrimary.copy(alpha = 0.06f),
                        AppTheme.colors.textPrimary.copy(alpha = 0.015f)
                    )
                )
            )
            .pressable(scaleDown = 0.97f, haptic = false, onLongPress = onLongClick) { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Єдина підкладка під іконку
        IconPlate(iconSizeDp) {
            Icon(
                bitmap = (app.bitmap ?: app.icon.toBitmap(96, 96)).asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier.size(iconSizeDp.dp).iconShape(),
                tint = Color.Unspecified
            )
        }
        if (showLabel) {
            Spacer(Modifier.width(14.dp))
            val baseColor = if (inDock) AppTheme.colors.accent else AppTheme.colors.textPrimary
            val hl = AppTheme.colors.accent
            val labelText = remember(app.label, highlight, baseColor, hl) {
                val i = if (highlight.isBlank()) -1 else app.label.indexOf(highlight.trim(), ignoreCase = true)
                androidx.compose.ui.text.buildAnnotatedString {
                    if (i < 0) append(app.label)
                    else {
                        append(app.label.substring(0, i))
                        withStyle(androidx.compose.ui.text.SpanStyle(color = hl, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
                            append(app.label.substring(i, i + highlight.trim().length))
                        }
                        append(app.label.substring(i + highlight.trim().length))
                    }
                }
            }
            Text(labelText, color = baseColor, fontSize = fontSizeSp.sp)
        }
    }
}
