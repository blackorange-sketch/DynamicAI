package com.dynamicai.launcher.widget

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dynamicai.launcher.ui.theme.AppTheme

/**
 * Віджети інших програм (календар, музика, банк…). Лаунчер виступає «хостом»: система видає номер віджета,
 * користувач обирає його в системному списку, а ми вбудовуємо готовий вигляд на сторінку.
 * У сховищі такий віджет має тип "appwidget:<номер>".
 */
object AppWidgetHolder {
    private const val HOST_ID = 0x44AA
    private const val PREFIX = "appwidget:"
    @Volatile private var host: AppWidgetHost? = null

    fun host(ctx: Context): AppWidgetHost =
        host ?: synchronized(this) { host ?: AppWidgetHost(ctx.applicationContext, HOST_ID).also { host = it } }

    fun manager(ctx: Context): AppWidgetManager = AppWidgetManager.getInstance(ctx.applicationContext)

    fun typeFor(appWidgetId: Int): String = PREFIX + appWidgetId
    fun idOf(type: String): Int? = if (type.startsWith(PREFIX)) type.removePrefix(PREFIX).toIntOrNull() else null

    fun allocate(ctx: Context): Int = host(ctx).allocateAppWidgetId()

    fun delete(ctx: Context, appWidgetId: Int) {
        try { host(ctx).deleteAppWidgetId(appWidgetId) } catch (_: Throwable) {}
    }

    /** Системний список віджетів; він же питає дозвіл на прив'язку. */
    fun pickIntent(appWidgetId: Int): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            // Порожні списки потрібні деяким прошивкам, інакше вікно вибору падає
            .putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, ArrayList<AppWidgetProviderInfo>())
            .putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, ArrayList<Bundle>())

    /** Деякі віджети мають власний екран налаштування — відкриваємо його після вибору. */
    fun configureIfNeeded(activity: Activity?, appWidgetId: Int) {
        if (activity == null) return
        try {
            val info = manager(activity).getAppWidgetInfo(appWidgetId) ?: return
            if (info.configure != null) host(activity).startAppWidgetConfigureActivityForResult(activity, appWidgetId, 0, 9911, null)
        } catch (_: Throwable) {}
    }

    fun start(ctx: Context) { try { host(ctx).startListening() } catch (_: Throwable) {} }
    fun stop() { try { host?.stopListening() } catch (_: Throwable) {} }

    fun label(ctx: Context, type: String): String? {
        val id = idOf(type) ?: return null
        return try { manager(ctx).getAppWidgetInfo(id)?.loadLabel(ctx.packageManager) } catch (_: Throwable) { null }
    }
}

/** Оновлення віджетів надходять лише поки лаунчер на екрані. Викликається один раз на головному екрані. */
@Composable
fun AppWidgetHostLifecycle() {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> AppWidgetHolder.start(ctx)
                Lifecycle.Event.ON_STOP -> AppWidgetHolder.stop()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) AppWidgetHolder.start(ctx)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            AppWidgetHolder.stop()
        }
    }
}

@Composable
fun SystemAppWidget(appWidgetId: Int, size: String, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val info = remember(appWidgetId) {
        try { AppWidgetHolder.manager(ctx).getAppWidgetInfo(appWidgetId) } catch (_: Throwable) { null }
    }
    BoxWithConstraints(modifier = modifier) {
        val widthDp = maxWidth
        val heightDp = when (size) {
            "small" -> 110.dp
            "large" -> 320.dp
            "full" -> if (constraints.hasBoundedHeight) maxHeight else 480.dp
            else -> (info?.let { with(density) { it.minHeight.toDp() } } ?: 160.dp).coerceIn(110.dp, 260.dp)
        }
        if (info == null) {
            Column(Modifier.fillMaxWidth().widgetCard().padding(16.dp)) {
                Text("Віджет недоступний", color = AppTheme.colors.textPrimary, fontSize = 14.sp)
                Text("Імовірно, програму видалено. Приберіть його через «⋮».", color = AppTheme.colors.textMuted, fontSize = 12.sp)
            }
        } else {
            val w = widthDp.value.toInt()
            val h = heightDp.value.toInt()
            AndroidView(
                factory = { c -> AppWidgetHolder.host(c).createView(c, appWidgetId, info) },
                update = { v ->
                    // Повідомляємо віджету його розмір, щоб він обрав відповідну розкладку
                    try {
                        @Suppress("DEPRECATION")
                        v.updateAppWidgetSize(Bundle(), w, h, w, h)
                    } catch (_: Throwable) {}
                },
                modifier = Modifier.fillMaxWidth().height(heightDp).clip(RoundedCornerShape(22.dp))
            )
        }
        // Чужий віджет сам обробляє дотики, тому налаштування (розмір, сторінка, видалення) — через цю кнопку
        Text(
            "⋮",
            color = AppTheme.colors.textPrimary,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .clip(CircleShape)
                .background(AppTheme.colors.surface.copy(alpha = 0.55f))
                .clickable { onLongPress() }
                .padding(horizontal = 9.dp, vertical = 3.dp)
        )
    }
}
