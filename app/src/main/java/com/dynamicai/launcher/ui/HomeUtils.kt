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
import android.os.PowerManager
import android.util.Log
import com.dynamicai.launcher.data.SettingsStorage
import com.dynamicai.launcher.suggestion.BrowserBridge

internal fun openGoogleSearch(ctx: Context, query: String) {
    val url = "https://www.google.com/search?q=" + Uri.encode(query)
    if (BrowserBridge.open(ctx, url)) return
    val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    
    try {
        val st = SettingsStorage(ctx).load()
        if (st.defaultBrowser.isNotBlank()) {
            val i = Intent(viewIntent).setPackage(st.defaultBrowser)
            ctx.startActivity(i)
            return
        }
    } catch (_: Throwable) {}
    
    try {
        val i = Intent(viewIntent).setPackage("com.google.android.googlequicksearchbox")
        ctx.startActivity(i)
        return
    } catch (_: Throwable) {}
    
    try {
        val i = Intent(viewIntent).setPackage("com.android.chrome")
        ctx.startActivity(i)
        return
    } catch (_: Throwable) {}
    
    try {
        ctx.startActivity(viewIntent)
    } catch (_: Throwable) {}
}

internal fun isScreenOn(ctx: Context): Boolean {
    return try {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isInteractive
    } catch (_: Throwable) { true }



}

/** Системна сторінка «Про застосунок». */
internal fun openAppInfo(ctx: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try { ctx.startActivity(intent) } catch (_: Exception) {}
}

/** Видалення застосунку: системний діалог, запасний ACTION_DELETE, наостанок — сторінка застосунку. */
internal fun uninstallApp(ctx: Context, packageName: String) {
    try {
        val uninstallIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = Uri.fromParts("package", packageName, null)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(uninstallIntent)
        } catch (e1: Exception) {
            Log.e("Uninstall", "UNINSTALL failed: ${e1.message}", e1)
            val deleteIntent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(deleteIntent)
        }
    } catch (t: Throwable) {
        openAppInfo(ctx, packageName)
    }
}
