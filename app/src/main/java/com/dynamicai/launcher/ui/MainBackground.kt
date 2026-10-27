package com.dynamicai.launcher.ui

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dynamicai.launcher.data.LauncherSettings

@Composable
fun MainBackground(settings: LauncherSettings) {
    val ctx = LocalContext.current

    var customBg by remember(settings.backgroundImageUri) {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(settings.backgroundImageUri) {
        customBg = if (settings.backgroundImageUri.isNotBlank()) {
            CustomBackgroundLoader.loadFromFile(settings.backgroundImageUri, 720)
        } else null
    }

    // Малюємо ТІЛЬКИ якщо є кастомне фото.
    // Якщо немає — нічого не малюємо, і системні шпалери просвічують крізь прозорий Activity.
    if (customBg != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                bitmap = customBg!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(
                        radius = settings.wallpaperBlurRadius.dp,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded
                    )
            )
        }
    }
}
