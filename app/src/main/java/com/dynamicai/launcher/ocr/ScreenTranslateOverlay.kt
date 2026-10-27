package com.dynamicai.launcher.ocr

import com.dynamicai.launcher.ui.theme.AppTheme
import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
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

object ScreenTranslateOverlay {

    private const val TAG = "TranslateOverlay"

    private var wm: WindowManager? = null
    private var view: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    fun show(
        context: Context,
        blocks: List<TranslatedBlock>,
        targetLang: String,
        onCopy: (String) -> Unit = {}
    ) {
        if (view != null) hide()
        try {
            val wmLocal = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val owner = OverlayLifecycleOwner()

            val composeView = ComposeView(context).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    TranslateContent(blocks, targetLang, onCopy = onCopy, onClose = { hide() })
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            owner.onCreate()
            wmLocal.addView(composeView, params)
            wm = wmLocal
            view = composeView
            lifecycleOwner = owner
            Log.d(TAG, "Overlay shown with ${blocks.size} blocks")
        } catch (t: Throwable) {
            Log.e(TAG, "show failed: ${t.message}", t)
        }
    }

    fun hide() {
        try {
            view?.let { wm?.removeView(it) }
        } catch (_: Throwable) {}
        view = null
        wm = null
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
    }

    /** Мінімальний LifecycleOwner для Compose overlay */
    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

        fun onCreate() {
            savedStateController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun onDestroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
    }
}

@Composable
private fun TranslateContent(
    blocks: List<TranslatedBlock>,
    targetLang: String,
    onCopy: (String) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            AppTheme.colors.surface.copy(alpha = 0.93f),
                            AppTheme.colors.glassBackgroundAlt.copy(alpha = 0.8f)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${blocks.size} блоків · $targetLang",
                color = AppTheme.colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AppTheme.colors.textPrimary.copy(alpha = 0.2f))
                    .clickable { onClose() }
            ) {
                Text("X", color = AppTheme.colors.textPrimary, fontSize = 18.sp)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp, bottom = 16.dp, start = 12.dp, end = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            blocks.forEach { b ->
                BlockCard(b, onCopy = onCopy)
            }
        }
    }
}

@Composable
private fun BlockCard(block: TranslatedBlock, onCopy: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        AppTheme.colors.surface.copy(alpha = 0.94f),
                        AppTheme.colors.glassBackgroundAlt.copy(alpha = 0.9f)
                    )
                )
            )
            .border(
                width = 0.5.dp,
                color = AppTheme.colors.glassBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onCopy(block.translatedText) }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            block.originalText,
            color = AppTheme.colors.textPrimary.copy(alpha = 0.55f),
            fontSize = 12.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            block.translatedText,
            color = AppTheme.colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
