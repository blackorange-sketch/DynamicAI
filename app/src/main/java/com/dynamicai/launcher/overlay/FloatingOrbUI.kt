package com.dynamicai.launcher.overlay

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FloatingOrb(
    isExpanded: Boolean,
    messages: List<Pair<String, String>>,
    isThinking: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onDrag: (Float, Float) -> Unit,
    onSendMessage: (String) -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.9f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )

    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // Модифікатор для перетягування
    val dragModifier = Modifier.pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
            change.consume()
            onDrag(dragAmount.x, dragAmount.y)
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (!isExpanded) {
            // Маленька куля (Зона натискання 64dp, візуал 48dp)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .then(dragModifier)
                    .clickable { onExpandChange(true) },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(48.dp)) {
                    val center = this.center
                    val baseRadius = size.minDimension * 0.4f
                    val primaryColor = Color(0xFF00E676)

                    // Дуже делікатне світіння (ледь помітне)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.15f), Color.Transparent),
                            center = center, radius = baseRadius * 1.5f
                        ),
                        radius = baseRadius * 1.5f, center = center
                    )
                    // Ядро
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.9f), primaryColor.copy(alpha = 0.6f), Color.Transparent),
                            center = Offset(center.x - baseRadius * 0.2f, center.y - baseRadius * 0.2f), radius = baseRadius * 0.7f * pulse
                        ),
                        radius = baseRadius * 0.7f * pulse, center = center
                    )
                    // Тонке кільце
                    drawCircle(color = primaryColor.copy(alpha = 0.5f), radius = baseRadius * 1.1f, center = center, style = Stroke(width = 1.dp.toPx()))
                }
            }
        } else {
            // Розгорнутий міні-чат
            Box(
                modifier = Modifier
                    .size(width = 320.dp, height = 420.dp)
                    .clip(RoundedCornerShape(24.dp))
                    // ПРОЗОРИЙ ФОН (Liquid Glass)
                    .background(Color(0xE60A0A0A))
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    // Header (за нього можна тягнути)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(dragModifier) // Додаємо перетягування для шапки
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (isThinking) Color(0xFF2196F3) else Color(0xFF4CAF50)))
                            Spacer(Modifier.width(8.dp))
                            Text("JARVIS", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("✕", color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp, modifier = Modifier.clickable(indication = LocalIndication.current, interactionSource = remember { MutableInteractionSource() }) { onExpandChange(false) })
                    }

                    // Повідомлення
                    LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(messages) { (role, text) ->
                            val isUser = role == "user"
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 240.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isUser) Color(0xFF2196F3).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f))
                                        .padding(10.dp)
                                ) {
                                    Text(text, color = Color.White, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    // Індикатор "Думаю"
                    if (isThinking) {
                        Text("🔍 Думаю...", color = Color(0xFF64B5F6), fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                    }

                    Spacer(Modifier.height(8.dp))

                    // Поле вводу
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            cursorBrush = SolidColor(Color.White),
                            decorationBox = { inner ->
                                if (query.isEmpty()) Text("Запитай...", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
                                inner()
                            }
                        )
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF64B5F6).copy(alpha = 0.3f))
                                .clickable(
                                    indication = LocalIndication.current,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    if (query.isNotBlank()) {
                                        onSendMessage(query)
                                        query = ""
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) { Text("➤", color = Color(0xFF64B5F6), fontSize = 16.sp) }
                        
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50).copy(alpha = 0.3f))
                                .clickable(
                                    indication = LocalIndication.current,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { onMicClick() },
                            contentAlignment = Alignment.Center
                        ) { Text("🎤", fontSize = 16.sp) }
                    }
                }
            }
        }
    }
}
