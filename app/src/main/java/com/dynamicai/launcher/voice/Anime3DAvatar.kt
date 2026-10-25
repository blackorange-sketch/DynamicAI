package com.dynamicai.launcher.voice

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import com.dynamicai.launcher.VoiceState
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Transform
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
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

    // Додаємо джерело світла для природних кольорів
    val lightNode = remember {
        LightNode(engine = engine).apply {
            type = io.github.sceneview.node.LightNode.Type.DIRECTIONAL
            intensity = 100_000f
            transform = Transform(
                position = Position(2f, 3f, 3f),
                rotation = Rotation(x = -45f, y = 30f)
            )
        }
    }

    val modelNode = remember {
        try {
            val instance = modelLoader.createModelInstance("anime_girl.glb")
            val node = ModelNode(modelInstance = instance, scaleToUnits = 2.0f)
            
            // Поворот (якщо вона дивиться спиною, зміни y = 180f на y = 0f)
            node.rotation = Rotation(y = 180f)
            node.position = Position(0f, -1.5f, 0f)
            
            node
        } catch (e: Exception) {
            errorText = "Помилка: " + e.javaClass.simpleName + " - " + e.message
            null
        }
    }

    if (modelNode != null) {
        LaunchedEffect(modelNode, voiceState) {
            childNodes.add(lightNode)
            childNodes.add(modelNode)
            
            val head = findBone(modelNode, listOf("C_Head", "Head"))
            val chest = findBone(modelNode, listOf("C_Chest", "Chest", "C_Spine", "Spine"))
            val hips = findBone(modelNode, listOf("C_Hips", "Hips"))
            val leftArm = findBone(modelNode, listOf("L_UpperArm", "UpperArm_L"))
            val rightArm = findBone(modelNode, listOf("R_UpperArm", "UpperArm_R"))
            val leftElbow = findBone(modelNode, listOf("L_LowerArm", "LowerArm_L"))
            val rightElbow = findBone(modelNode, listOf("R_LowerArm", "LowerArm_R"))

            var time = 0f
            while (true) {
                withFrameNanos { _ ->
                    val speed = when(voiceState) {
                        VoiceState.SPEAKING -> 3f  // Швидший танець
                        VoiceState.THINKING -> 1f  // Повільні роздуми
                        else -> 1.5f               // Спокійний рух
                    }
                    time += 0.016f * speed
                    
                    val breath = sin(time * 2f) * 2.5f 
                    
                    when (voiceState) {
                        VoiceState.SPEAKING -> {
                            // Танець/жестикуляція під час розмови
                            chest?.rotation = Rotation(x = breath, z = sin(time * 2f) * 5f)
                            head?.rotation = Rotation(
                                x = sin(time * 4f) * 5f, 
                                y = sin(time * 3f) * 10f,
                                z = sin(time * 5f) * 5f
                            )
                            hips?.rotation = Rotation(z = sin(time * 3f) * 8f)
                            
                            // Махи руками
                            leftArm?.rotation = Rotation(z = -65f + sin(time * 3f) * 30f, x = sin(time * 3f) * 20f)
                            rightArm?.rotation = Rotation(z = 65f - sin(time * 3f) * 30f, x = -sin(time * 3f) * 20f)
                            leftElbow?.rotation = Rotation(y = sin(time * 3f) * 20f)
                            rightElbow?.rotation = Rotation(y = -sin(time * 3f) * 20f)
                        }
                        VoiceState.THINKING -> {
                            // Задумливі рухи
                            chest?.rotation = Rotation(x = breath * 0.5f)
                            head?.rotation = Rotation(x = -10f + sin(time * 1f) * 3f, z = 15f)
                            hips?.rotation = Rotation(z = sin(time * 1f) * 2f)
                            
                            leftArm?.rotation = Rotation(z = -70f) // Руки опущені
                            rightArm?.rotation = Rotation(z = 70f)
                            leftElbow?.rotation = Rotation(y = 60f) // Рука біля підборіддя
                            rightElbow?.rotation = Rotation(y = -60f)
                        }
                        VoiceState.LISTENING -> {
                            // Уважне слухання
                            chest?.rotation = Rotation(x = breath)
                            head?.rotation = Rotation(x = sin(time * 1.5f) * 2f, z = 5f)
                            hips?.rotation = Rotation(z = sin(time * 1.5f) * 2f)
                            
                            leftArm?.rotation = Rotation(z = -65f)
                            rightArm?.rotation = Rotation(z = 65f)
                        }
                        else -> {
                            // Стан спокою (танцювальне погойдування)
                            chest?.rotation = Rotation(x = breath)
                            head?.rotation = Rotation(y = sin(time * 1.2f) * 6f)
                            hips?.rotation = Rotation(z = sin(time * 1.5f) * 4f)
                            
                            leftArm?.rotation = Rotation(z = -65f + sin(time * 1.5f) * 10f)
                            rightArm?.rotation = Rotation(z = 65f - sin(time * 1.5f) * 10f)
                        }
                    }
                }
            }
        }

        Scene(
            modifier = modifier
                .fillMaxSize()
                // Блокуємо всі дотики, щоб неможливо було крутити камеру
                .pointerInput(Unit) { detectTapGestures { } },
            engine = engine,
            modelLoader = modelLoader,
            childNodes = childNodes,
            cameraNode = rememberCameraNode(engine) {
                position = Position(0f, 1.2f, 3.0f)
            }
        )
    } else {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(errorText ?: "Завантаження...", color = Color.Red)
        }
    }
}
