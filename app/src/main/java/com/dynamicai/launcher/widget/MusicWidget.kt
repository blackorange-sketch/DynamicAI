package com.dynamicai.launcher.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.macro.AppUtils
import com.dynamicai.launcher.music.*
import com.dynamicai.launcher.ui.LaunchedWhileStarted
import com.dynamicai.launcher.ui.pressable
import com.dynamicai.launcher.ui.theme.AppTheme
import kotlinx.coroutines.delay

/**
 * Музичний віджет у спільному стилі: картка-скло, заголовок, чіпи.
 * Дані — з активної медіасесії (YT Music, ReVanced, Spotify…): назва, обкладинка, прогрес, черга.
 * Вкладки в великих розмірах: Черга · Історія · Бібліотека (через MediaBrowser плеєра).
 */
@Composable
fun MusicWidget(size: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var access by remember { mutableStateOf(true) }
    LaunchedWhileStarted {
        while (true) {
            access = MediaHub.ensureStarted(ctx)
            delay(if (access) 20_000 else 4_000)
        }
    }
    val st by MediaHub.state.collectAsState()
    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedWhileStarted {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(if (MediaHub.state.value?.playing == true) 1000 else 4000)
        }
    }
    var tab by remember { mutableStateOf("queue") }
    val big = size == "large" || size == "full"

    Column(modifier = modifier.widgetCard().padding(12.dp)) {
        val appName = st?.let { AppUtils.labelFor(ctx, it.pkg) }
        Text(
            "🎵 Музика" + (appName?.let { " · $it" } ?: ""),
            color = AppTheme.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis
        )

        val s = st
        if (s == null || (s.title.isBlank() && s.queue.isEmpty())) {
            EmptyState(ctx, access)
        } else {
            when (size) {
                "small" -> NowRow(s, compact = true)
                else -> {
                    NowRow(s, compact = false)
                    Progress(s, now)
                }
            }
        }

        if (big) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("queue" to "Черга", "history" to "Історія", "library" to "Бібліотека").forEach { (id, label) ->
                    val on = tab == id
                    Text(
                        label, fontSize = 12.sp, color = AppTheme.colors.textPrimary, maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (on) AppTheme.colors.accent.copy(alpha = 0.28f) else AppTheme.colors.textPrimary.copy(alpha = 0.08f))
                            .border(1.dp, if (on) AppTheme.colors.accent else AppTheme.colors.glassBorder.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                            .pressable(scaleDown = 0.94f) { tab = id }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                when (tab) {
                    "queue" -> QueueList(s)
                    "history" -> HistoryList(ctx, s?.pkg)
                    else -> LibraryPane(ctx)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(ctx: Context, access: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (access) "Нічого не грає" else "Дайте доступ до сповіщень, щоб бачити, що грає",
            color = AppTheme.colors.textPrimary, fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!access) Chip("Дозволити") {
                try { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
            }
            Chip("▶ Відкрити плеєр") { MediaHub.openPlayer(ctx) }
        }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Text(
        label, color = AppTheme.colors.textPrimary, fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AppTheme.colors.accent.copy(alpha = 0.28f))
            .border(1.dp, AppTheme.colors.accent, RoundedCornerShape(20.dp))
            .pressable(scaleDown = 0.94f) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun Art(bmp: Bitmap?, sizeDp: Int, radius: Int = 12) {
    val shape = RoundedCornerShape(radius.dp)
    if (bmp != null) {
        Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(sizeDp.dp).clip(shape))
    } else {
        Box(Modifier.size(sizeDp.dp).clip(shape).background(AppTheme.colors.accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Text("🎵", fontSize = (sizeDp * 0.45f).sp)
        }
    }
}

@Composable
private fun NowRow(s: NowPlaying, compact: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Art(s.art, if (compact) 40 else 52)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title.ifBlank { "Без назви" }, color = AppTheme.colors.textPrimary, fontSize = if (compact) 13.sp else 15.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (s.artist.isNotBlank()) Text(s.artist, color = AppTheme.colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!compact) MusicBtn("⏮") { MediaHub.previous() }
            MusicBtn(if (s.playing) "⏸" else "▶", main = true) { MediaHub.togglePlay() }
            MusicBtn("⏭") { MediaHub.next() }
        }
    }
}

@Composable
private fun Progress(s: NowPlaying, now: Long) {
    if (s.duration <= 0) return
    val pos = s.positionNow(now)
    Spacer(Modifier.height(8.dp))
    LinearProgressIndicator(
        progress = { (pos.toFloat() / s.duration).coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
        color = AppTheme.colors.accent,
        trackColor = AppTheme.colors.textPrimary.copy(alpha = 0.12f)
    )
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(fmt(pos), color = AppTheme.colors.textMuted, fontSize = 10.sp)
        Text(fmt(s.duration), color = AppTheme.colors.textMuted, fontSize = 10.sp)
    }
}

private fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

@Composable
private fun MusicBtn(icon: String, main: Boolean = false, onClick: () -> Unit) {
    val sz = if (main) 44.dp else 34.dp
    Box(
        modifier = Modifier
            .size(sz)
            .clip(CircleShape)
            .background(if (main) AppTheme.colors.accent.copy(alpha = 0.28f) else AppTheme.colors.textPrimary.copy(alpha = 0.08f))
            .border(1.dp, if (main) AppTheme.colors.accent else AppTheme.colors.glassBorder.copy(alpha = 0.7f), CircleShape)
            .pressable(scaleDown = 0.88f) { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(icon, color = AppTheme.colors.textPrimary, fontSize = if (main) 18.sp else 14.sp) }
}

@Composable
private fun TrackRow(art: Bitmap?, title: String, subtitle: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) AppTheme.colors.accent.copy(alpha = 0.16f) else AppTheme.colors.textPrimary.copy(alpha = 0.05f))
            .pressable(scaleDown = 0.97f, haptic = false) { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(art, 36, 8)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (active) AppTheme.colors.accent else AppTheme.colors.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = AppTheme.colors.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = AppTheme.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp))
}

