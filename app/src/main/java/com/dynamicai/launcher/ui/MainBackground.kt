package com.dynamicai.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dynamicai.launcher.data.LauncherSettings
import com.dynamicai.launcher.ui.theme.AppTheme

@Composable
fun MainBackground(settings: LauncherSettings) {
    val ctx = LocalContext.current

    // 1. Пріоритет: вибране фото
    var customBg by remember(settings.backgroundImageUri) {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(settings.backgroundImageUri) {
        customBg = if (settings.backgroundImageUri.isNotBlank()) {
            CustomBackgroundLoader.loadFromFile(settings.backgroundImageUri, 720)
        } else null
    }

    // 2. Fallback: системні шпалери (якщо доступні)
    val systemWallpaper = remember {
        BackdropBlur.get(ctx, 720).also {
            if (it == null) android.util.Log.w("MainBg", "systemWallpaper NULL")
            else android.util.Log.d("MainBg", "systemWallpaper: ${it.width}x${it.height}")
        }
    }

    val bg = customBg ?: systemWallpaper

    Box(modifier = Modifier.fillMaxSize()) {
        if (bg != null) {
            Image(
                bitmap = bg,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(
                        radius = settings.wallpaperBlurRadius.dp,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded
                    )
            )
        } else {
            // 3. Останній fallback: градієнт
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                AppTheme.colors.backdropStart,
                                AppTheme.colors.backdropEnd
                            )
                        )
                    )
            )
        }
    }
}
