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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.dynamicai.launcher.VoiceState
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import kotlin.math.sin

fun findBone(node: Node, names: List<String>): Node? {
    val nodeName = node.name ?: ""
    if (names.any { nodeName.contains(it, ignoreCase = true) }) return node
    for (child in node.childNodes) {
        val found = findBone(child, names)
        if (found != null) return found
    }
    return null
}

@Composable
fun Anime3DAvatar(voiceState: VoiceState, modifier: Modifier = Modifier) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val childNodes = rememberNodes()
    
    var errorText by remember { mutableStateOf<String?>(null) }

    val modelNode = remember {
        try {
            val instance = modelLoader.createModelInstance("anime_girl.glb")
            val node = ModelNode(modelInstance = instance, scaleToUnits = 2.0f)
            
            // Розвертаємо її на 180 градусів, щоб дивилася на нас
            node.rotation = Rotation(y = 180f)
            // Опускаємо трохи вниз, щоб вона "стояла" на краю екрану
            node.position = Position(0f, -1.5f, 0f)
            
            // Опускаємо руки (якщо знайдемо)
            val leftArm = findBone(node, listOf("L_UpperArm", "UpperArm_L"))
            val rightArm = findBone(node, listOf("R_UpperArm", "UpperArm_R"))
            leftArm?.rotation = Rotation(z = -65f)
            rightArm?.rotation = Rotation(z = 65f)
            
            node
        } catch (e: Exception) {
            errorText = "Помилка: " + e.javaClass.simpleName + " - " + e.message
            null
        }
    }

    if (modelNode != null) {
        LaunchedEffect(modelNode, voiceState) {
            childNodes.add(modelNode)
            
            val head = findBone(modelNode, listOf("C_Head", "Head"))
            val chest = findBone(modelNode, listOf("C_Chest", "Chest", "C_Spine", "Spine"))
            val hips = findBone(modelNode, listOf("C_Hips", "Hips"))

            var time = 0f
            while (true) {
                withFrameNanos { _ ->
                    val speed = when(voiceState) {
                        VoiceState.SPEAKING -> 2f
                        VoiceState.THINKING -> 1f
                        else -> 1f
                    }
                    time += 0.016f * speed
                    
                    val breath = sin(time * 2f) * 2.5f 
                    
                    when (voiceState) {
                        VoiceState.SPEAKING -> {
                            // Активно киває і трохи рухає плечима
                            chest?.rotation = Rotation(x = breath * 1.5f)
                            head?.rotation = Rotation(
                                x = sin(time * 6f) * 4f, 
                                y = sin(time * 3f) * 3f,
                                z = sin(time * 4f) * 2f
                            )
                        }
                        VoiceState.THINKING -> {
                            // Нахиляє голову вбік і трохи вгору
                            chest?.rotation = Rotation(x = breath * 0.5f)
                            head?.rotation = Rotation(
                                x = -8f + sin(time * 1f) * 2f, 
                                z = 15f 
                            )
                        }
                        VoiceState.LISTENING -> {
                            // Уважно слухає, легкий нахил
                            chest?.rotation = Rotation(x = breath)
                            head?.rotation = Rotation(
                                x = sin(time * 1.5f) * 2f,
                                y = sin(time * 1f) * 3f,
                                z = 5f
                            )
                        }
                        else -> {
                            // Спокій
                            chest?.rotation = Rotation(x = breath)
                            head?.rotation = Rotation(y = sin(time * 0.8f) * 4f)
                        }
                    }
                    
                    hips?.rotation = Rotation(z = sin(time * 1.5f) * 1.5f)
                }
            }
        }

        Scene(
            modifier = modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            childNodes = childNodes,
            cameraNode = rememberCameraNode(engine) {
                // Камера дивиться на рівень обличчя
                position = Position(0f, 1.2f, 3.0f)
            }
        )
    } else {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(errorText ?: "Завантаження...", color = Color.Red)
        }
    }
}
