package com.dynamicai.launcher.overlay

import android.graphics.Bitmap
import android.os.SystemClock
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.data.WeatherRepository
import com.dynamicai.launcher.data.WeatherState
import com.dynamicai.launcher.music.MediaHub
import com.dynamicai.launcher.music.NowPlaying
import com.dynamicai.launcher.macro.TriggerPermissions
import com.dynamicai.launcher.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

private val IslandBlack = Color(0xFF000000)
private val PanelBlack = Color(0xFF0B0B0F)
private val Soft = Color.White.copy(alpha = 0.62f)
private val Faint = Color.White.copy(alpha = 0.10f)

/** Таймер/секундомір як текст. */
private fun chronoText(item: LiveItem, now: Long): String {
    val ms = if (item.countDown) (item.chrono - now).coerceAtLeast(0) else (now - item.chrono).coerceAtLeast(0)
    return DateUtils.formatElapsedTime(ms / 1000)
}

private fun livePriority(k: LiveItem.Kind) = when (k) {
    LiveItem.Kind.CALL -> 0; LiveItem.Kind.TIMER -> 1; LiveItem.Kind.NAV -> 2
    LiveItem.Kind.STOPWATCH -> 3; LiveItem.Kind.PROGRESS -> 4
}

/** Тікає раз на секунду, лише поки потрібен (є таймери/секундоміри). */
@Composable
private fun rememberNow(active: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        while (active && isActive) { now = System.currentTimeMillis(); delay(1000L - now % 1000L) }
    }
    return now
}

// ======================= Компактний острівець =======================

/** Висота капсули: вписується у статус-бар біля камери і не закриває годинник та значки. */
val ISLAND_H = 30.dp