@Composable
private fun QueueList(s: NowPlaying?) {
    val q = s?.queue.orEmpty()
    if (q.isEmpty()) { Hint("Черга недоступна: плеєр не передає її або нічого не грає."); return }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(q, key = { it.id }) { e ->
            TrackRow(e.icon, e.title, e.subtitle, e.id == s?.activeQueueId) { MediaHub.skipToQueueItem(e.id) }
        }
    }
}

@Composable
private fun HistoryList(ctx: Context, pkg: String?) {
    val tracks = remember { MusicHistory.get(ctx) }
    if (tracks.isEmpty()) { Hint("Історія зʼявиться після кількох прослуханих треків."); return }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(tracks, key = { it.key }) { t ->
            val art by produceState<Bitmap?>(null, t.key) { value = MusicHistory.art(ctx, t.key) }
            TrackRow(art, t.title, t.artist, false) { MediaHub.playFromSearch(ctx, t.title, t.artist, t.pkg.ifBlank { pkg }) }
        }
    }
}

@Composable
private fun LibraryPane(ctx: Context) {
    val pkg = remember { MediaHub.musicPackage(ctx) }
    if (pkg == null) { Hint("Не знайдено YT Music чи ReVanced."); return }
    val lib = remember(pkg) { YtLibrary(ctx, pkg) }
    DisposableEffect(lib) { lib.connect(); onDispose { lib.release() } }
    val st by lib.state.collectAsState()
    when (val s = st) {
        is LibState.Idle, is LibState.Connecting -> Hint("Підключаюсь до бібліотеки…")
        is LibState.Error -> Column {
            Hint(s.msg + ". Спробуйте вкладки «Черга» та «Історія».")
            Chip("Повторити") { lib.release(); lib.connect() }
        }
        is LibState.Items -> Column {
            if (s.depth > 0) Text(
                "‹ ${s.title}", color = AppTheme.colors.accent, fontSize = 13.sp,
                modifier = Modifier.pressable(scaleDown = 0.96f) { lib.back() }.padding(vertical = 6.dp, horizontal = 4.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(s.items, key = { it.mediaId + it.title }) { it0 ->
                    val art by produceState(it0.iconBitmap, it0.mediaId) {
                        if (value == null && it0.iconUri != null) value = ArtLoader.load(ctx, it0.iconUri)
                    }
                    TrackRow(art, (if (it0.browsable) "📁 " else "") + it0.title, it0.subtitle, false) { lib.open(it0) }
                }
            }
        }
    }
}
