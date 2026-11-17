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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.border
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.text.style.TextAlign
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
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
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

// Напівпрозоре «скло», як док і віджети лаунчера
private val IslandBlack = Color(0xFF07070B).copy(alpha = 0.86f)
private val PanelGlass = Color(0xFF101016).copy(alpha = 0.86f)
private val GlassEdge = Color.White.copy(alpha = 0.08f)
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

// ======================= Острівець: одна капсула, що «перетікає» =======================

/** Висота капсули: вписується у статус-бар біля камери і не закриває годинник та значки. */
val ISLAND_H = 30.dp

private const val COLLAPSE_MS = 380L

/**
 * Острівець у стилі Coucou: одна й та сама капсула морфиться між станами
 * (компактний → «визирання» → розгорнута панель), а персонаж переїжджає всередині неї як спільний елемент.
 * Вікно не змінює ширину (лише висоту), тож немає ривків убік.
 */
@Composable
fun IslandHost(
    mood: BuddyMood,
    micLevel: () -> Float,
    /** Сервіс уже розгорнув вікно на весь екран під панель. */
    expanded: Boolean,
    /** Вікно на всю ширину (шар спецможливостей). Інакше — вікно розміром з капсулу. */
    fullWidth: Boolean,
    onOpen: () -> Unit,
    /** Панель згорнулась (анімація закінчена) — можна стискати вікно. */
    onCollapsed: () -> Unit,
    onOpenChat: () -> Unit,
    onVoice: () -> Unit,
    onReplyFocus: (Boolean) -> Unit,
    onShade: () -> Unit,
    /** Потрібний розмір вікна (dp) для компактних станів: спершу більший, після анімації — точний. */
    onWindowSize: (Dp, Dp) -> Unit,
    /** На скільки опустити розгорнуту панель, щоб вона стала під статус-бар (годинник лишається видно). */
    openOffsetY: Dp = 0.dp
) {
    val scope = rememberCoroutineScope()
    val peek by IslandHub.peek.collectAsState()
    val live by IslandHub.live.collectAsState()
    val notifs by IslandHub.notifs.collectAsState()
    val np by MediaHub.state.collectAsState()
    val screenW = LocalConfiguration.current.screenWidthDp.dp

    // Закриття: спершу капсула стискається, потім сервіс зменшує вікно
    var collapsing by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) { if (!expanded) collapsing = false }
    val isOpen = expanded && !collapsing
    val closeThen: (() -> Unit) -> Unit = { after ->
        if (expanded && !collapsing) {
            collapsing = true
            onReplyFocus(false)
            scope.launch { delay(COLLAPSE_MS); onCollapsed(); after() }
        }
    }
    val close = { closeThen {} }

    // Нове сповіщення — персонаж дивується («!» і підскок)
    val pulse = peek?.id ?: 0L
    val shownMood = mood

    val topLive = live.filter { liveLabel(it, System.currentTimeMillis()).isNotBlank() }.minByOrNull { livePriority(it.kind) }
    val agentBusy = mood == BuddyMood.LISTEN || mood == BuddyMood.THINK || mood == BuddyMood.SPEAK
    val mode = when {
        isOpen -> "open"
        peek != null -> "peek"
        agentBusy -> "agent"
        topLive != null -> "live"
        np?.playing == true -> "music"
        else -> "idle"
    }
    val now = rememberNow(mode == "live" && topLive?.kind != LiveItem.Kind.PROGRESS && topLive?.kind != LiveItem.Kind.NAV)

    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val liveStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    val panelW = (screenW - 16.dp).coerceAtMost(400.dp)
    // Висота панелі — за вмістом поточної сторінки (повідомляє ExpandedContent)
    var openH by remember { mutableStateOf(320.dp) }
    val peekW = (screenW - 20.dp).coerceAtMost(420.dp)
    // Ширина капсули — за вмістом: ліва і права частини однакові (камера посередині),
    // кожна — за ширшим із двох боків. Лічильник сповіщень вміщується в мінімальну ширину.
    val liveText = if (mode == "live" && topLive != null) liveLabel(topLive, now) else ""
    val leftW = 34.dp + if (mode == "live" || mode == "music") 26.dp else 0.dp
    val rightW = when (mode) {
        "live" -> with(density) { measurer.measure(liveText, liveStyle).size.width.toDp() } + 10.dp
        "music" -> 26.dp
        else -> 24.dp
    }
    val side = maxOf(leftW, rightW, 36.dp)
    val tw = when (mode) {
        "open" -> panelW
        "peek" -> peekW
        else -> side * 2 + 30.dp
    }
    val th = when (mode) { "open" -> openH; "peek" -> 64.dp; else -> ISLAND_H }
    val offY by animateDpAsState(if (mode == "open") openOffsetY else 0.dp, spring(dampingRatio = 0.8f, stiffness = 420f), label = "oy")
    val tr = when (mode) { "open" -> 34.dp; "peek" -> 30.dp; else -> ISLAND_H / 2 }

    // Пружини: розкриття — м'яке з легким «желе», дрібні зміни — швидкі
    val big = mode == "open" || collapsing
    val sizeSpec = if (big) spring<Dp>(dampingRatio = 0.74f, stiffness = 420f) else spring(dampingRatio = 0.8f, stiffness = 620f)
    val w by animateDpAsState(tw, sizeSpec, label = "iw")
    val h by animateDpAsState(th, sizeSpec, label = "ih")
    val r by animateDpAsState(tr, sizeSpec, label = "ir")
    val bg by animateColorAsState(
        if (isOpen) AppTheme.colors.background.copy(alpha = 0.96f) else IslandBlack, tween(260), label = "ibg"
    )
    val scrim by animateFloatAsState(if (isOpen) 0.25f else 0f, tween(220), label = "scrim")

    // Персонаж — спільний елемент: їде й росте разом із капсулою
    val (bx, bpy, bs) = when (mode) {
        "open" -> Triple(18.dp, 16.dp, 46.dp)
        "peek" -> Triple(peekW - 46.dp, 15.dp, 34.dp)
        else -> Triple(4.dp, 2.dp, 26.dp)
    }
    val buddyX by animateDpAsState(bx, sizeSpec, label = "bx")
    val buddyY by animateDpAsState(bpy, sizeSpec, label = "by")
    val buddyS by animateDpAsState(bs, sizeSpec, label = "bs")

    // Компактні стани: вікно змінює лише висоту (на всю ширину) — двічі на перехід
    val lastW = remember { mutableStateOf(tw) }
    val lastH = remember { mutableStateOf(th) }
    LaunchedEffect(tw, th, expanded) {
        if (expanded) return@LaunchedEffect
        onWindowSize(maxOf(lastW.value, tw), maxOf(lastH.value, th))
        lastW.value = tw; lastH.value = th
        delay(420)
        onWindowSize(tw, th)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrim))
            .pointerInput(expanded, fullWidth) {
                // Поза капсулою: у розгорнутому стані дотик закриває панель,
                // у компактному (вікно на всю ширину) свайп униз відкриває системну шторку
                if (expanded) detectTapGestures { close() }
                else if (fullWidth) detectVerticalDragGestures { _, dy -> if (dy > 12f) onShade() }
            },
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            Modifier
                .offset(y = offY)
                .size(w, h)
                .clip(RoundedCornerShape(r))
                .background(bg)
                .border(0.5.dp, if (isOpen) AppTheme.colors.glassBorder else GlassEdge, RoundedCornerShape(r))
                .pointerInput(isOpen) {
                    detectTapGestures(
                        onTap = { if (!isOpen) onOpen() },
                        onLongPress = { if (!isOpen) onVoice() }
                    )
                }
                .pointerInput(isOpen) {
                    detectVerticalDragGestures { _, dy ->
                        if (!isOpen && dy > 10f) onOpen()
                        if (isOpen && dy < -16f) close()
                    }
                }
        ) {
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    val inDelay = if (targetState == "open") 110 else 60
                    fadeIn(tween(160, delayMillis = inDelay)) togetherWith fadeOut(tween(80))
                },
                contentAlignment = Alignment.TopStart,
                label = "island"
            ) { m ->
                when (m) {
                    "open" -> ExpandedContent(
                        Modifier.requiredWidth(panelW).wrapContentHeight(unbounded = true, align = Alignment.Top),
                        now = now, live = live, notifs = notifs, np = np,
                        onClose = close, onReplyFocus = onReplyFocus,
                        onHeight = { hh -> openH = hh }
                    )
                    "peek" -> peek?.let { p -> PeekContent(Modifier.requiredSize(peekW, 64.dp), p) } ?: Spacer(Modifier)
                    else -> Row(
                        Modifier.requiredSize(tw, ISLAND_H),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Ліворуч від камери: місце під персонажа (окремий шар) + іконка
                        Row(Modifier.width(side).padding(start = 34.dp), verticalAlignment = Alignment.CenterVertically) {
                            when (m) {
                                "live" -> topLive?.let { SmallIcon(it.icon, liveEmoji(it.kind)) }
                                "music" -> np?.let { SmallIcon(it.art, "🎵", rounded = 5) }
                            }
                        }
                        Spacer(Modifier.width(30.dp)) // камера
                        // Праворуч від камери: стан
                        Box(Modifier.width(side).padding(end = 6.dp), contentAlignment = Alignment.CenterEnd) {
                            when (m) {
                                "agent" -> IslandGlyph(mood, micLevel)
                                "live" -> topLive?.let { item ->
                                    Text(
                                        liveLabel(item, now), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                                        style = liveStyle,
                                        color = when (item.kind) {
                                            LiveItem.Kind.CALL -> AppTheme.colors.success
                                            LiveItem.Kind.TIMER -> Color(0xFFFFB74D)
                                            else -> Color.White
                                        },
                                    )
                                }
                                "music" -> EqBars(Modifier.size(20.dp, 14.dp))
                                else -> if (notifs.isNotEmpty()) {
                                    // Компактний лічильник: не ширший за значок стану, острів не розтягує
                                    Box(
                                        Modifier.heightIn(min = 16.dp).widthIn(min = 16.dp).clip(RoundedCornerShape(8.dp))
                                            .background(AppTheme.colors.accent).padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            if (notifs.size > 9) "9+" else "${notifs.size}", color = Color.White,
                                            fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp
                                        )
                                    }
                                } else IslandGlyph(mood, micLevel)
                            }
                        }
                    }
                }
            }
            // Персонаж поверх: тап у панелі — чат, утримання — голос; у компактному — розкрити
            OrbBuddy(
                mood = if (shownMood == BuddyMood.SLEEP && mode != "idle") BuddyMood.IDLE else shownMood,
                size = buddyS,
                micLevel = micLevel,
                modifier = Modifier.offset(buddyX, buddyY),
                pulse = pulse,
                onTap = { if (isOpen) closeThen(onOpenChat) else onOpen() },
                onLongPress = { if (isOpen) closeThen(onVoice) else onVoice() }
            )
        }
    }
}

