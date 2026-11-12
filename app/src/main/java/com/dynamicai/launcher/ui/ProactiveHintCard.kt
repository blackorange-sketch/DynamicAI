package com.dynamicai.launcher.ui

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.data.WeatherState
import com.dynamicai.launcher.macro.MacroLauncher
import com.dynamicai.launcher.suggestion.Hint
import com.dynamicai.launcher.suggestion.ProactiveHints
import com.dynamicai.launcher.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Картка проактивної підказки на головному екрані: текст, одна дія і закриття. Перевіряється раз на хвилину. */
@Composable
fun ProactiveHintCard(weather: WeatherState, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val weatherNow by rememberUpdatedState(weather)
    var hint by remember { mutableStateOf<Hint?>(null) }
    // Останню підказку тримаємо, щоб картка плавно зникала, а не обривалась
    var shown by remember { mutableStateOf<Hint?>(null) }
    LaunchedWhileStarted {
        while (true) {
            hint = withContext(Dispatchers.Default) { ProactiveHints.current(ctx, weatherNow) }
            hint?.let { shown = it }
            delay(60_000)
        }
    }
    fun close(h: Hint) { ProactiveHints.snooze(ctx, h); hint = null }
    fun act(h: Hint) {
        try {
            when {
                h.special.startsWith("calendar:") -> {
                    val begin = h.special.removePrefix("calendar:").toLongOrNull() ?: System.currentTimeMillis()
                    val uri = ContentUris.withAppendedId(CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build(), begin)
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                h.special == "storage" ->
                    ctx.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                h.tool.isNotBlank() -> MacroLauncher.runTool(ctx, h.tool, "hint:" + h.id.substringBefore(':'))
            }
        } catch (_: Throwable) {}
        close(h)
    }
    AnimatedVisibility(
        visible = hint != null,
        enter = expandVertically(tween(animMs(320), easing = Motion.Decel)) + fadeIn(tween(animMs(240))),
        exit = shrinkVertically(tween(animMs(220), easing = Motion.Accel)) + fadeOut(tween(animMs(160))),
        modifier = modifier
    ) {
        val h = shown ?: return@AnimatedVisibility
        val shape = RoundedCornerShape(20.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(shape)
                .background(AppTheme.colors.glassBackground)
                .border(1.dp, AppTheme.colors.glassBorder, shape)
                .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(h.icon, fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Text(h.text, color = AppTheme.colors.textPrimary, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
            Text(
                h.actionLabel,
                color = AppTheme.colors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { act(h) }.padding(horizontal = 10.dp, vertical = 8.dp)
            )
            Text(
                "✕",
                color = AppTheme.colors.textMuted,
                fontSize = 14.sp,
                modifier = Modifier.clip(CircleShape).clickable { close(h) }.padding(8.dp)
            )
        }
    }
}
