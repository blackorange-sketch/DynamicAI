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

/** Пружинне «вискакування» вікна/діалогу: масштаб 0.88 → 1 і поява. */
fun Modifier.popIn(): Modifier = composed {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val p by animateFloatAsState(
        if (go) 1f else 0f,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "popIn"
    )
    graphicsLayer {
        alpha = p.coerceIn(0f, 1f)
        val s = 0.88f + 0.12f * p
        scaleX = s; scaleY = s
        transformOrigin = TransformOrigin(0.5f, 0.6f)
    }
}
