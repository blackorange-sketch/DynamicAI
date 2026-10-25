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
import androidx.compose.ui.platform.LocalContext
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.node.ModelNode
import java.io.File

@Composable
fun Anime3DAvatar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val childNodes = rememberNodes()
    
    var errorText by remember { mutableStateOf<String?>(null) }

    // Копіюємо файл з assets у тимчасову пам'ять додатка (Filament не читає assets напряму)
    val localFile = remember {
        try {
            val file = File(context.cacheDir, "anime_girl.glb")
            if (!file.exists() || file.length() == 0L) {
                context.assets.open("anime_girl.glb").use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            file
        } catch (e: Exception) {
            errorText = "Помилка копіювання файлу: \${e.message}"
            null
        }
    }

    val modelNode = remember(localFile) {
        if (localFile == null) return@remember null
        try {
            ModelNode(
                modelInstance = modelLoader.createModelInstance(localFile.path),
                scaleToUnits = 1.5f
            )
        } catch (e: Exception) {
            errorText = "Помилка завантаження моделі: \${e.javaClass.simpleName} - \${e.message}"
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
                position = Position(0f, 1.2f, 2.5f)
            }
        )
    } else {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(errorText ?: "Завантаження...", color = Color.Red)
        }
    }
}
