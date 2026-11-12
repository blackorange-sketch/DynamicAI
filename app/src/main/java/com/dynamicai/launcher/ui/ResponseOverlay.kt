package com.dynamicai.launcher.ui

import com.dynamicai.launcher.ui.theme.AppTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.tween

/**
 * Компактна нижня панель з відповіддю асистента.
 * Згорнута: до 4 рядків; тап по тексту розгортає (з прокруткою). «Копіювати» кладе відповідь у буфер,
 * ✕ або свайп вниз — закрити.
 */
@Composable
fun ResponseOverlay(
    text: String,
    visible: Boolean,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    var expanded by remember(text) { mutableStateOf(false) }
    var copied by remember(text) { mutableStateOf(false) }
    AnimatedVisibility(
        visible = visible && text.isNotBlank(),
        enter = slideInVertically(tween(animMs(340), easing = Motion.Decel)) { it / 2 } + fadeIn(tween(animMs(200))),
        exit = slideOutVertically(tween(animMs(200), easing = Motion.Accel)) { it / 2 } + fadeOut(tween(animMs(160))),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            AppTheme.colors.surface.copy(alpha = 0.96f),
                            AppTheme.colors.glassBackgroundAlt.copy(alpha = 0.92f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            AppTheme.colors.textPrimary.copy(alpha = 0.30f),
                            AppTheme.colors.textPrimary.copy(alpha = 0.06f)
                        )
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total > 60f) onDismiss() }
                    ) { _, dy -> total += dy }
                }
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // «ручка» панелі
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AppTheme.colors.textPrimary.copy(alpha = 0.25f))
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = text,
                color = AppTheme.colors.textPrimary,
                fontSize = 15.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (expanded) Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()) else Modifier)
                    .clickable { expanded = !expanded }
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    if (copied) "Скопійовано ✓" else "Копіювати",
                    color = AppTheme.colors.accent,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable {
                        try {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Відповідь", text))
                            copied = true
                        } catch (_: Throwable) {}
                    }.padding(vertical = 6.dp)
                )
                Text(
                    if (expanded) "Згорнути" else "Розгорнути",
                    color = AppTheme.colors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 6.dp)
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Закрити",
                    tint = AppTheme.colors.textSecondary,
                    modifier = Modifier.size(28.dp).clickable { onDismiss() }.padding(4.dp)
                )
            }
        }
    }
}
