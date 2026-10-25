package com.dynamicai.launcher.voice

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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

    // Завантажуємо модель з assets
    val modelNode = remember {
        try {
            ModelNode(
                modelInstance = modelLoader.createModelInstance("file:///android_asset/anime_girl.vrm"),
                scaleToUnits = 1.5f // Масштаб моделі
            )
        } catch (e: Exception) {
            android.util.Log.e("Anime3D", "Failed to load model", e)
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
                position = Position(0f, 1.2f, 2.5f) // Камера на рівні обличчя
            }
        )
    } else {
        // Якщо модель не завантажилась, показуємо текст
        androidx.compose.material3.Text("Помилка завантаження 3D-моделі", color = androidx.compose.ui.graphics.Color.Red)
    }
}
