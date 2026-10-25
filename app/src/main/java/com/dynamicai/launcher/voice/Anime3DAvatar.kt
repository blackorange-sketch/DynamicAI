package com.dynamicai.launcher.voice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.node.ModelNode

@Composable
fun Anime3DAvatar(modifier: Modifier = Modifier) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val childNodes = rememberNodes()
    
    var errorText by remember { mutableStateOf<String?>(null) }

    val modelNode = remember {
        try {
            val instance = modelLoader.createModelInstance("anime_girl.glb")
            
            // Логуємо всі доступні анімації
            val anims = instance.animations.map { it.name }
            android.util.Log.d("Anime3D", "Available animations: $anims")

            val node = ModelNode(
                modelInstance = instance,
                scaleToUnits = 1.2f // Трохи зменшимо, щоб влазила в екран
            )
            
            // Намагаємось запустити анімацію (Idle, Stand або першу-ліпшу)
            val animToPlay = anims.find { it.contains("Idle", ignoreCase = true) || it.contains("Stand", ignoreCase = true) } ?: anims.firstOrNull()
            if (animToPlay != null) {
                node.animationName = animToPlay
                node.isAnimating = true
                android.util.Log.d("Anime3D", "Playing animation: $animToPlay")
            }
            
            node
        } catch (e: Exception) {
            errorText = "Помилка: ${e.javaClass.simpleName} - ${e.message}"
            android.util.Log.e("Anime3D", "Load failed", e)
            null
        }
    }

    if (modelNode != null) {
        LaunchedEffect(modelNode) {
            childNodes.add(modelNode)
        }

        Scene(
            modifier = modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            childNodes = childNodes,
            cameraNode = rememberCameraNode(engine) {
                // Ставимо камеру навпроти обличчя (приблизно 1.4 метра висоти)
                position = Position(0f, 1.4f, 3.5f)
            }
        )
    } else {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(errorText ?: "Завантаження...", color = Color.Red)
        }
    }
}