/** Вміст розгорнутої панелі: шапка (місце під персонажа ліворуч) і сторінки, що гортаються вбік. */
@Composable
private fun ExpandedContent(
    modifier: Modifier,
    now: Long,
    live: List<LiveItem>,
    notifs: List<IslandNotif>,
    np: NowPlaying?,
    onClose: () -> Unit,
    onReplyFocus: (Boolean) -> Unit,
    onHeight: (Dp) -> Unit
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val maxPager = (LocalConfiguration.current.screenHeightDp * 0.62f).dp
    // Природна висота кожної сторінки → висота пейджера = поточна сторінка (у межах)
    val pageHeights = remember { mutableStateMapOf<Int, Dp>() }
    val battery by IslandHub.battery.collectAsState()
    val hasListener = remember { TriggerPermissions.hasNotificationListener(ctx) }
    val weather = remember { WeatherRepository(ctx).loadCachedWeather() }
    val pages = listOf("home", "widgets")
    val pager = rememberPagerState { pages.size }

    Column(
        modifier
            .onSizeChanged { onHeight(with(density) { it.height.toDp() }) }
            .padding(top = 14.dp, bottom = 8.dp)
    ) {
        // Шапка: ліворуч місце для персонажа (він — окремий шар і «приїжджає» сюди з капсули)
        Row(Modifier.fillMaxWidth().padding(start = 76.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(SimpleDateFormat("EEEE, d MMMM", Locale("uk")).format(Date(now)), color = Soft, fontSize = 11.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                (weather as? WeatherState.Data)?.let { w -> Text("${w.icon} ${w.tempC}°", color = Color.White, fontSize = 12.sp) }
                battery?.let { b ->
                    Text((if (b.charging) "⚡ " else if (b.level <= 15) "🪫 " else "🔋 ") + "${b.level}%", color = Soft, fontSize = 12.sp)
                }
            }
        }
        Text(
            "Торкніться мене — чат · утримуйте — голос",
            color = Color.White.copy(alpha = 0.35f), fontSize = 10.sp,
            modifier = Modifier.padding(start = 76.dp, top = 2.dp, bottom = 8.dp)
        )
        val pagerH = (pageHeights[pager.currentPage] ?: 200.dp).coerceIn(90.dp, maxPager)
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(pagerH)) { i ->
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        .onSizeChanged { pageHeights[i] = with(density) { it.height.toDp() } }
                ) {
                    when (pages[i]) {
                        "home" -> {
                            SuggestionsBlock(weather, onClose)
                            LiveList(live, now, onClose)
                            NotifsPage(notifs, hasListener, onClose, onReplyFocus)
                        }
                        "widgets" -> WidgetsPage(weather, np, onClose)
                    }
                }
            }
        }
        // Крапки сторінок
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
            pages.forEachIndexed { i, _ ->
                val sel = pager.currentPage == i
                val dw by animateDpAsState(if (sel) 16.dp else 6.dp, spring(stiffness = 600f), label = "dot")
                Box(
                    Modifier.padding(horizontal = 3.dp).size(dw, 6.dp).clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = if (sel) 0.85f else 0.3f))
                )
            }
        }
    }
}

