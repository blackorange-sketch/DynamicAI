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

// Допоміжна функція для пошуку кісток (з перевіркою на null)
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
fun Anime3DAvatar(modifier: Modifier = Modifier) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val childNodes = rememberNodes()
    
    var errorText by remember { mutableStateOf<String?>(null) }

    val modelNode = remember {
        try {
            val instance = modelLoader.createModelInstance("anime_girl.glb")
            val node = ModelNode(modelInstance = instance, scaleToUnits = 1.2f)
            
            // Спроба виправити T-позу (опустити руки вниз)
            val leftArm = findBone(node, listOf("L_UpperArm", "UpperArm_L"))
            val rightArm = findBone(node, listOf("R_UpperArm", "UpperArm_R"))
            
            // Повертаємо руки вздовж тіла (значення підібрано для стандартного VRM)
            leftArm?.rotation = Rotation(z = -65f)
            rightArm?.rotation = Rotation(z = 65f)
            
            node
        } catch (e: Exception) {
            errorText = "Помилка: " + e.javaClass.simpleName + " - " + e.message
            null
        }
    }

    if (modelNode != null) {
        LaunchedEffect(modelNode) {
            childNodes.add(modelNode)
            
            // Збираємо всі назви кісток у логи (безпечно для null)
            val allBones = mutableListOf<String>()
            fun collectNames(n: Node) {
                val name = n.name
                if (!name.isNullOrEmpty()) allBones.add(name)
                n.childNodes.forEach { collectNames(it) }
            }
            collectNames(modelNode)
            android.util.Log.d("Anime3D_Bones", "Available bones: " + allBones.joinToString(", "))
            
            // Знаходимо кістки для анімації
            val head = findBone(modelNode, listOf("C_Head", "Head"))
            val chest = findBone(modelNode, listOf("C_Chest", "Chest", "C_Spine", "Spine"))
            val hips = findBone(modelNode, listOf("C_Hips", "Hips"))

            var time = 0f
            // Нескінченний цикл анімації (оновлюється кожен кадр)
            while (true) {
                withFrameNanos { _ ->
                    time += 0.016f // ~60 FPS
                    
                    // 1. Дихання (грудна клітка підіймається і опускається)
                    val breath = sin(time * 2f) * 2.5f 
                    chest?.rotation = Rotation(x = breath)
                    
                    // 2. Рух голови (легкий нахил та погойдування)
                    head?.rotation = Rotation(
                        x = breath * 0.5f, 
                        y = sin(time * 1.2f) * 4f
                    )
                    
                    // 3. Перенесення ваги з ноги на ногу (погойдування тазу)
                    val sway = sin(time * 1.5f) * 1.5f
                    hips?.rotation = Rotation(z = sway)
                }
            }
        }

        Scene(
            modifier = modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            childNodes = childNodes,
            cameraNode = rememberCameraNode(engine) {
                position = Position(0f, 1.4f, 3.5f)
            }
        )
    } else {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(errorText ?: "Завантаження...", color = Color.Red)
        }
    }
}