@Composable
fun IslandCompact(
    mood: BuddyMood,
    micLevel: () -> Float,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSwipeDown: () -> Unit,
    /** Потрібний розмір вікна (dp). Вікно змінюється лише двічі на перехід, анімація — всередині нього. */
    onWindowSize: (Dp, Dp) -> Unit
) {
    val peek by IslandHub.peek.collectAsState()
    val live by IslandHub.live.collectAsState()
    val notifs by IslandHub.notifs.collectAsState()
    val np by MediaHub.state.collectAsState()
    val screenW = LocalConfiguration.current.screenWidthDp.dp

    val topLive = live.filter { liveLabel(it, System.currentTimeMillis()).isNotBlank() }.minByOrNull { livePriority(it.kind) }
    val agentBusy = mood == BuddyMood.LISTEN || mood == BuddyMood.THINK || mood == BuddyMood.SPEAK
    val mode = when {
        peek != null -> "peek"
        agentBusy -> "agent"
        topLive != null -> "live"
        np?.playing == true -> "music"
        else -> "idle"
    }
    val now = rememberNow(mode == "live" && topLive?.kind != LiveItem.Kind.PROGRESS && topLive?.kind != LiveItem.Kind.NAV)

    // Цільові розміри: компактні стани вузькі (видно годинник і значки), «визирання» — картка вниз
    val tw = when (mode) {
        "peek" -> (screenW - 20.dp).coerceAtMost(420.dp)
        "live" -> 150.dp
        "music" -> 128.dp
        "agent" -> 118.dp
        else -> if (notifs.isNotEmpty()) 118.dp else 108.dp
    }
    val th = if (mode == "peek") 64.dp else ISLAND_H
    val sizeSpec = spring<Dp>(dampingRatio = 0.82f, stiffness = 700f)
    val w by animateDpAsState(tw, sizeSpec, label = "iw")
    val h by animateDpAsState(th, sizeSpec, label = "ih")

    // Вікно: спершу більше з двох розмірів (щоб анімація не обрізалась), після анімації — точний розмір
    val lastW = remember { mutableStateOf(tw) }
    val lastH = remember { mutableStateOf(th) }
    LaunchedEffect(tw, th) {
        onWindowSize(maxOf(lastW.value, tw), maxOf(lastH.value, th))
        lastW.value = tw; lastH.value = th
        delay(420)
        onWindowSize(tw, th)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .size(w, h)
                .clip(RoundedCornerShape(minOf(h / 2, 26.dp)))
                .background(IslandBlack)
                .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() }) }
                .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy > 10f) onSwipeDown() } }
        ) {
            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn(tween(140, delayMillis = 60)) togetherWith fadeOut(tween(90)) },
                contentAlignment = Alignment.Center,
                label = "island"
            ) { m ->
                if (m == "peek") {
                    peek?.let { p -> PeekContent(p, mood, micLevel) } ?: Spacer(Modifier.fillMaxSize())
                } else {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Ліворуч від камери: персонаж (+ іконка)
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            OrbBuddy(mood = mood, size = 26.dp, micLevel = micLevel, onTap = onTap, onLongPress = onLongPress)
                            when (m) {
                                "live" -> topLive?.let { SmallIcon(it.icon, liveEmoji(it.kind)) }
                                "music" -> np?.let { SmallIcon(it.art, "🎵", rounded = 5) }
                            }
                        }
                        Spacer(Modifier.width(30.dp)) // камера
                        // Праворуч від камери: стан
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                            when (m) {
                                "agent" -> IslandGlyph(mood, micLevel)
                                "live" -> topLive?.let { item ->
                                    Text(
                                        liveLabel(item, now), maxLines = 1, overflow = TextOverflow.Clip,
                                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                        color = when (item.kind) {
                                            LiveItem.Kind.CALL -> AppTheme.colors.success
                                            LiveItem.Kind.TIMER -> Color(0xFFFFB74D)
                                            else -> Color.White
                                        },
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                }
                                "music" -> EqBars(Modifier.padding(end = 4.dp).size(20.dp, 14.dp))
                                else -> if (notifs.isNotEmpty()) {
                                    Box(
                                        Modifier.padding(end = 3.dp).clip(CircleShape).background(AppTheme.colors.accent)
                                            .padding(horizontal = 6.dp, vertical = 1.dp),
                                        contentAlignment = Alignment.Center
                                    ) { Text("${notifs.size.coerceAtMost(99)}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                                } else IslandGlyph(mood, micLevel)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** «Визирання»: картка з аватаром, заголовком і текстом. */
@Composable
private fun PeekContent(p: IslandPeek, mood: BuddyMood, micLevel: () -> Float) {
    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Faint), contentAlignment = Alignment.Center) {
            if (p.icon != null) {
                val img = remember(p.icon) { p.icon.asImageBitmap() }
                Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else Text(p.emoji ?: "🔔", fontSize = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (p.text.isNotBlank()) Text(p.text, color = Soft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        OrbBuddy(mood = if (mood == BuddyMood.SLEEP) BuddyMood.IDLE else mood, size = 30.dp, micLevel = micLevel)
    }
}

/** Короткий текст живої активності для капсули (порожній — нема чого показувати). */
private fun liveLabel(item: LiveItem, now: Long): String = when (item.kind) {
    LiveItem.Kind.PROGRESS -> item.progress?.let { "${(it * 100).toInt()}%" }.orEmpty()
    LiveItem.Kind.NAV -> item.title.ifBlank { item.text }.take(12)
    else -> chronoText(item, now)
}

private fun liveEmoji(k: LiveItem.Kind) = when (k) {
    LiveItem.Kind.CALL -> "📞"; LiveItem.Kind.TIMER -> "⏳"; LiveItem.Kind.STOPWATCH -> "⏱"
    LiveItem.Kind.NAV -> "🧭"; LiveItem.Kind.PROGRESS -> "⬇"
}

@Composable
private fun SmallIcon(bmp: Bitmap?, emoji: String?, rounded: Int = 50) {
    Spacer(Modifier.width(4.dp))
    if (bmp != null) {
        val img = remember(bmp) { bmp.asImageBitmap() }
        Image(img, null, Modifier.size(22.dp).clip(RoundedCornerShape(rounded)), contentScale = ContentScale.Crop)
    } else if (emoji != null) {
        Text(emoji, fontSize = 14.sp)
    }
}

/** Еквалайзер «грає музика». */
@Composable
private fun EqBars(modifier: Modifier) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (isActive) withFrameMillis { t = (it - start) / 1000f }
    }
    val c = AppTheme.colors.accentLight
    Canvas(modifier) {
        val n = 4
        val w = size.width / (n * 1.6f)
        for (i in 0 until n) {
            val k = 0.3f + 0.7f * abs(sin(t * (5f + i * 1.3f) + i * 1.1f))
            val h = size.height * k
            val x = w * 0.8f + i * w * 1.6f
            drawLine(c, Offset(x, size.height - h), Offset(x, size.height), w, StrokeCap.Round)
        }
    }
}

/** Значок стану праворуч у капсулі (для «слухає/думає/говорить» та спокою). */
@Composable
private fun IslandGlyph(mood: BuddyMood, micLevel: () -> Float) {
    val colors = AppTheme.colors
    var t by remember { mutableFloatStateOf(0f) }
    val animated = mood == BuddyMood.LISTEN || mood == BuddyMood.THINK || mood == BuddyMood.SPEAK
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (isActive) withFrameMillis { t = (it - start) / 1000f }
    }
    Canvas(Modifier.size(18.dp)) {
        val w = size.width; val h = size.height
        when (mood) {
            BuddyMood.LISTEN, BuddyMood.SPEAK -> {
                val lvl = if (mood == BuddyMood.LISTEN) micLevel().coerceIn(0f, 1f) else 0.6f
                for (i in 0..2) {
                    val k = 0.35f + 0.65f * abs(sin(t * (6f + i * 1.7f) + i)) * (0.4f + 0.6f * lvl)
                    val bh = h * k; val x = w * (0.2f + i * 0.3f)
                    drawLine(colors.accentLight, Offset(x, h / 2 - bh / 2), Offset(x, h / 2 + bh / 2), w * 0.14f, StrokeCap.Round)
                }
            }
            BuddyMood.THINK -> drawArc(colors.purple, (t * 300f) % 360f, 260f, false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.14f, cap = StrokeCap.Round))
            BuddyMood.ERROR -> drawCircle(colors.danger, radius = w * 0.22f)
            BuddyMood.HAPPY -> drawCircle(colors.success, radius = w * 0.22f)
            else -> drawCircle(colors.accent.copy(alpha = 0.6f), radius = w * 0.14f)
        }
    }
}

// ======================= Розгорнута панель =======================

@Composable
fun IslandPanel(
    mood: BuddyMood,
    micLevel: () -> Float,
    onClose: () -> Unit,
    onOpenChat: () -> Unit,
    onVoice: () -> Unit,
    onReplyFocus: (Boolean) -> Unit
) {
    val ctx = LocalContext.current
    val live by IslandHub.live.collectAsState()
    val notifs by IslandHub.notifs.collectAsState()
    val battery by IslandHub.battery.collectAsState()
    val np by MediaHub.state.collectAsState()
    val hasListener = remember { TriggerPermissions.hasNotificationListener(ctx) }
    val weather = remember { WeatherRepository(ctx).loadCachedWeather() }
    val now = rememberNow(true)
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.8f).dp

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scrim by animateFloatAsState(if (visible) 0.35f else 0f, tween(200), label = "scrim")

    // Вікно на весь екран: анімація не змінює його розмір (плавно), дотик по затемненню закриває панель
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrim))
            .pointerInput(Unit) { detectTapGestures { onClose() } }
    ) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(110)) + scaleIn(
            initialScale = 0.6f, transformOrigin = TransformOrigin(0.5f, 0f),
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 650f)
        ),
        exit = fadeOut(tween(90))
    ) {
        Column(
            Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .fillMaxWidth()
                .heightIn(max = maxH)
                .clip(RoundedCornerShape(30.dp))
                .background(PanelBlack)
                .pointerInput(Unit) { detectTapGestures { } } // дотики по панелі не закривають її
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            // --- Шапка: персонаж, час, погода, заряд ---
            Row(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy < -14f) onClose() } },
                verticalAlignment = Alignment.CenterVertically
            ) {
                OrbBuddy(mood = mood, size = 44.dp, micLevel = micLevel, onTap = onOpenChat, onLongPress = onVoice)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(SimpleDateFormat("EEEE, d MMMM", Locale("uk")).format(Date(now)), color = Soft, fontSize = 11.sp)
                }
                (weather as? WeatherState.Data)?.let { w ->
                    Chip("${w.icon} ${w.tempC}°")
                    Spacer(Modifier.width(6.dp))
                }
                battery?.let { b -> Chip((if (b.charging) "⚡" else if (b.level <= 15) "🪫" else "🔋") + " ${b.level}%") }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionPill("💬  Чат з асистентом", Modifier.weight(1f)) { onOpenChat() }
                ActionPill("🎤  Голос", Modifier.weight(0.6f)) { onVoice() }
            }
            Spacer(Modifier.height(12.dp))

            // --- Живі активності ---
            live.sortedBy { livePriority(it.kind) }.forEach { item ->
                Section {
                    Row(
                        Modifier.fillMaxWidth().clickable { IslandHub.open(ctx, item.intent, item.pkg); onClose() }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(item.icon, liveEmoji(item.kind))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title.ifBlank { item.app }, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (item.text.isNotBlank()) Text(item.text, color = Soft, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            item.progress?.let { p ->
                                LinearProgressIndicator(
                                    progress = { p }, color = AppTheme.colors.accent, trackColor = Faint,
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                                )
                            }
                        }
                        if (item.kind == LiveItem.Kind.TIMER || item.kind == LiveItem.Kind.STOPWATCH || item.kind == LiveItem.Kind.CALL) {
                            Text(chronoText(item, now), color = if (item.kind == LiveItem.Kind.TIMER) Color(0xFFFFB74D) else AppTheme.colors.success, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // --- Музика ---
            np?.takeIf { it.title.isNotBlank() }?.let { NowPlayingCard(it) }

            // --- Сповіщення ---
            if (!hasListener) {
                Section {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            try { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}; onClose()
                        }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.NotificationsOff, null, tint = AppTheme.colors.warning)
                        Spacer(Modifier.width(10.dp))
                        Text("Дайте доступ до сповіщень, щоб бачити їх тут", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("Надати", color = AppTheme.colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else if (notifs.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Сповіщення · ${notifs.size}", color = Soft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (notifs.any { it.clearable }) {
                        Text("Очистити", color = AppTheme.colors.accent, fontSize = 12.sp, modifier = Modifier.clickable { IslandHub.dismissAll() }.padding(4.dp))
                    }
                }
                notifs.take(8).forEach { n ->
                    key(n.key) { NotifRow(n, onClose, onReplyFocus) }
                }
            } else {
                Text("Нових сповіщень немає", color = Soft, fontSize = 12.sp, modifier = Modifier.padding(6.dp))
            }

            // Ручка-закривачка
            Box(
                Modifier.fillMaxWidth().padding(top = 8.dp).clickable { onClose() }.padding(6.dp),
                contentAlignment = Alignment.Center
            ) { Box(Modifier.size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.3f))) }
        }
    }
    }
}

