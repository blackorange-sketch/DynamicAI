package com.dynamicai.launcher.widget

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MusicWidget(size: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var isPlaying by remember { mutableStateOf(audioManager.isMusicActive) }
    
    // Стан гучності
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var currentVolume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }

    val glassBackground = Brush.verticalGradient(
        colors = listOf(Color(0x33FFFFFF), Color(0x11FFFFFF))
    )
    val glassBorder = Color.White.copy(alpha = 0.15f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(glassBackground)
            .border(0.5.dp, glassBorder, RoundedCornerShape(24.dp))
    ) {
        when (size) {
            "small" -> SmallMusicContent(context, isPlaying, audioManager) { isPlaying = it }
            "large", "full" -> LargeMusicContent(context, size, isPlaying, audioManager, currentVolume, maxVolume) { 
                isPlaying = it 
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume.toInt(), 0)
            }
            else -> MediumMusicContent(context, isPlaying, audioManager) { isPlaying = it }
        }
    }
}

@Composable
fun SmallMusicContent(context: Context, isPlaying: Boolean, audioManager: AudioManager, onPlayStateChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayControlButton("⏮") { sendMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        PlayControlButton(if (isPlaying) "⏸" else "▶️", isMain = true) {
            handlePlayPause(context, audioManager, isPlaying, onPlayStateChange)
        }
        PlayControlButton("⏭") { sendMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_NEXT) }
    }
}

@Composable
fun MediumMusicContent(context: Context, isPlaying: Boolean, audioManager: AudioManager, onPlayStateChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0x339C27B0)),
            contentAlignment = Alignment.Center
        ) { Text("🎵", fontSize = 22.sp) }
        
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Музика", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(if (isPlaying) "Відтворюється" else "На паузі", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayControlButton("⏮") { sendMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
            PlayControlButton(if (isPlaying) "⏸" else "▶️", isMain = true) {
                handlePlayPause(context, audioManager, isPlaying, onPlayStateChange)
            }
            PlayControlButton("⏭") { sendMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_NEXT) }
        }
    }
}

@Composable
fun LargeMusicContent(
    context: Context, size: String, isPlaying: Boolean, audioManager: AudioManager, volume: Float, maxVolume: Int, onPlayStateChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x449C27B0)),
                contentAlignment = Alignment.Center
            ) { Text("🎵", fontSize = 28.sp) }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("ReVanced Music", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Натисніть Play, щоб відкрити", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        Text("Гучність", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        Slider(
            value = volume,
            onValueChange = { onPlayStateChange(isPlaying) },
            valueRange = 0f..maxVolume.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFF64B5F6),
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
        
        Spacer(Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayControlButton("⏮") { sendMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
            Spacer(Modifier.width(16.dp))
            PlayControlButton(if (isPlaying) "⏸" else "▶️", isMain = true) {
                handlePlayPause(context, audioManager, isPlaying, onPlayStateChange)
            }
            Spacer(Modifier.width(16.dp))
            PlayControlButton("⏭") { sendMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_NEXT) }
        }
        
        if (size == "full") {
            Spacer(Modifier.height(16.dp))
            Text("Черга відтворення", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(5) { index ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${index + 1}", color = Color.White.copy(alpha = 0.3f), modifier = Modifier.width(24.dp))
                        Text("Трек у черзі...", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PlayControlButton(icon: String, isMain: Boolean = false, onClick: () -> Unit) {
    val size = if (isMain) 48.dp else 36.dp
    val bgColor = if (isMain) Color.White.copy(alpha = 0.15f) else Color.Transparent
    
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(bgColor).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(icon, color = Color.White, fontSize = if (isMain) 22.sp else 18.sp)
    }
}

private fun handlePlayPause(
    context: Context, 
    audioManager: AudioManager, 
    isPlaying: Boolean, 
    onStateChange: (Boolean) -> Unit
) {
    if (isPlaying) {
        // Музика грає - ставимо на паузу
        sendMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PAUSE)
        onStateChange(false)
    } else {
        // Музика не грає
        if (audioManager.isMusicActive) {
            // Активна сесія є - продовжуємо
            sendMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PLAY)
            onStateChange(true)
        } else {
            // Сесії немає - відкриваємо ReVanced
            val packageName = findReVancedPackage(context)
            if (packageName != null) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                }
            }
        }
    }
}

private fun findReVancedPackage(context: Context): String? {
    val possiblePackages = listOf(
        "com.google.android.apps.youtube.music",
        "anddea.youtube.music",
        "app.revanced.android.apps.youtube.music"
    )
    for (pkg in possiblePackages) {
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            return pkg
        } catch (_: Exception) {}
    }
    // Якщо не знайшли за відомими ID - шукаємо за назвою
    val apps = context.packageManager.getInstalledApplications(0)
    return apps.find { it.loadLabel(context.packageManager).toString().contains("YouTube Music", ignoreCase = true) }?.packageName
}

private fun sendMediaKey(audioManager: AudioManager, keyCode: Int) {
    audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
}
