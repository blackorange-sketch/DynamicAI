package com.dynamicai.launcher.ocr

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object RegionSelectorOverlay {

    private const val TAG = "RegionSelector"

    private var wm: WindowManager? = null
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null

    fun show(
        context: Context,
        onSelected: (Rect) -> Unit,  // у пікселях екрана
        onCancel: () -> Unit = {}
    ) {
        hide()
        try {
            val wmLocal = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val lcOwner = OverlayOwner()

            val composeView = ComposeView(context).apply {
                setViewTreeLifecycleOwner(lcOwner)
                setViewTreeSavedStateRegistryOwner(lcOwner)
                setContent {
                    RegionSelectorContent(
                        onSelected = { rect ->
                            hide()
                            onSelected(rect)
                        },
                        onCancel = {
                            hide()
                            onCancel()
                        }
                    )
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            lcOwner.onCreate()
            wmLocal.addView(composeView, params)
            wm = wmLocal
            view = composeView
            owner = lcOwner
            Log.d(TAG, "Region selector shown")
        } catch (t: Throwable) {
            Log.e(TAG, "show failed: ${t.message}", t)
        }
    }

    fun hide() {
        try { view?.let { wm?.removeView(it) } } catch (_: Throwable) {}
        view = null
        wm = null
        owner?.onDestroy()
        owner = null
    }

    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lr = LifecycleRegistry(this)
        private val src = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = lr
        override val savedStateRegistry: SavedStateRegistry get() = src.savedStateRegistry
        fun onCreate() { src.performRestore(null); lr.currentState = Lifecycle.State.RESUMED }
        fun onDestroy() { lr.currentState = Lifecycle.State.DESTROYED }
    }
}

@Composable
private fun RegionSelectorContent(
    onSelected: (Rect) -> Unit,
    onCancel: () -> Unit
) {
    val density = LocalDensity.current
    var start by remember { mutableStateOf(Offset.Zero) }
    var current by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }

    val rect = remember(start, current) {
        if (!dragging && start == Offset.Zero) Rect(0, 0, 0, 0)
        else Rect(
            min(start.x, current.x).toInt(),
            min(start.y, current.y).toInt(),
            max(start.x, current.x).toInt(),
            max(start.y, current.y).toInt()
        )
    }

    val isValid = rect.width() > 60 && rect.height() > 60

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        start = offset
                        current = offset
                        dragging = true
                    },
                    onDrag = { change, amount ->
                        current = Offset(
                            current.x + amount.x,
                            current.y + amount.y
                        )
                        change.consume()
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false }
                )
            }
    ) {
        // Прямокутник виділення
        if (rect.width() > 0 && rect.height() > 0) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(rect.left, rect.top) }
                    .width(with(density) { rect.width().toDp() })
                    .height(with(density) { rect.height().toDp() })
                    .border(
                        width = 2.dp,
                        color = Color(0xFF64B5F6),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }

        // Верхня підказка
        if (!dragging && rect.width() == 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xEE1A1A1A))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    "Виділи область з текстом",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Кнопки внизу
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Btn(
                text = "Скасувати",
                color = Color(0xFF616161),
                modifier = Modifier.weight(1f)
            ) { onCancel() }

            Btn(
                text = "Перекласти",
                color = if (isValid) Color(0xFF2196F3) else Color(0xFF37474F),
                modifier = Modifier.weight(1f),
                enabled = isValid
            ) {
                if (isValid) {
                    // Конвертуємо px у Int для Rect
                    onSelected(rect)
                }
            }
        }
    }
}

@Composable
private fun Btn(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(color)
            .then(
                if (enabled) Modifier.pointerInput(Unit) {
                    detectTapGestures { onClick() }
                } else Modifier
            )
    ) {
        Text(
            text,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
