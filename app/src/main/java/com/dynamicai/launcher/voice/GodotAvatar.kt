package com.dynamicai.launcher.voice

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.scale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.dynamicai.launcher.VoiceState
import org.godotengine.godot.GodotFragment

@Composable
fun GodotAvatar(
    voiceState: VoiceState, 
    scale: Float, 
    offsetY: Float, 
    offsetX: Float, 
    rotationY: Float, 
    modifier: Modifier = Modifier
) {
    // Використовуємо безпечні Modifier.scale та Modifier.offset замість graphicsLayer
    Box(
        modifier = modifier
            .fillMaxSize()
            .scale(scale)
            .offset(x = offsetX.dp, y = offsetY.dp)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val container = FragmentContainerView(context).apply {
                    id = View.generateViewId()
                }
                
                val activity = context as? FragmentActivity
                activity?.supportFragmentManager?.beginTransaction()?.apply {
                    // Додаємо перевірку, щоб не створювати дублікат фрагмента
                    if (activity.supportFragmentManager.findFragmentById(container.id) == null) {
                        val fragment = GodotFragment().apply {
                    arguments = Bundle().apply {
                        putString("main_pack", "res://godot/DynamicAIAvatar.pck")
                    }
                }
                replace(container.id, fragment)
                        commit()
                    }
                }
                
                container
            }
        )
    }
}
