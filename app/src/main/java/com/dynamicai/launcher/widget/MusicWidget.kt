package com.dynamicai.launcher.widget

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import com.dynamicai.launcher.ui.theme.AppTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MusicWidget(size: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var isPlaying by remember { mutableStateOf(audioManager.isMusicActive) }
    var trackTitle by remember { mutableStateOf("Музика") }
    var trackArtist by remember { mutableStateOf("Натисніть Play") }
    val scope = rememberCoroutineScope()

    // Періодично перевіряємо стан
    LaunchedEffect(Unit) {
        while (true) {
            isPlaying = audioManager.isMusicActive
            if (isPlaying && trackArtist == "Натисніть Play") {
                trackArtist = "Відтворюється"
            }
            delay(1000)
        }
    }

    // Стиль як у інших віджетів (Liquid Glass dark)
    Box(
        modifier = modifier.widgetCard()
    ) {
        when (size) {
            "small" -> SmallContent(audioManager, isPlaying, context, scope) { isPlaying = it }
            "large", "full" -> LargeContent(size, audioManager, isPlaying, context, scope, trackTitle, trackArtist) { isPlaying = it }
            else -> MediumContent(audioManager, isPlaying, context, scope, trackTitle, trackArtist) { isPlaying = it }
        }
    }
}

@Composable
fun SmallContent(
    audioManager: AudioManager, isPlaying: Boolean, context: Context,
    scope: kotlinx.coroutines.CoroutineScope, onPlayStateChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CtrlBtn("⏮") { sendMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        CtrlBtn(if (isPlaying) "⏸" else "▶", main = true) {
            handlePlayPause(context, audioManager, isPlaying, scope, onPlayStateChange)
        }
        CtrlBtn("⏭") { sendMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_NEXT) }
    }
}

@Composable
fun MediumContent(
    audioManager: AudioManager, isPlaying: Boolean, context: Context,
    scope: kotlinx.coroutines.CoroutineScope, title: String, artist: String,
    onPlayStateChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(AppTheme.colors.pink.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) { Text("🎵", fontSize = 22.sp) }

        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = AppTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, color = AppTheme.colors.textPrimary.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            CtrlBtn("⏮") { sendMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
            CtrlBtn(if (isPlaying) "⏸" else "▶", main = true) {
                handlePlayPause(context, audioManager, isPlaying, scope, onPlayStateChange)
            }
            CtrlBtn("⏭") { sendMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_NEXT) }
        }
    }
}

@Composable
fun LargeContent(
    size: String, audioManager: AudioManager, isPlaying: Boolean, context: Context,
    scope: kotlinx.coroutines.CoroutineScope, title: String, artist: String,
    onPlayStateChange: (Boolean) -> Unit
) {
    val maxVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var volume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(AppTheme.colors.pink.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) { Text("🎵", fontSize = 28.sp) }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = AppTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist, color = AppTheme.colors.textPrimary.copy(alpha = 0.5f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Spacer(Modifier.height(12.dp))

        Text("Гучність", color = AppTheme.colors.textPrimary.copy(alpha = 0.4f), fontSize = 10.sp)
        Slider(
            value = volume,
            onValueChange = {
                volume = it
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0)
            },
            valueRange = 0f..maxVol.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = AppTheme.colors.textPrimary,
                activeTrackColor = AppTheme.colors.accent,
                inactiveTrackColor = AppTheme.colors.textPrimary.copy(alpha = 0.15f)
            )
        )

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CtrlBtn("⏮") { sendMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
            Spacer(Modifier.width(20.dp))
            CtrlBtn(if (isPlaying) "⏸" else "▶", main = true) {
                handlePlayPause(context, audioManager, isPlaying, scope, onPlayStateChange)
            }
            Spacer(Modifier.width(20.dp))
            CtrlBtn("⏭") { sendMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_NEXT) }
        }
    }
}

@Composable
fun CtrlBtn(icon: String, main: Boolean = false, onClick: () -> Unit) {
    val sz = if (main) 46.dp else 34.dp
    val bg = if (main) AppTheme.colors.textPrimary.copy(alpha = 0.12f) else Color.Transparent
    Box(
        modifier = Modifier.size(sz).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(icon, color = AppTheme.colors.textPrimary, fontSize = if (main) 22.sp else 17.sp)
    }
}

private fun handlePlayPause(
    context: Context,
    audioManager: AudioManager,
    isPlaying: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    onStateChange: (Boolean) -> Unit
) {
    if (isPlaying) {
        // Пауза
        sendMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PAUSE)
        onStateChange(false)
    } else {
        // Пробуємо Play
        sendMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PLAY)
        onStateChange(true)

        // Якщо через 1.5 сек музика все ще не активна — значить, немає сесії, відкриваємо ReVanced
        scope.launch {
            delay(1500)
            if (!audioManager.isMusicActive) {
                openReVanced(context)
                onStateChange(false) // Скидаємо, бо це був просто запуск додатку
            }
        }
    }
}

private fun openReVanced(context: Context) {
    val packages = listOf(
        "com.google.android.apps.youtube.music",
        "anddea.youtube.music",
        "app.revanced.android.apps.youtube.music"
    )
    for (pkg in packages) {
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            }
        } catch (_: Exception) {}
    }
    // Fallback: пошук за назвою
    try {
        val apps = context.packageManager.getInstalledApplications(0)
        val ytm = apps.find { it.loadLabel(context.packageManager).toString().contains("YouTube Music", ignoreCase = true) }
        if (ytm != null) {
            val intent = context.packageManager.getLaunchIntentForPackage(ytm.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent?.let { context.startActivity(it) }
        }
    } catch (_: Exception) {}
}

private fun sendMediaKey(audioManager: AudioManager, keyCode: Int) {
    audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
}
