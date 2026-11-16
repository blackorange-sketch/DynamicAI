package com.dynamicai.launcher.overlay

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/** Настрій персонажа асистента. */
enum class BuddyMood { IDLE, SLEEP, LISTEN, THINK, SPEAK, HAPPY, ERROR }

/**
 * Персонаж асистента: м'який «squircle» з очима, намальований кодом.
 * Дихає, кліпає, роззирається; реагує на голос (рівень мікрофона), думає, говорить, радіє, засинає.
 * Подвійне натискання — запаморочення, довге — голос. У сні кадр майже статичний (не тратить батарею).
 */
@Composable
fun OrbBuddy(
    mood: BuddyMood,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    micLevel: () -> Float = { 0f },
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onDrag: ((Float, Float) -> Unit)? = null,
    onDragEnd: () -> Unit = {},
) {
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLong by rememberUpdatedState(onLongPress)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    // Час у секундах — крутиться лише поки персонаж не спить
    var time by remember { mutableFloatStateOf(0f) }
    var dizzyUntil by remember { mutableLongStateOf(0L) }
    var zTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(mood) {
        if (mood == BuddyMood.SLEEP) {
            // У сні — лише повільна зміна «z», раз на 0.7 с
            while (isActive) { delay(700); zTick++ }
        } else {
            val start = withFrameMillis { it } - (time * 1000).toLong()
            while (isActive) {
                withFrameMillis { now -> time = (now - start) / 1000f }
            }
        }
    }

    // Кліпання
    val blink = remember { Animatable(1f) }
    LaunchedEffect(mood) {
        if (mood == BuddyMood.SLEEP || mood == BuddyMood.HAPPY || mood == BuddyMood.ERROR) return@LaunchedEffect
        while (isActive) {
            delay(Random.nextLong(2200, 5200))
            blink.animateTo(0.05f, tween(70))
            blink.animateTo(1f, tween(110))
            if (Random.nextInt(5) == 0) { // інколи подвійне
                delay(90); blink.animateTo(0.05f, tween(70)); blink.animateTo(1f, tween(110))
            }
        }
    }

    // Куди дивиться (−1..1): блукає, коли нічого не робить
    val lookX = remember { Animatable(0f) }
    val lookY = remember { Animatable(0f) }
    LaunchedEffect(mood) {
        when (mood) {
            BuddyMood.IDLE -> while (isActive) {
                delay(Random.nextLong(1500, 3800))
                val tx = if (Random.nextInt(3) == 0) 0f else Random.nextFloat() * 2f - 1f
                val ty = Random.nextFloat() * 1.2f - 0.6f
                launch { lookX.animateTo(tx, spring(dampingRatio = 0.7f, stiffness = 120f)) }
                lookY.animateTo(ty, spring(dampingRatio = 0.7f, stiffness = 120f))
            }
            BuddyMood.LISTEN, BuddyMood.SPEAK, BuddyMood.HAPPY -> {
                launch { lookX.animateTo(0f, spring(stiffness = 200f)) }
                lookY.animateTo(0.15f, spring(stiffness = 200f))
            }
            else -> {}
        }
    }

    // Стискання від натискання і стрибок радості
    val squish = remember { Animatable(0f) }
    val jump = remember { Animatable(0f) }
    LaunchedEffect(mood) {
        if (mood == BuddyMood.HAPPY) {
            repeat(2) {
                squish.animateTo(0.12f, tween(90))
                launch { squish.animateTo(-0.08f, tween(160)) }
                jump.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
                jump.animateTo(0f, tween(220))
                squish.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 400f))
            }
        }
    }

    val level = micLevel
    val listenBoost by animateFloatAsState(if (mood == BuddyMood.LISTEN) 1f else 0f, spring(stiffness = 300f), label = "listen")

    Canvas(
        modifier
            .size(size)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        scope.launch {
                            squish.snapTo(0.18f)
                            squish.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMediumLow))
                        }
                        currentOnTap()
                    },
                    onDoubleTap = {
                        dizzyUntil = System.currentTimeMillis() + 2600
                        scope.launch {
                            squish.snapTo(0.25f)
                            squish.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = Spring.StiffnessLow))
                        }
                    },
                    onLongPress = { currentOnLong() }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragEnd() }
                ) { change, drag ->
                    val cb = currentOnDrag ?: return@detectDragGestures
                    change.consume()
                    cb(drag.x, drag.y)
                    // Дивиться туди, куди тягнуть
                    scope.launch { lookX.snapTo((drag.x / 6f).coerceIn(-1f, 1f)) }
                    scope.launch { lookY.snapTo((drag.y / 6f).coerceIn(-1f, 1f)) }
                }
            }
    ) {
        val t = time
        // Читаємо в фазі малювання: кадри й так оновлюються щокадру
        val dizzy = dizzyUntil > System.currentTimeMillis()
        val s = this.size.minDimension
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val body = s * 0.74f

        // Дихання: сон — повільно і глибоко (тут статично), решта — легко
        val breath = when (mood) {
            BuddyMood.SLEEP -> 0.02f
            BuddyMood.SPEAK -> 0.06f * abs(sin(t * 9f)) + 0.02f * sin(t * 2.1f)
            else -> 0.025f * sin(t * 2.2f)
        }
        val lvl = if (mood == BuddyMood.LISTEN) level().coerceIn(0f, 1f) else 0f
        val sq = squish.value
        val scaleX = 1f - breath * 0.6f + sq + lvl * 0.06f
        val scaleY = 1f + breath - sq + lvl * 0.06f
        val lift = -jump.value * s * 0.16f + if (mood == BuddyMood.SLEEP) s * 0.03f else 0f
        val wobble = when {
            dizzy -> sin(t * 14f) * 9f
            mood == BuddyMood.ERROR -> sin(t * 22f) * 6f * (1f - ((t * 0.6f) % 1f))
            mood == BuddyMood.THINK -> sin(t * 1.6f) * 4f
            else -> 0f
        }

        // Тінь
        val shadowW = body * (0.8f - jump.value * 0.25f)
        drawOval(
            color = Color.Black.copy(alpha = 0.22f - jump.value * 0.1f),
            topLeft = Offset(c.x - shadowW / 2f, c.y + body * 0.47f),
            size = Size(shadowW, body * 0.12f)
        )

        // Аура слухання — кільця від гучності
        if (listenBoost > 0.01f) {
            val r = body * (0.58f + 0.12f * lvl + 0.04f * sin(t * 6f))
            drawCircle(colors.accent.copy(alpha = 0.18f * listenBoost), radius = r, center = c.copy(y = c.y + lift))
            drawCircle(
                colors.accent.copy(alpha = 0.35f * listenBoost), radius = r * 1.12f, center = c.copy(y = c.y + lift),
                style = Stroke(width = s * 0.02f)
            )
        }

        withTransform({
            translate(0f, lift)
            rotate(wobble, pivot = c)
            scale(scaleX, scaleY, pivot = Offset(c.x, c.y + body * 0.5f))
        }) {
            // Тіло
            val path = squircle(c, body / 2f, body / 2f)
            val top = when (mood) {
                BuddyMood.ERROR -> colors.danger
                BuddyMood.SLEEP -> colors.accentDark
                else -> colors.accentLight
            }
            val bottom = when (mood) {
                BuddyMood.ERROR -> colors.danger.copy(alpha = 0.85f)
                BuddyMood.THINK -> colors.purple
                else -> colors.accent
            }
            drawPath(path, Brush.verticalGradient(listOf(top, bottom), startY = c.y - body / 2f, endY = c.y + body / 2f))
            // Відблиск
            drawOval(
                color = Color.White.copy(alpha = 0.28f),
                topLeft = Offset(c.x - body * 0.32f, c.y - body * 0.42f),
                size = Size(body * 0.34f, body * 0.16f)
            )
            // Рум'янці
            if (mood == BuddyMood.HAPPY || mood == BuddyMood.SPEAK || mood == BuddyMood.IDLE) {
                val blush = Color(0xFFFF8FA3).copy(alpha = if (mood == BuddyMood.HAPPY) 0.55f else 0.25f)
                drawOval(blush, Offset(c.x - body * 0.40f, c.y + body * 0.06f), Size(body * 0.16f, body * 0.09f))
                drawOval(blush, Offset(c.x + body * 0.24f, c.y + body * 0.06f), Size(body * 0.16f, body * 0.09f))
            }

            drawFace(mood, dizzy, c, body, t, blink.value, lookX.value, lookY.value, lvl, listenBoost, zTick)
        }

        // Крапки «думаю»
        if (mood == BuddyMood.THINK) {
            for (i in 0..2) {
                val ph = t * 5f - i * 0.6f
                val dy = -abs(sin(ph)) * s * 0.05f
                drawCircle(
                    colors.textPrimary.copy(alpha = 0.85f), radius = s * 0.035f,
                    center = Offset(c.x + body * 0.38f + i * s * 0.09f, c.y - body * 0.62f + dy + lift)
                )
            }
        }
    }
}

