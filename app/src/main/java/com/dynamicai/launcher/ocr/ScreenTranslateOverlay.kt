package com.dynamicai.launcher.ocr

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

object ScreenTranslateOverlay {

    private const val TAG = "TranslateOverlay"

    private var wm: WindowManager? = null
    private var view: ComposeView? = null

    fun show(
        context: Context,
        blocks: List<TranslatedBlock>,
        targetLang: String,
        onCopy: (String) -> Unit = {}
    ) {
        if (view != null) hide()
        try {
            val wmLocal = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val composeView = ComposeView(context).apply {
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

            wmLocal.addView(composeView, params)
            wm = wmLocal
            view = composeView
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
        // Верхня панель
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xEE1A1A1A),
                            Color(0xCC0D0D0D)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "🌐 ${blocks.size} блоків · $targetLang",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
                    .clickable { onClose() }
            ) {
                Text("✕", color = Color.White, fontSize = 18.sp)
            }
        }

        // Список перекладених блоків
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
                        Color(0xF01A1A1A),
                        Color(0xE60D0D0D)
                    )
                )
            )
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable {
                onCopy(block.translatedText)
                val cm = android.content.ClipboardManager::class
            }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            block.originalText,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            block.translatedText,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
