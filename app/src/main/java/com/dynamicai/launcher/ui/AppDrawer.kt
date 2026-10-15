package com.dynamicai.launcher.ui

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.model.AppInfo

@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    backgroundAlphaPercent: Int,
    iconSizeDp: Int,
    fontSizeSp: Int,
    onLaunch: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
    visible: Boolean,
    gridState: LazyGridState = rememberLazyGridState()
) {
    val haptic = LocalHapticFeedback.current
    val ctxLocal = LocalContext.current

    val columns = remember(iconSizeDp) {
        GridCells.Adaptive(minSize = (iconSizeDp + 24).dp)
    }

    // Шпалера — кешується
    val wallpaper = remember {
        try {
            val wm = WallpaperManager.getInstance(ctxLocal.applicationContext)
            val d = wm.drawable
            if (d != null) {
                val srcW = d.intrinsicWidth.coerceAtLeast(1)
                val srcH = d.intrinsicHeight.coerceAtLeast(1)
                val w = (srcW / 3).coerceAtLeast(300)
                val h = (srcH / 3).coerceAtLeast(300)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                d.setBounds(0, 0, w, h)
                d.draw(c)
                bmp.asImageBitmap()
            } else null
        } catch (_: Throwable) { null }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 280,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            )
        ) + fadeIn(
            animationSpec = androidx.compose.animation.core.tween(220)
        ),
        exit = slideOutVertically(
            targetOffsetY = { it / 2 },
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 220,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            )
        ) + fadeOut(
            animationSpec = androidx.compose.animation.core.tween(180)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0A))
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount > 40) onDismiss()
                    }
                }
        ) {
            // ==== Шпалера з RenderEffect blur ====
            wallpaper?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.85f)
                        .graphicsLayer {
                            if (Build.VERSION.SDK_INT >= 31) {
                                renderEffect = android.graphics.RenderEffect
                                    .createBlurEffect(80f, 80f, Shader.TileMode.CLAMP)
                                    .asComposeRenderEffect()
                            }
                        }
                )
            }

            // ==== Dim overlay ====
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0x55000000),
                                Color(0x77000000),
                                Color(0xAA000000)
                            )
                        )
                    )
            )

            // ==== Glass-панель з іконками ====
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.04f),
                                Color.White.copy(alpha = 0.01f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.25f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(top = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(start = 12.dp, end = 48.dp)
                ) {
                    Spacer(Modifier.height(12.dp))

                    LazyVerticalGrid(
                        state = gridState,
                        columns = columns,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(apps, key = { it.packageName }) { app ->
                            AppGridItem(app, iconSizeDp, fontSizeSp) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onLaunch(app)
                                onDismiss()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppGridItem(
    app: AppInfo,
    iconSizeDp: Int,
    fontSizeSp: Int,
    onTap: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onTap = onTap)
            .padding(vertical = 6.dp)
    ) {
        val glassBrush = remember {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.10f),
                    Color.White.copy(alpha = 0.02f)
                )
            )
        }
        val glassBorderBrush = remember {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.22f),
                    Color.White.copy(alpha = 0.04f)
                )
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size((iconSizeDp + 16).dp)
                .clip(RoundedCornerShape((iconSizeDp / 2 + 8).dp))
                .background(brush = glassBrush)
                .border(
                    width = 0.5.dp,
                    brush = glassBorderBrush,
                    shape = RoundedCornerShape((iconSizeDp / 2 + 8).dp)
                )
        ) {
            Icon(
                bitmap = app.bitmap!!.asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier.size(iconSizeDp.dp),
                tint = Color.Unspecified
            )
        }
        if (fontSizeSp > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = app.label,
                color = Color.White,
                fontSize = fontSizeSp.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