/** Острівець угорі екрана: чорна капсула біля камери з маленьким персонажем і (пізніше) текстом/сповіщеннями. */
@Composable
fun OrbIsland(
    mood: BuddyMood,
    text: String?,
    micLevel: () -> Float,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black)
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 380f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() }) }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OrbBuddy(mood = mood, size = 30.dp, micLevel = micLevel, onTap = onTap, onLongPress = onLongPress)
        // Місце під камеру посередині капсули
        Spacer(Modifier.width(if (text.isNullOrBlank()) 44.dp else 10.dp))
        if (!text.isNullOrBlank()) {
            Text(
                text, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp).padding(end = 8.dp)
            )
        } else {
            // Індикатор стану праворуч
            Box(Modifier.size(width = 22.dp, height = 30.dp), contentAlignment = Alignment.Center) {
                StatusGlyph(mood, micLevel)
            }
        }
    }
}

@Composable
private fun StatusGlyph(mood: BuddyMood, micLevel: () -> Float) {
    val colors = AppTheme.colors
    var t by remember { mutableFloatStateOf(0f) }
    val animated = mood == BuddyMood.LISTEN || mood == BuddyMood.THINK || mood == BuddyMood.SPEAK
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (isActive) withFrameMillis { t = (it - start) / 1000f }
    }
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        when (mood) {
            BuddyMood.LISTEN, BuddyMood.SPEAK -> {
                // Еквалайзер: 3 смужки
                val lvl = if (mood == BuddyMood.LISTEN) micLevel().coerceIn(0f, 1f) else 0.6f
                for (i in 0..2) {
                    val k = 0.35f + 0.65f * abs(sin(t * (6f + i * 1.7f) + i)) * (0.4f + 0.6f * lvl)
                    val bh = h * k
                    val x = w * (0.2f + i * 0.3f)
                    drawLine(colors.accentLight, Offset(x, h / 2 - bh / 2), Offset(x, h / 2 + bh / 2), w * 0.14f, StrokeCap.Round)
                }
            }
            BuddyMood.THINK -> {
                val a = (t * 300f) % 360f
                drawArc(colors.purple, a, 260f, false, style = Stroke(w * 0.14f, cap = StrokeCap.Round))
            }
            BuddyMood.ERROR -> drawCircle(colors.danger, radius = w * 0.22f)
            BuddyMood.HAPPY -> drawCircle(colors.success, radius = w * 0.22f)
            else -> drawCircle(colors.accent.copy(alpha = 0.6f), radius = w * 0.14f)
        }
    }
}

