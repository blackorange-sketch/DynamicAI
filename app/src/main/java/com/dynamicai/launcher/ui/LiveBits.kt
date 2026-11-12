package com.dynamicai.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.dynamicai.launcher.ui.theme.AppTheme

/**
 * Текст, у якому кожен символ змінюється окремо: нова цифра виїжджає знизу, стара йде вгору.
 * Для годинника — змінюється лише та цифра, що справді інша.
 */
@Composable
fun RollingText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight? = null,
    modifier: Modifier = Modifier
) {
    // Цифри однакової ширини, щоб рядок не «дихав» при зміні
    val style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { i, ch ->
            key(i) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        (slideInVertically(tween(380, easing = Motion.Decel)) { it / 2 } + fadeIn(tween(260))) togetherWith
                            (slideOutVertically(tween(260, easing = Motion.Accel)) { -it / 2 } + fadeOut(tween(180)))
                    },
                    label = "rollingChar"
                ) { c ->
                    Text(c.toString(), color = color, fontSize = fontSize, fontWeight = fontWeight, style = style, maxLines = 1)
                }
            }
        }
    }
}

/** Індикатор сторінок: активна крапка витягується в риску й перетікає слідом за пальцем. */
@Composable
fun PageDots(pagerState: PagerState, pageCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val pos = pagerState.currentPage + pagerState.currentPageOffsetFraction
        for (p in 0 until pageCount) {
            val sel = (1f - kotlin.math.abs(pos - p)).coerceIn(0f, 1f)
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .height(6.dp)
                    .width((6f + 14f * sel).dp)
                    .clip(CircleShape)
                    .background(AppTheme.colors.textPrimary.copy(alpha = 0.35f + 0.65f * sel))
            )
        }
    }
}

/** Частка заповнення смужки, що плавно доходить до нового значення (ресурси, заряд). */
@Composable
fun animatedFraction(target: Float): Float {
    val v by animateFloatAsState(target.coerceIn(0f, 1f), tween(700, easing = Motion.Decel), label = "barFraction")
    return v
}
