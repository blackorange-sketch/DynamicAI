package com.dynamicai.launcher.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.graphics.drawable.toBitmap
import com.dynamicai.launcher.model.AppInfo
import com.dynamicai.launcher.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ярлик додатка (як «Нова вкладка» в Chrome): статичні/динамічні shortcuts через LauncherApps. */
class AppShortcut(val info: ShortcutInfo, val label: String, val icon: Drawable?)

object AppShortcuts {
    /** Працює лише коли лаунчер — домашній застосунок за замовчуванням; інакше повертає порожній список. */
    fun load(ctx: Context, pkg: String): List<AppShortcut> = try {
        val la = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        if (!la.hasShortcutHostPermission()) emptyList()
        else {
            val q = LauncherApps.ShortcutQuery()
                .setPackage(pkg)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            (la.getShortcuts(q, android.os.Process.myUserHandle()) ?: emptyList())
                .filter { it.isEnabled }
                .sortedBy { it.rank }
                .take(4)
                .map {
                    AppShortcut(
                        it,
                        (it.shortLabel ?: it.longLabel ?: "").toString(),
                        try { la.getShortcutIconDrawable(it, ctx.resources.displayMetrics.densityDpi) } catch (_: Throwable) { null }
                    )
                }
        }
    } catch (_: Throwable) { emptyList() }

    fun start(ctx: Context, s: ShortcutInfo) {
        try {
            (ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps)
                .startShortcut(s, null, null)
        } catch (_: Throwable) {}
    }
}

private class AnchorPositionProvider(private val anchor: Offset, private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize
    ): IntOffset {
        val ax = if (anchor == Offset.Unspecified) windowSize.width / 2 else anchor.x.toInt()
        val ay = if (anchor == Offset.Unspecified) windowSize.height / 2 else anchor.y.toInt()
        val x = (ax - popupContentSize.width / 2).coerceIn(gapPx, (windowSize.width - popupContentSize.width - gapPx).coerceAtLeast(gapPx))
        // Низ екрана → меню над іконкою, верх → під іконкою
        val above = ay > windowSize.height / 2
        val y = if (above) ay - popupContentSize.height - gapPx * 3 else ay + gapPx * 3
        return IntOffset(x, y.coerceIn(gapPx, (windowSize.height - popupContentSize.height - gapPx).coerceAtLeast(gapPx)))
    }
}

/** Контекстне меню іконки: біля пальця, зі списком ярликів додатка зверху й діями знизу (content). */
@Composable
fun AppMenuPopup(
    app: AppInfo,
    anchor: Offset,
    onDismiss: () -> Unit,
    actions: @Composable ColumnScope.() -> Unit
) {
    val ctx = LocalContext.current
    val shortcuts by produceState(initialValue = emptyList<AppShortcut>(), app.packageName) {
        value = withContext(Dispatchers.IO) { AppShortcuts.load(ctx, app.packageName) }
    }
    val gap = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.roundToPx() }
    Popup(
        popupPositionProvider = remember(anchor) { AnchorPositionProvider(anchor, gap) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            Modifier
                .width(264.dp)
                .shadow(16.dp, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .background(AppTheme.colors.surface)
                .border(0.5.dp, AppTheme.colors.glassBorder, RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                app.bitmap?.let {
                    Image(it.asImageBitmap(), null, Modifier.size(28.dp).iconShape())
                    Spacer(Modifier.width(10.dp))
                }
                Text(app.label, color = AppTheme.colors.textPrimary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (shortcuts.isNotEmpty()) {
                shortcuts.forEach { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { AppShortcuts.start(ctx, s.info); onDismiss() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val bmp = remember(s.info.id) { try { s.icon?.toBitmap(64, 64)?.asImageBitmap() } catch (_: Throwable) { null } }
                        if (bmp != null) Image(bmp, null, Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)))
                        else Spacer(Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(s.label, color = AppTheme.colors.textPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(0.5.dp).background(AppTheme.colors.glassBorder))
            }
            actions()
        }
    }
}
