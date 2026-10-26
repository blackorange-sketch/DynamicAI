package com.dynamicai.launcher.voice

import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.dynamicai.launcher.VoiceState
import org.godotengine.godot.GodotFragment

@Composable
fun GodotAvatar(voiceState: VoiceState, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            // Створюємо контейнер для фрагмента
            val container = FragmentContainerView(context).apply {
                id = View.generateViewId()
            }
            
            // Додаємо GodotFragment у контейнер
            val activity = context as? FragmentActivity
            activity?.supportFragmentManager?.beginTransaction()?.apply {
                replace(container.id, GodotFragment())
                commit()
            }
            
            container
        }
    )
}
