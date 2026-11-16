package com.dynamicai.launcher.overlay

import com.dynamicai.launcher.ui.theme.AppTheme
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
    modifier: Modifier = Modifier,
    mood: BuddyMood = if (isThinking) BuddyMood.THINK else BuddyMood.IDLE,
    micLevel: () -> Float = { 0f },
    island: Boolean = false,
    hint: String? = null
) {
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
            if (island) {
                // Острівець угорі біля камери
                OrbIsland(
                    mood = mood, text = hint, micLevel = micLevel,
                    onTap = { onExpandChange(true) }, onLongPress = onMicClick
                )
            } else {
                // Персонаж (зона дотику трохи більша за малюнок)
                Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    OrbBuddy(
                        mood = mood, size = 64.dp, micLevel = micLevel,
                        onTap = { onExpandChange(true) }, onLongPress = onMicClick,
                        onDrag = onDrag
                    )
                }
            }
        } else {
            // Розгорнутий міні-чат
            Box(
                modifier = Modifier
                    .size(width = 320.dp, height = 420.dp)
                    .clip(RoundedCornerShape(24.dp))
                    // ПРОЗОРИЙ ФОН (Liquid Glass)
                    .background(AppTheme.colors.glassBackground.copy(alpha = 0.9f))
                    .border(0.5.dp, AppTheme.colors.glassBorder, RoundedCornerShape(24.dp))
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
                            OrbBuddy(mood = mood, size = 30.dp, micLevel = micLevel, onLongPress = onMicClick)
                            Spacer(Modifier.width(6.dp))
                            Text("JARVIS", color = AppTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("✕", color = AppTheme.colors.textSecondary, fontSize = 18.sp, modifier = Modifier.clickable(indication = LocalIndication.current, interactionSource = remember { MutableInteractionSource() }) { onExpandChange(false) })
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
                                        .background(if (isUser) AppTheme.colors.accentDark.copy(alpha = 0.4f) else AppTheme.colors.glassBorder.copy(alpha = 0.7f))
                                        .padding(10.dp)
                                ) {
                                    Text(text, color = AppTheme.colors.textPrimary, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    // Індикатор "Думаю"
                    if (isThinking) {
                        Text("🔍 Думаю...", color = AppTheme.colors.accent, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                    }

                    Spacer(Modifier.height(8.dp))

                    // Поле вводу
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(AppTheme.colors.glassBorder.copy(alpha = 0.7f))
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            textStyle = TextStyle(color = AppTheme.colors.textPrimary, fontSize = 14.sp),
                            cursorBrush = SolidColor(AppTheme.colors.textPrimary),
                            decorationBox = { inner ->
                                if (query.isEmpty()) Text("Запитай...", color = AppTheme.colors.textSecondary, fontSize = 14.sp)
                                inner()
                            }
                        )
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AppTheme.colors.accent.copy(alpha = 0.3f))
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
                        ) { Text("➤", color = AppTheme.colors.accent, fontSize = 16.sp) }
                        
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AppTheme.colors.success.copy(alpha = 0.3f))
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