@Composable
private fun ActionPill(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(Faint).clickable { onClick() }.padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
}

@Composable
private fun Chip(text: String) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(Faint).padding(horizontal = 9.dp, vertical = 4.dp)) {
        Text(text, color = Color.White, fontSize = 11.sp)
    }
}

@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.06f)), content = content)
}

@Composable
private fun Avatar(bmp: Bitmap?, emoji: String?) {
    Box(Modifier.size(36.dp).clip(CircleShape).background(Faint), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            val img = remember(bmp) { bmp.asImageBitmap() }
            Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (emoji != null) Text(emoji, fontSize = 16.sp)
    }
}

@Composable
private fun NowPlayingCard(np: NowPlaying) {
    val ctx = LocalContext.current
    var pos by remember { mutableLongStateOf(np.positionNow(SystemClock.elapsedRealtime())) }
    LaunchedEffect(np) {
        while (isActive) { pos = np.positionNow(SystemClock.elapsedRealtime()); delay(500) }
    }
    Section {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Faint).clickable { MediaHub.openPlayer(ctx) }, contentAlignment = Alignment.Center) {
                np.art?.let { a ->
                    val img = remember(a) { a.asImageBitmap() }
                    Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } ?: Text("🎵", fontSize = 20.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(np.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(np.artist, color = Soft, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (np.duration > 0) {
                    LinearProgressIndicator(
                        progress = { (pos.toFloat() / np.duration).coerceIn(0f, 1f) }, color = Color.White, trackColor = Faint,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            MediaBtn(Icons.Default.SkipPrevious) { MediaHub.previous() }
            MediaBtn(if (np.playing) Icons.Default.Pause else Icons.Default.PlayArrow, big = true) { MediaHub.togglePlay() }
            MediaBtn(Icons.Default.SkipNext) { MediaHub.next() }
        }
    }
}

@Composable
private fun MediaBtn(icon: ImageVector, big: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(if (big) 40.dp else 34.dp).clip(CircleShape).background(if (big) Color.White else Color.Transparent).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = if (big) Color.Black else Color.White, modifier = Modifier.size(if (big) 24.dp else 22.dp)) }
}

@Composable
private fun NotifRow(n: IslandNotif, onClose: () -> Unit, onReplyFocus: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var replying by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    Section {
        Row(
            Modifier.fillMaxWidth().clickable { IslandHub.open(ctx, n.intent, n.pkg); onClose() }.padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Avatar(n.icon, "🔔")
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n.app, color = Soft, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text(" · " + DateUtils.getRelativeTimeSpanString(n.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS), color = Soft, fontSize = 10.sp, maxLines = 1)
                }
                Text(n.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (n.text.isNotBlank()) Text(n.text, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (sent) Text("✓ Надіслано", color = AppTheme.colors.success, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (n.clearable) {
                    Box(Modifier.size(32.dp).clip(CircleShape).clickable { IslandHub.dismiss(n.key) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, "Прибрати", tint = Soft, modifier = Modifier.size(18.dp))
                    }
                }
                if (n.reply != null && !sent) {
                    Box(Modifier.size(32.dp).clip(CircleShape).clickable {
                        replying = !replying
                        if (!replying) onReplyFocus(false)
                    }, contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.Reply, "Відповісти", tint = AppTheme.colors.accent, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        if (replying) {
            LaunchedEffect(Unit) {
                onReplyFocus(true)
                delay(120)
                try { focus.requestFocus() } catch (_: Throwable) {}
            }
            val send = {
                val t = replyText.trim()
                if (t.isNotEmpty() && IslandHub.reply(ctx, n, t)) {
                    sent = true; replying = false; replyText = ""
                    onReplyFocus(false)
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = replyText, onValueChange = { replyText = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    cursorBrush = SolidColor(AppTheme.colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(Faint)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .focusRequester(focus),
                    decorationBox = { inner ->
                        if (replyText.isEmpty()) Text("Відповідь…", color = Soft, fontSize = 13.sp)
                        inner()
                    }
                )
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(38.dp).clip(CircleShape).background(AppTheme.colors.accent).clickable { send() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Надіслати", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
