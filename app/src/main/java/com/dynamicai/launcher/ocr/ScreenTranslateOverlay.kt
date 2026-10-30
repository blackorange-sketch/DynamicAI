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
                    com.dynamicai.launcher.ui.theme.StoredAppTheme {
                        TranslateContent(blocks, targetLang, onCopy = onCopy, onClose = { hide() })
                    }
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

private fun copyText(ctx: Context, text: String) {
    try {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("translation", text))
        android.widget.Toast.makeText(ctx, "Скопійовано", android.widget.Toast.LENGTH_SHORT).show()
    } catch (_: Throwable) {}
}

@Composable
private fun TranslateContent(
    blocks: List<TranslatedBlock>,
    targetLang: String,
    onCopy: (String) -> Unit,
    onClose: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var inline by remember { mutableStateOf(true) }
    val copy: (String) -> Unit = { copyText(ctx, it); onCopy(it) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (inline) 0.18f else 0.45f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { if (inline) onClose() }
    ) {
        if (inline) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            blocks.forEach { b ->
                InlineBlock(b, density, copy)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 64.dp, bottom = 16.dp, start = 12.dp, end = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                blocks.forEach { b -> BlockCard(b, onCopy = copy) }
            }
        }

        // Плаваюча панель: режим, копіювати все, закрити
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(AppTheme.colors.surface.copy(alpha = 0.95f))
                .border(0.5.dp, AppTheme.colors.glassBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "${blocks.size} · ${targetLang.uppercase()}",
                color = AppTheme.colors.textSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
            ToolPill(if (inline) "Список" else "На місці") { inline = !inline }
            ToolPill("Копіювати все") { copy(blocks.joinToString("\n\n") { it.translatedText }) }
            ToolPill("✕") { onClose() }
        }
    }
}

@Composable
private fun ToolPill(label: String, onClick: () -> Unit) {
    Text(
        label, color = AppTheme.colors.textPrimary, fontSize = 13.sp, maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(AppTheme.colors.textPrimary.copy(alpha = 0.10f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

/** Переклад поверх оригіналу: рамка за координатами OCR, розмір шрифту підбирається під площу блока. */
@Composable
private fun InlineBlock(b: TranslatedBlock, density: androidx.compose.ui.unit.Density, onCopy: (String) -> Unit) {
    val r = b.bounds
    val wDp = with(density) { r.width().toDp() }
    val hDp = with(density) { r.height().toDp() }
    val area = r.width().toFloat() * r.height().toFloat()
    val chars = b.translatedText.length.coerceAtLeast(1)
    val fontPx = kotlin.math.sqrt(area / (chars * 0.62f * 1.3f))
    val fontSp = with(density) { fontPx.toSp() }.value.coerceIn(10f, 22f)
    Box(
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset(r.left, r.top) }
            .width(wDp)
            .heightIn(min = hDp)
            .clip(RoundedCornerShape(6.dp))
            .background(AppTheme.colors.surface.copy(alpha = 0.96f))
            .clickable { onCopy(b.translatedText) }
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            b.translatedText,
            color = AppTheme.colors.textPrimary,
            fontSize = fontSp.sp,
            lineHeight = (fontSp * 1.2f).sp
        )
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
