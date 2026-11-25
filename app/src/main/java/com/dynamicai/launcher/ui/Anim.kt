package com.dynamicai.launcher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.CubicBezierEasing

/** Єдині криві руху: швидкий старт із м'яким гальмуванням для появи, розгін для зникнення. */
object Motion {
    val Decel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Accel = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

/** Анімація появи головного екрана програється один раз за запуск процесу. */
object HomeAnim { @Volatile var played = false }

/** Віджети виїжджають знизу з пружиною по черзі; після першого показу — без анімації. */
fun Modifier.appearStagger(index: Int): Modifier = composed {
    val already = remember { HomeAnim.played }
    var go by remember { mutableStateOf(already) }
    LaunchedEffect(Unit) { if (!go) { delay(40L + index * 70L); go = true } }
    val p by animateFloatAsState(
        if (go) 1f else 0f,
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow),
        label = "appear"
    )
    graphicsLayer {
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 36.dp.toPx()
    }
}

/** Пружинне «вискакування» вікна/меню: масштаб 0.88 → 1 і поява; closing = true — швидко стискається й гасне. */
fun Modifier.popIn(closing: Boolean = false, origin: TransformOrigin = TransformOrigin(0.5f, 0.6f)): Modifier = composed {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val p by animateFloatAsState(
        if (go && !closing) 1f else 0f,
        if (closing) androidx.compose.animation.core.tween(150, easing = Motion.Accel)
        else spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "popIn"
    )
    graphicsLayer {
        alpha = p.coerceIn(0f, 1f)
        val s = 0.88f + 0.12f * p
        scaleX = s; scaleY = s
        transformOrigin = origin
    }
}

/** Закриття з анімацією: спершу [closing] = true (popIn програє зникнення), за 150 мс — onDismiss. */
@Composable
fun rememberAnimatedDismiss(onDismiss: () -> Unit): Pair<Boolean, () -> Unit> {
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(onDismiss)
    return closing to {
        if (!closing) {
            closing = true
            scope.launch { delay(150); current() }
        }
    }
}
