package com.dynamicai.launcher.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.dynamicai.launcher.model.AppInfo

@Composable
fun DockOverlayContent(
    appsFlow: () -> List<AppInfo>,
    getDockPkgs: () -> List<String>,
    onLaunch: (String) -> Unit,
    onOpenSearch: () -> Unit
) {
    val apps = appsFlow()
    val dockPkgs = remember(apps) { getDockPkgs() }
    val dockApps = remember(apps, dockPkgs) { apps.filter { it.packageName in dockPkgs } }

    var expanded by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.7f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "alpha"
    )

    Box(modifier = Modifier.wrapContentSize()) {
        AnimatedVisibility(
            visible = !expanded,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it }
        ) {
            // Згорнутий стан — вертикальна риска
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(end = 2.dp)
                    .size(width = 8.dp, height = 96.dp)
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .background(Color.White.copy(alpha = alpha))
                    .clickable { expanded = true }
            ) {}
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it }
        ) {
            Row(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                    .background(Color(0xCC1A1A1A))
                    .clickable { }  // перехоплює тапи, щоб не проходили крізь
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Кнопка згортання
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A2A2A))
                        .clickable { expanded = false }
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = "Згорнути",
                        tint = Color.White
                    )
                }

                Spacer(Modifier.width(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Кнопка пошуку
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2196F3).copy(alpha = 0.3f))
                            .clickable(onClick = onOpenSearch)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Пошук",
                            tint = Color(0xFF64B5F6)
                        )
                    }

                    // Застосунки з дока
                    dockApps.take(6).forEach { app ->
                        Icon(
                            bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
                            contentDescription = app.label,
                            tint = Color.Unspecified,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable { onLaunch(app.packageName) }
                        )
                    }
                }
            }
        }
    }
}
