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

    val modelNode = remember {
        try {
            val instance = modelLoader.createModelInstance("anime_girl.glb")
            val node = ModelNode(
                modelInstance = instance, 
                scaleToUnits = 1.5f
            )
            
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
                    time += 0.016f
                    val breath = sin(time * 1.5f) * 1.5f 
                    val sway = sin(time * 0.8f) * 2.0f
                    
                    when (voiceState) {
                        VoiceState.SPEAKING -> {
                            chest?.rotation = Rotation(x = breath, z = sway * 0.5f)
                            head?.rotation = Rotation(x = sin(time * 3f) * 2f, y = sin(time * 1.5f) * 5f, z = sin(time * 2f) * 2f)
                            leftArm?.rotation = Rotation(z = -65f, x = sin(time * 2.5f) * 15f - 10f)
                            rightArm?.rotation = Rotation(z = 65f, x = -sin(time * 2.5f) * 15f - 10f)
                            leftElbow?.rotation = Rotation(y = sin(time * 2f) * 15f + 20f)
                            rightElbow?.rotation = Rotation(y = -sin(time * 2f) * 15f - 20f)
                            hips?.rotation = Rotation(z = sway)
                        }
                        VoiceState.THINKING -> {
                            chest?.rotation = Rotation(x = breath * 0.5f)
                            head?.rotation = Rotation(x = -5f, z = 10f + sin(time * 0.5f) * 2f)
                            leftArm?.rotation = Rotation(z = -70f)
                            rightArm?.rotation = Rotation(z = 70f, x = -40f)
                            rightElbow?.rotation = Rotation(y = -80f)
                            hips?.rotation = Rotation(z = sway * 0.5f)
                        }
                        VoiceState.LISTENING -> {
                            chest?.rotation = Rotation(x = breath)
                            head?.rotation = Rotation(x = 2f, z = 3f + sin(time * 1.2f) * 2f)
                            leftArm?.rotation = Rotation(z = -65f, x = sin(time * 0.5f) * 2f)
                            rightArm?.rotation = Rotation(z = 65f, x = sin(time * 0.5f) * 2f)
                            hips?.rotation = Rotation(z = sway)
                        }
                        else -> {
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
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) { awaitPointerEvent().changes.forEach { it.consume() } }
                    }
                },
            engine = engine,
            modelLoader = modelLoader,
            childNodes = childNodes,
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