/** «Визирання»: картка з аватаром, заголовком і текстом (персонаж — праворуч окремим шаром). */
@Composable
private fun PeekContent(modifier: Modifier, p: IslandPeek) {
    Row(modifier.padding(start = 12.dp, end = 54.dp), verticalAlignment = Alignment.CenterVertically) {
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

/** Пропозиції (перенесені з-над пошуку): проактивна підказка і часті програми. */
@Composable
private fun SuggestionsBlock(weather: WeatherState?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var hint by remember { mutableStateOf<com.dynamicai.launcher.suggestion.Hint?>(null) }
    LaunchedEffect(Unit) {
        hint = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            try { com.dynamicai.launcher.suggestion.ProactiveHints.current(ctx, weather) } catch (_: Throwable) { null }
        }
    }
    data class Fav(val pkg: String, val label: String, val icon: androidx.compose.ui.graphics.ImageBitmap)
    val favs = produceState<List<Fav>>(emptyList()) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val pm = ctx.packageManager
            com.dynamicai.launcher.suggestion.AppUsage.top(ctx, 10).mapNotNull { pkg ->
                try {
                    if (pm.getLaunchIntentForPackage(pkg) == null) return@mapNotNull null
                    val ai = pm.getApplicationInfo(pkg, 0)
                    Fav(pkg, pm.getApplicationLabel(ai).toString(), pm.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap())
                } catch (_: Throwable) { null }
            }.take(5)
        }
    }.value

    hint?.let { h ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f))
                .clickable { com.dynamicai.launcher.ui.runProactiveHint(ctx, h); hint = null; onClose() }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(h.icon, fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Text(h.text, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(h.actionLabel, color = AppTheme.colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(26.dp).clip(CircleShape).clickable {
                com.dynamicai.launcher.suggestion.ProactiveHints.snooze(ctx, h); hint = null
            }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, "Сховати", tint = Soft, modifier = Modifier.size(14.dp)) }
        }
    }
    if (favs.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            favs.forEach { f ->
                Column(
                    Modifier.width(58.dp).clip(RoundedCornerShape(14.dp)).clickable {
                        ctx.packageManager.getLaunchIntentForPackage(f.pkg)?.let {
                            it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            try { ctx.startActivity(it) } catch (_: Throwable) {}
                        }
                        com.dynamicai.launcher.suggestion.AppUsage.bump(ctx, f.pkg)
                        onClose()
                    }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(f.icon, f.label, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)))
                    Text(f.label, color = Soft, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}

/** Живі активності (таймери, дзвінки, завантаження) на першій сторінці панелі. */
@Composable
private fun LiveList(live: List<LiveItem>, now: Long, onClose: () -> Unit) {
    val ctx = LocalContext.current
    live.sortedBy { livePriority(it.kind) }.take(3).forEach { item ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { IslandHub.open(ctx, item.intent, item.pkg); onClose() }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(item.icon, liveEmoji(item.kind))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title.ifBlank { item.app }, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val prog = item.progress
                if (prog != null) {
                    LinearProgressIndicator(
                        progress = { prog }, color = AppTheme.colors.accent, trackColor = Faint,
                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                    )
                } else if (item.text.isNotBlank()) {
                    Text(item.text, color = Soft, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (item.kind == LiveItem.Kind.TIMER || item.kind == LiveItem.Kind.STOPWATCH || item.kind == LiveItem.Kind.CALL) {
                Text(chronoText(item, now), color = if (item.kind == LiveItem.Kind.TIMER) Color(0xFFFFB74D) else AppTheme.colors.success, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    if (live.isNotEmpty()) Spacer(Modifier.height(6.dp))
}

/** Друга сторінка: погода з прогнозом, найближчі події, стан пристрою, музика. */
@Composable
private fun WidgetsPage(weather: WeatherState?, np: NowPlaying?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val events = produceState<List<Pair<String, String>>>(emptyList()) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { upcomingEvents(ctx) }
    }.value
    val stats = produceState<com.dynamicai.launcher.system.SystemStats.Snapshot?>(null) {
        while (isActive) {
            value = try { com.dynamicai.launcher.system.SystemStats.snapshot(ctx) } catch (_: Throwable) { null }
            delay(3000)
        }
    }.value

    // Погода
    (weather as? WeatherState.Data)?.let { w ->
        Section {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(w.icon, fontSize = 26.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${w.tempC}° · ${w.description}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${w.tempMin}°…${w.tempMax}° · відчувається ${w.feelsLike}°", color = Soft, fontSize = 11.sp, maxLines = 1)
                    }
                }
                if (w.forecast.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        w.forecast.take(5).forEach { d ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(d.dayName.take(3), color = Soft, fontSize = 10.sp)
                                Text(d.icon, fontSize = 16.sp)
                                Text("${d.tempMax}°", color = Color.White, fontSize = 11.sp)
                                Text("${d.tempMin}°", color = Soft, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    // Події
    Section {
        Column(Modifier.fillMaxWidth().clickable {
            try {
                ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.provider.CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Throwable) {}
            onClose()
        }.padding(12.dp)) {
            Text("📅  Найближчі події", color = Soft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            if (events.isEmpty()) Text("Нічого на найближчу добу", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            events.take(3).forEach { (time, title) ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text(time, color = AppTheme.colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp))
                    Text(title, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    // Пристрій
    stats?.let { st ->
        Section {
            Column(Modifier.padding(12.dp)) {
                MiniBar("Заряд", "${st.batteryPct}%" + if (st.charging) " ⚡" else "", st.batteryPct / 100f)
                MiniBar("Пам'ять", "${st.ramUsedMb / 1024f}".take(4) + " / ${st.ramTotalMb / 1024} ГБ", if (st.ramTotalMb > 0) st.ramUsedMb.toFloat() / st.ramTotalMb else 0f)
                MiniBar("Сховище", "${st.storageUsedGb.toInt()} / ${st.storageTotalGb.toInt()} ГБ", if (st.storageTotalGb > 0) st.storageUsedGb / st.storageTotalGb else 0f)
            }
        }
    }
    // Музика
    np?.takeIf { it.title.isNotBlank() }?.let { NowPlayingCard(it) }
}

@Composable
private fun MiniBar(label: String, value: String, frac: Float) {
    Column(Modifier.padding(vertical = 3.dp)) {
        Row {
            Text(label, color = Soft, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(value, color = Color.White, fontSize = 11.sp)
        }
        LinearProgressIndicator(
            progress = { frac.coerceIn(0f, 1f) },
            color = when { frac > 0.9f -> AppTheme.colors.danger; frac > 0.75f -> AppTheme.colors.warning; else -> AppTheme.colors.accent },
            trackColor = Faint,
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
        )
    }
}

/** Події календаря на найближчі 24 години: (час/«весь день», назва). */
private fun upcomingEvents(ctx: android.content.Context): List<Pair<String, String>> {
    if (ctx.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED) return emptyList()
    val now = System.currentTimeMillis()
    val uri = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon()
        .appendPath(now.toString()).appendPath((now + 24 * 3600_000L).toString()).build()
    val out = mutableListOf<Pair<String, String>>()
    try {
        ctx.contentResolver.query(
            uri,
            arrayOf(android.provider.CalendarContract.Instances.TITLE, android.provider.CalendarContract.Instances.BEGIN,
                android.provider.CalendarContract.Instances.END, android.provider.CalendarContract.Instances.ALL_DAY),
            null, null, android.provider.CalendarContract.Instances.BEGIN + " ASC"
        )?.use { c ->
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            while (c.moveToNext() && out.size < 5) {
                val end = c.getLong(2)
                if (end < now) continue
                val title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "Подія"
                val time = if (c.getInt(3) != 0) "весь день" else fmt.format(Date(c.getLong(1)))
                out += time to title
            }
        }
    } catch (_: Throwable) {}
    return out
}

@Composable
private fun NotifsPage(notifs: List<IslandNotif>, hasListener: Boolean, onClose: () -> Unit, onReplyFocus: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    if (!hasListener) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Щоб бачити сповіщення тут, дайте доступ до них", color = Color.White, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            ActionPill("Надати доступ", Modifier.fillMaxWidth(0.7f)) {
                try {
                    ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Throwable) {}
                onClose()
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Сповіщення" + if (notifs.isNotEmpty()) " · ${notifs.size}" else "", color = Soft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (notifs.any { it.clearable }) {
            Text("Очистити", color = AppTheme.colors.accent, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { IslandHub.dismissAll() }.padding(4.dp))
        }
    }
    if (notifs.isEmpty()) {
        Text("Нових сповіщень немає", color = Soft, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(), textAlign = TextAlign.Center)
    }
    notifs.take(12).forEach { n -> key(n.key) { NotifRow(n, onClose, onReplyFocus) } }
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