// ===== Малювання =====

/** Суперелліпс (n = 4) — м'який квадратик. */
private fun squircle(c: Offset, rx: Float, ry: Float, n: Float = 4f): Path {
    val p = Path()
    val steps = 72
    for (i in 0..steps) {
        val a = (i.toFloat() / steps) * 2f * PI.toFloat()
        val ca = cos(a); val sa = sin(a)
        val x = c.x + rx * sign(ca) * abs(ca).pow(2f / n)
        val y = c.y + ry * sign(sa) * abs(sa).pow(2f / n)
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

private fun DrawScope.drawFace(
    mood: BuddyMood, dizzy: Boolean, c: Offset, body: Float, t: Float,
    blink: Float, lookX: Float, lookY: Float, lvl: Float, listen: Float, zTick: Int
) {
    val eyeDX = body * 0.19f
    val eyeY = c.y - body * 0.07f
    val ink = Color(0xFF1B1530)
    val stroke = Stroke(width = body * 0.06f, cap = StrokeCap.Round)
    val left = Offset(c.x - eyeDX, eyeY)
    val right = Offset(c.x + eyeDX, eyeY)

    when {
        dizzy -> {
            // Спіральки, що крутяться
            for (e in listOf(left, right)) {
                rotate(t * 400f, pivot = e) {
                    val sp = Path()
                    for (i in 0..40) {
                        val a = i / 40f * 4f * PI.toFloat()
                        val r = body * 0.012f + body * 0.09f * i / 40f
                        val x = e.x + r * cos(a); val y = e.y + r * sin(a)
                        if (i == 0) sp.moveTo(x, y) else sp.lineTo(x, y)
                    }
                    drawPath(sp, ink, style = Stroke(width = body * 0.035f, cap = StrokeCap.Round))
                }
            }
            drawMouth(c, body, open = 0.4f, ink = ink, wavy = true, t = t)
        }
        mood == BuddyMood.SLEEP -> {
            // Закриті очі-дужки
            for (e in listOf(left, right)) {
                drawArc(ink, 20f, 140f, false, Offset(e.x - body * 0.08f, e.y - body * 0.07f), Size(body * 0.16f, body * 0.1f), style = stroke)
            }
            // «z z» над головою
            for (i in 0..2) {
                val phase = (zTick + i) % 3
                val alpha = 1f - phase / 3f
                val zs2 = body * (0.09f + 0.03f * phase)
                drawZ(Offset(c.x + body * (0.26f + 0.08f * phase), c.y - body * (0.4f + 0.1f * phase)), zs2, Color.White.copy(alpha = alpha * 0.9f))
            }
        }
        mood == BuddyMood.HAPPY -> {
            // ^ ^
            for (e in listOf(left, right)) {
                drawArc(ink, 200f, 140f, false, Offset(e.x - body * 0.08f, e.y - body * 0.02f), Size(body * 0.16f, body * 0.12f), style = stroke)
            }
            drawMouth(c, body, open = 0.9f, ink = ink, wavy = false, t = t)
        }
        mood == BuddyMood.ERROR -> {
            for (e in listOf(left, right)) {
                val d = body * 0.06f
                drawLine(ink, Offset(e.x - d, e.y - d), Offset(e.x + d, e.y + d), body * 0.055f, StrokeCap.Round)
                drawLine(ink, Offset(e.x + d, e.y - d), Offset(e.x - d, e.y + d), body * 0.055f, StrokeCap.Round)
            }
            drawMouth(c, body, open = 0.25f, ink = ink, wavy = true, t = t)
        }
        else -> {
            // Звичайні очі: білки + зіниці
            val grow = 1f + 0.12f * listen + 0.22f * lvl
            val ew = body * 0.17f * grow
            val eh = body * 0.24f * grow * blink.coerceIn(0.05f, 1f)
            var lx = lookX
            var ly = lookY
            if (mood == BuddyMood.THINK) { lx = cos(t * 2.4f) * 0.8f; ly = -0.7f + sin(t * 2.4f) * 0.2f }
            for (e in listOf(left, right)) {
                drawOval(Color.White, Offset(e.x - ew / 2f, e.y - eh / 2f), Size(ew, eh))
                if (blink > 0.3f) {
                    val pr = ew * 0.36f
                    val px = e.x + lx * (ew / 2f - pr) * 0.9f
                    val py = e.y + ly * (eh / 2f - pr) * 0.9f
                    drawCircle(ink, radius = pr, center = Offset(px, py))
                    drawCircle(Color.White, radius = pr * 0.35f, center = Offset(px - pr * 0.3f, py - pr * 0.35f))
                }
            }
            when (mood) {
                BuddyMood.SPEAK -> drawMouth(c, body, open = 0.25f + 0.75f * abs(sin(t * 9f)), ink = ink, wavy = false, t = t)
                BuddyMood.LISTEN -> drawMouth(c, body, open = 0.15f + lvl * 0.3f, ink = ink, wavy = false, t = t)
                BuddyMood.THINK -> drawLine(ink, Offset(c.x - body * 0.05f, c.y + body * 0.2f), Offset(c.x + body * 0.07f, c.y + body * 0.18f), body * 0.04f, StrokeCap.Round)
                else -> drawArc(ink, 20f, 140f, false, Offset(c.x - body * 0.07f, c.y + body * 0.11f), Size(body * 0.14f, body * 0.1f), style = Stroke(body * 0.04f, cap = StrokeCap.Round))
            }
        }
    }
}

private fun DrawScope.drawMouth(c: Offset, body: Float, open: Float, ink: Color, wavy: Boolean, t: Float) {
    val mw = body * 0.16f
    val mh = body * 0.13f * open.coerceIn(0.1f, 1f)
    val top = c.y + body * 0.15f
    if (wavy) {
        val p = Path()
        for (i in 0..20) {
            val x = c.x - mw / 2f + mw * i / 20f
            val y = top + mh / 2f + sin(i / 20f * 2f * PI.toFloat() * 1.5f + t * 6f) * body * 0.018f
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, ink, style = Stroke(body * 0.035f, cap = StrokeCap.Round))
    } else {
        drawOval(ink, Offset(c.x - mw / 2f, top), Size(mw, mh))
        if (mh > body * 0.06f) {
            drawOval(Color(0xFFFF6F8B), Offset(c.x - mw * 0.3f, top + mh * 0.5f), Size(mw * 0.6f, mh * 0.42f))
        }
    }
}

private fun DrawScope.drawZ(o: Offset, size: Float, color: Color) {
    val p = Path().apply {
        moveTo(o.x, o.y); lineTo(o.x + size, o.y); lineTo(o.x, o.y + size); lineTo(o.x + size, o.y + size)
    }
    drawPath(p, color, style = Stroke(width = size * 0.22f, cap = StrokeCap.Round))
}
