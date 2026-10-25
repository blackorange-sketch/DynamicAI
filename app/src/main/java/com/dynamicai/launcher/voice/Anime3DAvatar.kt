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
import androidx.compose.ui.input.pointer.pointerInput
import com.dynamicai.launcher.VoiceState
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
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

    // 1. ДОДАЄМО СВІТЛО (щоб повернути природні кольори)
    val lightNode = remember {
        try {
            LightNode(engine).apply {
                // Робимо світло яскравим, але розсіяним
                intensity = 100_000f
                transform = io.github.sceneview.math.Transform(
                    position = Position(2f, 4f, 3f)
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    val modelNode = remember {
        try {
            val instance = modelLoader.createModelInstance("anime_girl.glb")
            val node = ModelNode(modelInstance = instance, scaleToUnits = 1.5f)
            
            node.rotation = Rotation(y = 180f)
            node.position = Position(0f, -1.2f, 0f)
            
            node
        } catch (e: Exception) {
            errorText = "Помилка: " + e.javaClass.simpleName + " - " + e.message
            null
        }
    }

    if (modelNode != null) {
        LaunchedEffect(modelNode, voiceState) {
            lightNode?.let { childNodes.add(it) }
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
                    // Плавний плин часу (60 fps)
                    time += 0.016f
                    
                    // Людське дихання (повільне)
                    val breath = sin(time * 1.5f) * 1.5f 
                    // Погойдування тіла (наче переносить вагу)
                    val sway = sin(time * 0.8f) * 2.0f
                    
                    when (voiceState) {
                        VoiceState.SPEAKING -> {
                            // Природна жестикуляція: плавні рухи головою та руками
                            chest?.rotation = Rotation(x = breath, z = sway * 0.5f)
                            
                            // Кивання головою (плавне)
                            head?.rotation = Rotation(
                                x = sin(time * 3f) * 2f, 
                                y = sin(time * 1.5f) * 5f,
                                z = sin(time * 2f) * 2f
                            )
                            
                            // Жестикуляція руками (піднімає та опускає)
                            leftArm?.rotation = Rotation(z = -65f, x = sin(time * 2.5f) * 15f - 10f)
                            rightArm?.rotation = Rotation(z = 65f, x = -sin(time * 2.5f) * 15f - 10f)
                            
                            // Згинання ліктів (жести)
                            leftElbow?.rotation = Rotation(y = sin(time * 2f) * 15f + 20f)
                            rightElbow?.rotation = Rotation(y = -sin(time * 2f) * 15f - 20f)
                            
                            hips?.rotation = Rotation(z = sway)
                        }
                        VoiceState.THINKING -> {
                            // Задумливий стан: нахил голови, рука біля обличчя
                            chest?.rotation = Rotation(x = breath * 0.5f)
                            head?.rotation = Rotation(x = -5f, z = 10f + sin(time * 0.5f) * 2f)
                            
                            leftArm?.rotation = Rotation(z = -70f)
                            rightArm?.rotation = Rotation(z = 70f, x = -40f) // Права рука трохи піднята
                            rightElbow?.rotation = Rotation(y = -80f) // Долоня біля підборіддя
                            
                            hips?.rotation = Rotation(z = sway * 0.5f)
                        }
                        VoiceState.LISTENING -> {
                            // Уважне слухання: дивиться прямо, іноді киває
                            chest?.rotation = Rotation(x = breath)
                            
                            // Легкий нахил голови
                            head?.rotation = Rotation(
                                x = 2f, 
                                z = 3f + sin(time * 1.2f) * 2f
                            )
                            
                            // Руки вільно опущені
                            leftArm?.rotation = Rotation(z = -65f, x = sin(time * 0.5f) * 2f)
                            rightArm?.rotation = Rotation(z = 65f, x = sin(time * 0.5f) * 2f)
                            
                            hips?.rotation = Rotation(z = sway)
                        }
                        else -> {
                            // Спокій (Idle): просто дихає і стоїть
                            chest?.rotation = Rotation(x = breath)
                            head?.rotation = Rotation(y = sin(time * 0.5f) * 3f)
                            
                            leftArm?.rotation = Rotation(z = -65f, x = sin(time * 0.8f) * 2f)
                            rightArm?.rotation = Rotation(z = 65f, x = sin(time * 0.8f) * 2f)
                            
                            hips?.rotation = Rotation(z = sway)
                        }
                    }
                }
            }
        }

        Scene(
            modifier = modifier
                .fillMaxSize()
                // 2. ПОВНЕ БЛОКУВАННЯ ЖЕСТІВ (щоб не втікала)
                .pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent().changes.forEach { it.consume() } } } },
            engine = engine,
            modelLoader = modelLoader,
            childNodes = childNodes,
            // ВИМИКАЄМО МОЖЛИВІСТЬ КРУТИТИ КАМЕРУ НА РІВНІ SCENEVIEW
            cameraManipulator = null, 
            cameraNode = rememberCameraNode(engine) {
                position = Position(0f, 1.0f, 3.5f)
            }
        )
    } else {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(errorText ?: "Завантаження...", color = Color.Red)
        }
    }
}
