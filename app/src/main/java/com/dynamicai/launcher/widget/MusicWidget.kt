package com.dynamicai.launcher.widget

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MusicWidget(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var isPlaying by remember { mutableStateOf(audioManager.isMusicActive) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x11FFFFFF)))
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF9C27B0).copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text("🎵", fontSize = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Музика", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (isPlaying) "Відтворюється" else "На паузі",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                // Попередній трек
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).clickable {
                        sendMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⏮", color = Color.White, fontSize = 20.sp)
                }
                // Play / Pause
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable {
                            val code = if (isPlaying) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY
                            sendMediaKey(audioManager, code)
                            isPlaying = !isPlaying
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isPlaying) "⏸" else "▶️", color = Color.White, fontSize = 22.sp)
                }
                // Наступний трек
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).clickable {
                        sendMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_NEXT)
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⏭", color = Color.White, fontSize = 20.sp)
                }
            }
        }
    }
}

private fun sendMediaKey(audioManager: AudioManager, keyCode: Int) {
    audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
}
