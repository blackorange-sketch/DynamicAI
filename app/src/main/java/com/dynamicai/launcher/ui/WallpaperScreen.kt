package com.dynamicai.launcher.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.wallpaper.VideoWallpaperService
import com.dynamicai.launcher.wallpaper.WallpaperUtils
import kotlinx.coroutines.launch

@Composable
fun WallpaperScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var muted by remember { mutableStateOf(VideoWallpaperService.getMuted(ctx)) }
    var hasVideo by remember { mutableStateOf(VideoWallpaperService.hasVideo(ctx)) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = WallpaperUtils.setStaticWallpaper(ctx, uri)
                if (res.isSuccess) {
                    Toast.makeText(ctx, "Шпалери встановлено", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(ctx, "Помилка: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            VideoWallpaperService.setVideo(ctx, uri)
            hasVideo = true
            Toast.makeText(ctx, "Відео обрано — встанови як live wallpaper", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White)
            }
            Text("Шпалери", color = Color.White, fontSize = 22.sp)
        }

        SectionHeader("Статичні шпалери")
        ActionButton("Обрати з галереї") {
            imagePicker.launch("image/*")
        }
        Hint("Встановить зображення як шпалери системи")

        SectionHeader("Відео-шпалери")
        ActionButton(if (hasVideo) "Змінити відео" else "Обрати відео") {
            videoPicker.launch("video/*")
        }

        if (hasVideo) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Без звуку", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = muted,
                    onCheckedChange = { m ->
                        muted = m
                        VideoWallpaperService.setMuted(ctx, m)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF64B5F6))
                )
            }
            ActionButton("Активувати відео-шпалери (система)") {
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(ctx, VideoWallpaperService::class.java)
                    )
                }
                try { ctx.startActivity(intent) } catch (_: Exception) {
                    try {
                        ctx.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
                    } catch (t: Exception) {
                        Toast.makeText(ctx, "Не підтримується: ${t.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            ActionButton("Прибрати відео") {
                VideoWallpaperService.clear(ctx)
                hasVideo = false
            }
        } else {
            Hint("Обери відео, потім активуй як live wallpaper через систему")
        }

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        color = Color(0xFF64B5F6),
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(horizontal = 24.dp, vertical = 14.dp)
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        color = Color.Gray,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
    )
}
