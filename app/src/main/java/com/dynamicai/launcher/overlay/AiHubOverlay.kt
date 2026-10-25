package com.dynamicai.launcher.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AiHubOverlay {
    private const val TAG = "AiHubOverlay"
    private var wm: WindowManager? = null
    private var view: ComposeView? = null
    private var owner: HubLifecycleOwner? = null
    private var appContext: android.content.Context? = null

    fun show(context: Context, vm: com.dynamicai.launcher.AppListViewModel? = null) {
        if (view != null) return
        appContext = context.applicationContext
        try {
            // Ховаємо док, щоб він не перекривав кнопки
            try { FloatingDockService.hide(context) } catch (_: Throwable) {}

            val wmLocal = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val lcOwner = HubLifecycleOwner()

            val composeView = ComposeView(context).apply {
                setViewTreeLifecycleOwner(lcOwner)
                setViewTreeSavedStateRegistryOwner(lcOwner)
                setContent {
                    AiHubContent(
                        ctx = context,
                        vm = vm,
                        onClose = { hide() }
                    )
                }
            }

            // Фокусабельне вікно з затемненням фону
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM
                dimAmount = 0.6f // Затемнення фону
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
        // Повертаємо док (якщо він був увімкнений)
        // (перевірка налаштувань не потрібна, show() сам перевірить)
        appContext?.let {
            try { FloatingDockService.show(it) } catch (_: Throwable) {}
        }
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
@OptIn(ExperimentalLayoutApi::class)
private fun AiHubContent(ctx: Context, vm: com.dynamicai.launcher.AppListViewModel?, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var aiResponse by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
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
            .background(Color.Black.copy(alpha = 0.4f)) // Додаткове затемнення
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onClose() } // Клік по фону закриває хаб
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { } // Блокуємо клік по самій панелі
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Відповідь AI
            if (aiResponse.isNotBlank() || isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xEE1A1A1A))
                        .padding(16.dp)
                        .heightIn(max = 250.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (isLoading && aiResponse.isBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = Color(0xFF64B5F6), modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Думаю...", color = Color.White)
                        }
                    } else {
                        Text(aiResponse, color = Color.White, fontSize = 15.sp)
                    }
                }
            }

            // Чіпи швидких дій
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionChip("Пошук", Color(0xFF4285F4)) {
                    Toast.makeText(ctx, "Пошук...", Toast.LENGTH_SHORT).show()
                    onClose()
                    val i = Intent(Intent.ACTION_WEB_SEARCH).apply {
                        putExtra("query", query.ifBlank { "Google" })
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try { ctx.startActivity(i) } catch (_: Throwable) {}
                }
                ActionChip("Переклад", Color(0xFFFF9800)) {
                    Toast.makeText(ctx, "Переклад...", Toast.LENGTH_SHORT).show()
                    onClose()
                    com.dynamicai.launcher.ocr.RegionSelectorOverlay.show(
                        context = ctx,
                        onSelected = { rect ->
                            CoroutineScope(Dispatchers.Main).launch {
                                kotlinx.coroutines.delay(150)
                                val translator = com.dynamicai.launcher.ocr.ScreenTranslator(ctx, null)
                                val blocks = translator.captureRegionAndTranslate(rect, targetLang = "uk")
                                if (blocks.isNotEmpty()) {
                                    com.dynamicai.launcher.ocr.ScreenTranslateOverlay.show(ctx, blocks, "uk")
                                }
                            }
                        },
                        onCancel = { }
                    )
                }
                ActionChip("🎙️ Розмова", Color(0xFFE91E63)) {
                    try {
                        ctx.startActivity(android.content.Intent(ctx, com.dynamicai.launcher.VoiceChatActivity::class.java).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    } catch (t: Throwable) {
                        android.util.Log.e("AiHubOverlay", "VoiceChat failed: ${t.message}", t)
                    }
                    onClose()
                }
                ActionChip("🎮 Wild Rift", Color(0xFF9C27B0)) {
                    try {
                        ctx.startActivity(android.content.Intent(ctx, com.dynamicai.launcher.WildRiftAdvisorActivity::class.java).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    } catch (t: Throwable) {
                        android.util.Log.e("AiHubOverlay", "Wild Rift launch failed: ${t.message}", t)
                    }
                    onClose()
                }
                                ActionChip("AI", Color(0xFF2196F3)) {
                    if (query.isNotBlank() && vm != null) {
                        isLoading = true
                        aiResponse = ""
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                vm.chatEngine.answerStreaming(query) { token ->
                                    aiResponse += token
                                }
                            } catch (t: Throwable) {
                                val err = "Помилка: ${t.message}"
                                aiResponse += "\n$err"
                                Toast.makeText(ctx, err, Toast.LENGTH_LONG).show()
                            } finally {
                                isLoading = false
                            }
                        }
                    } else if (vm == null) {
                        aiResponse = "AI не ініціалізовано"
                        Toast.makeText(ctx, "VM is null", Toast.LENGTH_SHORT).show()
                    } else {
                        aiResponse = "Введи питання"
                    }
                }
            }

            // Поле вводу
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
                        .clickable {
                            Toast.makeText(ctx, "Мікрофон...", Toast.LENGTH_SHORT).show()
                            onClose()
                            val voiceIntent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
                                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Слухаю...")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try { ctx.startActivity(voiceIntent) } catch (_: Throwable) {}
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
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
