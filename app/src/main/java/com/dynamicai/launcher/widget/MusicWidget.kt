package com.dynamicai.launcher.widget

import android.content.Context
import android.content.Intent
import android.media.AudioManager
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.music.MusicService

@Composable
fun MusicWidget(size: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var isPlaying by remember { mutableStateOf(MusicService.isPlaying) }
    var title by remember { mutableStateOf(MusicService.currentTitle) }
    var artist by remember { mutableStateOf(MusicService.currentArtist) }
    val maxVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var volume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }

    LaunchedEffect(Unit) {
        while (true) {
            isPlaying = MusicService.isPlaying
            title = MusicService.currentTitle
            artist = MusicService.currentArtist
            kotlinx.coroutines.delay(500)
        }
    }

    val glass = Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x11FFFFFF)))
    Box(modifier = modifier.clip(RoundedCornerShape(24.dp)).background(glass).border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))) {
        when (size) {
            "small" -> Row(Modifier.fillMaxSize().padding(8.dp), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                PlayBtn("⏮") { ensureService(context)?.previous() }
                PlayBtn(if (isPlaying) "⏸" else "▶️", true) { ensureService(context)?.togglePlayPause() }
                PlayBtn("⏭") { ensureService(context)?.next() }
            }
            "large", "full" -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x449C27B0)), contentAlignment = Alignment.Center) { Text("🎵", fontSize = 28.sp) }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(if (title.isNotBlank()) title else "Музика", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (artist.isNotBlank()) artist else "Скажіть: «Включи музику»", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Гучність", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                Slider(value = volume, onValueChange = { volume = it; audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0) }, valueRange = 0f..maxVol.toFloat(), colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF64B5F6), inactiveTrackColor = Color.White.copy(alpha = 0.2f)))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.Center, Alignment.CenterVertically) {
                    PlayBtn("⏮") { ensureService(context)?.previous() }
                    Spacer(Modifier.width(16.dp))
                    PlayBtn(if (isPlaying) "⏸" else "▶️", true) { ensureService(context)?.togglePlayPause() }
                    Spacer(Modifier.width(16.dp))
                    PlayBtn("⏭") { ensureService(context)?.next() }
                }
                if (size == "full") {
                    Spacer(Modifier.height(16.dp))
                    Text("Скажіть: «Включи Океан Ельзи» — і трек заграє тут.", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
            else -> Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0x339C27B0)), contentAlignment = Alignment.Center) { Text("🎵", fontSize = 22.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (title.isNotBlank()) title else "Музика", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (artist.isNotBlank()) artist else if (isPlaying) "Відтворюється" else "На паузі", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 1)
                }
                PlayBtn("⏮") { ensureService(context)?.previous() }
                PlayBtn(if (isPlaying) "⏸" else "▶️", true) { ensureService(context)?.togglePlayPause() }
                PlayBtn("⏭") { ensureService(context)?.next() }
            }
        }
    }
}

@Composable
fun PlayBtn(icon: String, isMain: Boolean = false, onClick: () -> Unit) {
    val sz = if (isMain) 48.dp else 36.dp
    val bg = if (isMain) Color.White.copy(alpha = 0.15f) else Color.Transparent
    Box(Modifier.size(sz).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(icon, color = Color.White, fontSize = if (isMain) 22.sp else 18.sp)
    }
}

private fun ensureService(ctx: Context): MusicService? {
    if (MusicService.instance == null) {
        try { ctx.startService(Intent(ctx, MusicService::class.java)) } catch (_: Throwable) {}
    }
    return MusicService.instance
}
