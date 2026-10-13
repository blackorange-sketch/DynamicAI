package com.dynamicai.launcher.ui

import android.app.WallpaperManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast

@Composable
fun WallpaperScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current

    fun openSystemWallpaper() {
        try {
            ctx.startActivity(Intent(Intent.ACTION_SET_WALLPAPER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (t: Throwable) {
            Toast.makeText(ctx, "Не вдалось відкрити: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun openLiveWallpaper() {
        try {
            ctx.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (t: Throwable) {
            Toast.makeText(ctx, "Не підтримується: ${t.message}", Toast.LENGTH_LONG).show()
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

        SectionHeader("Системні")
        ActionButton("Обрати шпалери (система)") { openSystemWallpaper() }
        Hint("Галерея, системні, живі шпалери, Google Wallpapers — усе через системний вибір")

        SectionHeader("Живі шпалери")
        ActionButton("Обрати live wallpaper") { openLiveWallpaper() }
        Hint("Якщо встановлено DynamicAI Live Wallpaper — обери його тут")

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
