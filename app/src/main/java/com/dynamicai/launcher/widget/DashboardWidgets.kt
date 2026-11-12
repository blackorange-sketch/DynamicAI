package com.dynamicai.launcher.widget

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.suggestion.TelegramIndex
import com.dynamicai.launcher.ui.theme.AppTheme
import kotlinx.coroutines.delay
import com.dynamicai.launcher.ui.LaunchedWhileStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun startSafe(ctx: Context, i: Intent) {
    try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
}

private var torchOn = false

private fun toggleTorch(ctx: Context) {
    try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull() ?: return
        torchOn = !torchOn
        cm.setTorchMode(id, torchOn)
    } catch (_: Throwable) { torchOn = false }
}

/** Недавні чати Telegram з локального індексу; тап відкриває чат. */
@Composable
fun TgChatsWidget(size: String, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val rows = when (size) { "small" -> 3; "large" -> 8; "full" -> 12; else -> 5 }
    var chats by remember { mutableStateOf(TelegramIndex.recent(ctx, rows)) }
    // Оновлюємо лише поки лаунчер на екрані, і читаємо індекс поза головним потоком
    LaunchedWhileStarted(rows) {
        while (true) {
            chats = withContext(Dispatchers.Default) { TelegramIndex.recent(ctx, rows) }
            delay(5000)
        }
    }
    Column(
        modifier = modifier.widgetCard()
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }
            .padding(12.dp)
    ) {
        Text("✈️ Чати Telegram", color = AppTheme.colors.textSecondary, fontSize = 12.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        if (chats.isEmpty()) {
            Text("Чати з'являться, коли надійдуть сповіщення Telegram", color = AppTheme.colors.textSecondary,
                fontSize = 12.sp, modifier = Modifier.padding(4.dp))
        }
        chats.forEach { c ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .pointerInput(c.name) {
                        detectTapGestures(onTap = { TelegramIndex.open(ctx, c.pkg, c.shortcutId) }, onLongPress = { onLongPress() })
                    }
                    .padding(horizontal = 6.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(AppTheme.colors.accent.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(c.name.take(1).uppercase(), color = AppTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Text(c.name, color = AppTheme.colors.textPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private data class QuickAction(val icon: String, val label: String, val run: (Context) -> Unit)

private val quickActions = listOf(
    QuickAction("🔦", "Ліхтарик") { toggleTorch(it) },
    QuickAction("📶", "Wi-Fi") { startSafe(it, Intent(Settings.ACTION_WIFI_SETTINGS)) },
    QuickAction("🔵", "Bluetooth") { startSafe(it, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
    QuickAction("📷", "Камера") { startSafe(it, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) },
    QuickAction("🔋", "Батарея") { startSafe(it, Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) },
    QuickAction("🔊", "Звук") { startSafe(it, Intent(Settings.ACTION_SOUND_SETTINGS)) },
    QuickAction("✈", "Режим польоту") { startSafe(it, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) },
    QuickAction("🌙", "Не турбувати") { startSafe(it, Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)) },
    QuickAction("⚙", "Налаштування") { startSafe(it, Intent(Settings.ACTION_SETTINGS)) }
)

/** Плитки швидких дій: ліхтарик та відкриття системних налаштувань. */
@Composable
fun QuickActionsWidget(size: String, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val cols = 3
    val rows = when (size) { "small" -> 1; "large" -> 3; "full" -> 3; else -> 2 }
    val shown = quickActions.take(rows * cols)
    Column(
        modifier = modifier.widgetCard()
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }
            .padding(12.dp)
    ) {
        Text("🔧 Швидкі дії", color = AppTheme.colors.textSecondary, fontSize = 12.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        shown.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { a ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(AppTheme.colors.textPrimary.copy(alpha = 0.08f))
                            .pointerInput(a.label) {
                                detectTapGestures(onTap = { a.run(ctx) }, onLongPress = { onLongPress() })
                            }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(a.icon, fontSize = 20.sp)
                        Text(a.label, color = AppTheme.colors.textPrimary, fontSize = 10.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
