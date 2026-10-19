package com.dynamicai.launcher.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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

object AiHubOverlay {
    private const val TAG = "AiHubOverlay"
    private var wm: WindowManager? = null
    private var view: ComposeView? = null
    private var owner: HubLifecycleOwner? = null

    fun show(context: Context) {
        if (view != null) return
        try {
            val wmLocal = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val lcOwner = HubLifecycleOwner()

            val composeView = ComposeView(context).apply {
                setViewTreeLifecycleOwner(lcOwner)
                setViewTreeSavedStateRegistryOwner(lcOwner)
                setContent {
                    AiHubContent(
                        ctx = context,
                        onClose = { hide() }
                    )
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM
            }

            lcOwner.onCreate()
            wmLocal.addView(composeView, params)
            wm = wmLocal
            view = composeView
            owner = lcOwner
            Log.e(TAG, "AI Hub shown")
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
        Log.e(TAG, "AI Hub hidden")
    }

    private class HubLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lr = LifecycleRegistry(this)
        private val src = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = lr
        override val savedStateRegistry: SavedStateRegistry get() = src.savedStateRegistry
        fun onCreate() { src.performRestore(null); lr.currentState = Lifecycle.State.RESUMED }
        fun onDestroy() { lr.currentState = Lifecycle.State.DESTROYED }
    }
}

@Composable
private fun AiHubContent(ctx: Context, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .imePadding()
            .pointerInput(Unit) { detectTapGestures { onClose() } }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 32.dp)
                .pointerInput(Unit) { detectTapGestures { } }, // блокуємо клік по фону
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Чіпи швидких дій
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionChip("Пошук", Color(0xFF4285F4)) {
                    onClose()
                    val i = Intent(ctx, com.dynamicai.launcher.MainActivity::class.java).apply {
                        putExtra("hub_action", "search")
                        putExtra("hub_query", query.ifBlank { "Google" })
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    try { ctx.startActivity(i) } catch (_: Throwable) {}
                }
                ActionChip("Переклад", Color(0xFFFF9800)) {
                    onClose()
                    // Запуск Region Selector (потрібен доступ до нього)
                    com.dynamicai.launcher.ocr.RegionSelectorOverlay.show(
                        context = ctx,
                        onSelected = { rect ->
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(150)
                                try {
                                    val translator = com.dynamicai.launcher.ocr.ScreenTranslator(ctx, null)
                                    val blocks = translator.captureRegionAndTranslate(rect, targetLang = "uk")
                                    if (blocks.isNotEmpty()) {
                                        com.dynamicai.launcher.ocr.ScreenTranslateOverlay.show(ctx, blocks, "uk")
                                    }
                                } catch (t: Throwable) {
                                    android.util.Log.e("AiHub", "translate error: ${t.message}", t)
                                }
                            }
                        },
                        onCancel = { }
                    )
                }
                ActionChip("AI", Color(0xFF2196F3)) {
                    onClose()
                    if (query.isNotBlank()) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            val i = Intent(ctx, com.dynamicai.launcher.MainActivity::class.java).apply {
                                putExtra("ai_query", query)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            try { ctx.startActivity(i) } catch (t: Throwable) {
                                android.util.Log.e("AiHub", "AI intent failed: ${t.message}")
                            }
                        }, 200)
                    }
                }
            }

            // Поле вводу (Gemini-style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xEE1A1A1A))
                    .border(
                        width = 0.5.dp,
                        brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.05f))),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color(0xFF64B5F6)),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        val i = Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra("query", query)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try { ctx.startActivity(i) } catch (_: Throwable) {}
                        onClose()
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 14.dp)
                        .focusRequester(focusRequester),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("Запитай або скажи...", color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp)
                        }
                        inner()
                    }
                )

                // Мікрофон
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FF5252))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                onClose() // 1. Закриваємо оверлей
                                // 2. Запускаємо STT через невелику затримку, щоб оверлей точно зник
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    val i = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
                                        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Слухаю...")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try { ctx.startActivity(i) } catch (t: Throwable) {
                                        android.util.Log.e("AiHub", "STT failed: ${t.message}")
                                    }
                                }, 200)
                            }
                        }
                ) {
                    Text("🎤", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, color: Color, onClick: () -> Unit) {
    Text(
        text = label,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.2f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
