package com.dynamicai.launcher.overlay

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun FloatingOrb(
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onDrag: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.9f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart), label = "rot"
    )

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .clickable { onExpandChange(!isExpanded) },
        contentAlignment = Alignment.Center
    ) {
        if (!isExpanded) {
            // Маленька куля 48dp
            Canvas(modifier = Modifier.size(48.dp)) {
                val center = this.center
                val baseRadius = size.minDimension * 0.4f
                val primaryColor = Color(0xFF00E676) // Колір для idle

                // Світіння
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.4f), Color.Transparent),
                        center = center, radius = baseRadius * 2f
                    ),
                    radius = baseRadius * 2f, center = center
                )
                // Ядро
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.9f), primaryColor.copy(alpha = 0.6f), Color.Transparent),
                        center = Offset(center.x - baseRadius * 0.2f, center.y - baseRadius * 0.2f),
                        radius = baseRadius * 0.7f * pulse
                    ),
                    radius = baseRadius * 0.7f * pulse, center = center
                )
                // Кільце
                drawCircle(color = primaryColor.copy(alpha = 0.6f), radius = baseRadius * 1.2f, center = center, style = Stroke(width = 2.dp.toPx()))
            }
        } else {
            // Міні-чат (Розширений стан)
            Box(
                modifier = Modifier
                    .size(width = 300.dp, height = 400.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xF0101010))
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text("JARVIS", color = Color.White, fontSize = 18.sp)
                    Spacer(Modifier.weight(1f))
                    Text("Тут буде чат...", color = Color.Gray, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Box(modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2196F3)), contentAlignment = Alignment.Center) {
                        Text("Слухаю...", color = Color.White)
                    }
                }
            }
        }
    }
}
