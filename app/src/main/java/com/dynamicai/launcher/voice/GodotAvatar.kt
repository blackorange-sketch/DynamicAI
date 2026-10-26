package com.dynamicai.launcher.voice

import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.dynamicai.launcher.VoiceState
import org.godotengine.godot.GodotFragment

@Composable
fun GodotAvatar(voiceState: VoiceState, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                // Застосовуємо збережені налаштування
                scaleX = AvatarSettings.scale
                scaleY = AvatarSettings.scale
                translationX = AvatarSettings.offsetX
                translationY = AvatarSettings.offsetY
                rotationY = AvatarSettings.rotationY
            },
        factory = { context ->
            val container = FragmentContainerView(context).apply {
                id = View.generateViewId()
            }
            
            val activity = context as? FragmentActivity
            activity?.supportFragmentManager?.beginTransaction()?.apply {
                replace(container.id, GodotFragment())
                commit()
            }
            
            container
        }
    )
}
