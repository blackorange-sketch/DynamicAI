package com.dynamicai.launcher.widget

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import com.dynamicai.launcher.ui.popIn
import com.dynamicai.launcher.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Віджет у списку вибору: чий він, як називається і скільки клітинок займає. */
private data class WidgetEntry(val info: AppWidgetProviderInfo, val app: String, val label: String, val cells: String)

private fun loadEntries(ctx: Context): List<WidgetEntry> {
    val pm = ctx.packageManager
    val density = ctx.resources.displayMetrics.density
    fun cells(px: Int): Int = (((px / density) + 30f) / 70f).toInt().coerceAtLeast(1)
    return AppWidgetHolder.manager(ctx).installedProviders.map { info ->
        val app = try {
            pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
        } catch (_: Throwable) { info.provider.packageName }
        val label = try { info.loadLabel(pm) } catch (_: Throwable) { null } ?: app
        WidgetEntry(info, app, label, "${cells(info.minWidth)}×${cells(info.minHeight)}")
    }.sortedWith(compareBy({ it.app.lowercase() }, { it.label.lowercase() }))
}

/** Картинка попереднього перегляду, яку надає сама програма (або її іконка, якщо картинки немає). */
private fun loadPreviewBitmap(ctx: Context, info: AppWidgetProviderInfo): Pair<Bitmap, Boolean>? {
    val dpi = ctx.resources.displayMetrics.densityDpi
    fun shrink(b: Bitmap, maxSide: Int): Bitmap {
        val longSide = maxOf(b.width, b.height)
        if (longSide <= maxSide) return b
        val k = maxSide.toFloat() / longSide
        return Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
    }
    try {
        info.loadPreviewImage(ctx, dpi)?.let { d ->
            val w = d.intrinsicWidth.takeIf { it > 0 } ?: 600
            val h = d.intrinsicHeight.takeIf { it > 0 } ?: 300
            return shrink(d.toBitmap(w, h), 900) to true
        }
    } catch (_: Throwable) {}
    return try {
        info.loadIcon(ctx, dpi)?.let { d -> shrink(d.toBitmap(144, 144), 144) to false }
    } catch (_: Throwable) { null }
}

@Composable
private fun WidgetPreview(info: AppWidgetProviderInfo) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(14.dp)
    val frame = Modifier
        .fillMaxWidth()
        .height(120.dp)
        .clip(shape)
        .background(AppTheme.colors.textPrimary.copy(alpha = 0.05f))
    // Нові програми (Android 12+) дають не картинку, а макет перегляду — показуємо його як є
    val previewLayout = if (Build.VERSION.SDK_INT >= 31) info.previewLayout else 0
    if (info.previewImage == 0 && previewLayout != 0) {
        var failed by remember(info.provider) { mutableStateOf(false) }
        if (!failed) {
            AndroidView(
                factory = { c ->
                    val holder = FrameLayout(c)
                    try {
                        val pkgCtx = c.createPackageContext(info.provider.packageName, Context.CONTEXT_RESTRICTED)
                        val v: View = LayoutInflater.from(pkgCtx).inflate(previewLayout, holder, false)
                        holder.addView(v)
                    } catch (_: Throwable) { failed = true }
                    holder
                },
                modifier = frame
            )
            return
        }
    }
    val preview by produceState<Pair<Bitmap, Boolean>?>(null, info.provider) {
        value = withContext(Dispatchers.IO) { loadPreviewBitmap(ctx, info) }
    }
    Box(frame, contentAlignment = Alignment.Center) {
        preview?.let { (bmp, isPreview) ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = if (isPreview) Modifier.fillMaxSize().padding(6.dp) else Modifier.height(56.dp)
            )
        }
    }
}

/** Власний список віджетів інших програм із попереднім переглядом і пошуком. */
@Composable
fun SystemWidgetPickerDialog(onPick: (AppWidgetProviderInfo) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val c = AppTheme.colors
    val entries by produceState<List<WidgetEntry>?>(null) {
        value = withContext(Dispatchers.Default) { try { loadEntries(ctx) } catch (_: Throwable) { emptyList() } }
    }
    var filter by remember { mutableStateOf("") }
    val shown = remember(entries, filter) {
        val q = filter.trim().lowercase()
        entries?.filter { q.isEmpty() || it.app.lowercase().contains(q) || it.label.lowercase().contains(q) }
    }
    AlertDialog(
        modifier = Modifier.popIn(),
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("Віджет іншої програми", color = c.textPrimary) },
        text = {
            Column {
                BasicTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    singleLine = true,
                    textStyle = TextStyle(color = c.textPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.textPrimary.copy(alpha = 0.06f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        if (filter.isEmpty()) Text("Пошук за назвою програми або віджета", color = c.textMuted, fontSize = 15.sp)
                        inner()
                    }
                )
                Spacer(Modifier.height(10.dp))
                when {
                    shown == null -> Text("Завантажую список…", color = c.textMuted, fontSize = 13.sp)
                    shown.isEmpty() -> Text("Нічого не знайдено", color = c.textMuted, fontSize = 13.sp)
                    else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                        items(shown, key = { it.info.provider.flattenToString() + ":" + it.info.profile.hashCode() }) { e ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, c.glassBorder.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                                    .clickable { onPick(e.info) }
                                    .padding(10.dp)
                            ) {
                                WidgetPreview(e.info)
                                Spacer(Modifier.height(8.dp))
                                Text(e.label, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text("${e.app} · ${e.cells}", color = c.textMuted, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати", color = c.textSecondary) } }
    )
}
