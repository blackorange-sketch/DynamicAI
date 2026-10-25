package com.dynamicai.launcher.voice

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.dynamicai.launcher.VoiceState
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotView

@Composable
fun GodotAvatar(voiceState: VoiceState, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            GodotView(context).apply {
                // Ініціалізуємо Godot і вказуємо йому шлях до нашого .pck файлу
                val godot = Godot(context)
                godot.start(this, "--main-pack", "res://godot/DynamicAIAvatar.pck")
            }
        },
        update = { view ->
            // Тут ми можемо передавати voiceState у Godot
        }
    )
}
