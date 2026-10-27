package com.dynamicai.launcher.overlay

import com.dynamicai.launcher.ui.theme.AppTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
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
    iconSizeDp: Int,
    handleLengthDp: Int,
    handleAlphaPercent: Int,
    onLaunch: (String) -> Unit,
    onOpenSearch: () -> Unit
) {
    val apps = appsFlow()
    val dockPkgs = remember(apps) { getDockPkgs() }
    val dockApps = remember(apps, dockPkgs) { apps.filter { it.packageName in dockPkgs } }

    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.wrapContentSize()) {

        // === Згорнутий стан — тонка риска ===
        AnimatedVisibility(
            visible = !expanded,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(end = 3.dp)
                    .size(width = 4.dp, height = handleLengthDp.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AppTheme.colors.textPrimary.copy(alpha = handleAlphaPercent / 100f))
                    .pointerInput(Unit) { detectTapGestures { expanded = true } }
            ) {}
        }

        // === Розгорнутий стан — вертикальний стовпчик круглих іконок ===
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it }
        ) {
            Column(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .pointerInput(Unit) { detectTapGestures { } }  // перехоплює тапи, щоб не проходили крізь
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Кнопка закриття (X) — тонке коло
                CircleIconButton(
                    size = iconSizeDp,
                    background = AppTheme.colors.glassBackground.copy(alpha = 0.2f),
                    border = AppTheme.colors.textPrimary.copy(alpha = 0.35f),
                    onTap = { expanded = false }
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Закрити",
                        tint = AppTheme.colors.textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Кнопка пошуку — акцентна
                CircleIconButton(
                    size = iconSizeDp,
                    background = AppTheme.colors.accentDark.copy(alpha = 0.25f),
                    border = AppTheme.colors.accent.copy(alpha = 0.6f),
                    onTap = onOpenSearch
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Пошук",
                        tint = AppTheme.colors.accent,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Іконки застосунків у світлих кружечках
                dockApps.take(6).forEach { app ->
                    AppCircleIcon(app, iconSizeDp) { onLaunch(app.packageName) }
                }
            }
        }
    }
}

@Composable
private fun CircleIconButton(
    size: Int,
    background: Color,
    border: Color,
    onTap: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background)
            .border(width = 1.5.dp, color = border, shape = CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            }
    ) {
        content()
    }
}

@Composable
private fun AppCircleIcon(app: AppInfo, sizeDp: Int, onTap: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "iconScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(sizeDp.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(AppTheme.colors.textPrimary)
            .border(width = 1.5.dp, color = AppTheme.colors.textPrimary.copy(alpha = 0.9f), shape = CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onTap() }
                )
            }
    ) {
        Icon(
            bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
            contentDescription = app.label,
            tint = Color.Unspecified,
            modifier = Modifier
                .fillMaxSize()
                .padding((sizeDp * 0.11f).dp)
                .clip(CircleShape)
        )
    }
}
