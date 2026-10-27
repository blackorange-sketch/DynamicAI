package com.dynamicai.launcher

import com.dynamicai.launcher.ui.theme.AppTheme
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AiHubActivity : ComponentActivity() {
    private val vm: AppListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Прозорий фон для активності (щоб бачити лаунчер ззаду)
        window.setBackgroundDrawableResource(android.R.color.transparent)

        // iOS-style blur на Android 12+
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = 80
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0.15f)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0.5f)
        }

        setContent {
            AiHubScreen(vm = vm, blurBgPath = intent.getStringExtra("blur_bg_path"), onClose = { finish() })
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun AiHubScreen(vm: AppListViewModel, blurBgPath: String?, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var showModelSettings by remember { mutableStateOf(false) }
    var aiResponse by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var webViewUrl by remember { mutableStateOf<String?>(null) }
    
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) {
                query = text
                if (!isLoading) {
                    isLoading = true
                    aiResponse = ""
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            aiResponse = "🧠 Завантажую LLM…"
                            val loaded = vm.ensureLlmLoaded()
                            if (!loaded) {
                                aiResponse = "LLM не завантажена"
                                return@launch
                            }
                            aiResponse = ""
                            vm.chatEngine.answerStreaming(query) { token -> aiResponse += token }
                        } catch (t: Throwable) {
                            aiResponse += "\nПомилка: ${t.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onClose() }
    ) {
        // 1. Фонове зображення (скріншот)
        if (blurBgPath != null) {
            val bmp = remember(blurBgPath) {
                try {
                    val f = java.io.File(blurBgPath)
                    if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.absolutePath) else null
                } catch (_: Throwable) { null }
            }
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (webViewUrl != null) {
            // Екран WebView
            Column(modifier = Modifier.fillMaxSize().padding(top = 40.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Пошук", color = AppTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Закрити", color = AppTheme.colors.accent, modifier = Modifier.clickable { webViewUrl = null }, fontSize = 16.sp)
                }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = true
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { it.loadUrl(webViewUrl!!) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // AI Hub UI
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { }
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Відповідь AI з liquid glass
                if (aiResponse.isNotBlank() || isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppTheme.colors.glassBackground.copy(alpha = 0.33f))
                            .border(0.5.dp, AppTheme.colors.glassBorder, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                            .heightIn(max = 250.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (isLoading && aiResponse.isBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = AppTheme.colors.accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text("Думаю...", color = AppTheme.colors.textPrimary)
                            }
                        } else {
                            Text(aiResponse, color = AppTheme.colors.textPrimary, fontSize = 15.sp)
                        }
                    }
                }

                // Чіпи
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionChip("Пошук", Color(0xFF4285F4)) {
                        if (query.isNotBlank()) {
                            webViewUrl = "https://duckduckgo.com/?q=${android.net.Uri.encode(query)}"
                        }
                    }
                    ActionChip("Переклад", AppTheme.colors.warning) {
                        onClose()
                        com.dynamicai.launcher.ocr.RegionSelectorOverlay.show(
                            context = vm.getApplication(),
                            onSelected = { rect ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    kotlinx.coroutines.delay(150)
                                    val translator = com.dynamicai.launcher.ocr.ScreenTranslator(vm.getApplication(), null)
                                    val blocks = translator.captureRegionAndTranslate(rect, targetLang = "uk")
                                    if (blocks.isNotEmpty()) {
                                        com.dynamicai.launcher.ocr.ScreenTranslateOverlay.show(vm.getApplication(), blocks, "uk")
                                    }
                                }
                            },
                            onCancel = { }
                        )
                    }
                    ActionChip("🎙️ Розмова", AppTheme.colors.pink) {
                        try {
                            val app = vm.getApplication<android.app.Application>()
                            app.startActivity(android.content.Intent(app, com.dynamicai.launcher.VoiceChatActivity::class.java).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (t: Throwable) {
                            android.util.Log.e("AiHub", "VoiceChat failed: ${t.message}", t)
                        }
                        onClose()
                    }
                    ActionChip("🎮 Wild Rift", AppTheme.colors.purple) {
                        try {
                            val app = vm.getApplication<android.app.Application>()
                            app.startActivity(android.content.Intent(app, com.dynamicai.launcher.WildRiftAdvisorActivity::class.java).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (t: Throwable) {
                            android.util.Log.e("AiHub", "Wild Rift launch failed: ${t.message}", t)
                        }
                        onClose()
                    }
                    ActionChip("AI", AppTheme.colors.accentDark) {
                        if (query.isNotBlank()) {
                            isLoading = true
                            aiResponse = ""
                            CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    aiResponse = "🧠 Завантажую LLM…"
                                    val loaded = vm.ensureLlmLoaded()
                                    if (!loaded) {
                                        aiResponse = "LLM не завантажена"
                                        return@launch
                                    }
                                    aiResponse = ""
                                    vm.chatEngine.answerStreaming(query) { token -> aiResponse += token }
                                } catch (t: Throwable) {
                                    aiResponse += "\nПомилка: ${t.message}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        } else {
                            aiResponse = "Введи питання"
                        }
                    }
                }

                // Поле вводу з liquid glass
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(AppTheme.colors.glassBackground.copy(alpha = 0.25f))
                        .border(
                            width = 0.5.dp,
                            brush = Brush.verticalGradient(listOf(AppTheme.colors.textPrimary.copy(alpha = 0.45f), AppTheme.colors.textPrimary.copy(alpha = 0.08f))),
                            shape = RoundedCornerShape(28.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        textStyle = TextStyle(color = AppTheme.colors.textPrimary, fontSize = 16.sp),
                        cursorBrush = SolidColor(AppTheme.colors.accent),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            if (query.isNotBlank()) {
                                isLoading = true
                                aiResponse = ""
                                CoroutineScope(Dispatchers.Main).launch {
                                    try {
                                        vm.chatEngine.answerStreaming(query) { token -> aiResponse += token }
                                    } catch (t: Throwable) {
                                        aiResponse += "\nПомилка: ${t.message}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        }),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 14.dp)
                            .focusRequester(focusRequester),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text("Запитай або скажи...", color = AppTheme.colors.textSecondary, fontSize = 16.sp)
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
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Слухаю...")
                                }
                                try { voiceLauncher.launch(intent) } catch (_: Throwable) {}
                            }
                    ) {
                        Text("🎤", fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, color: Color, onClick: () -> Unit) {
    Text(
        text = label,
        color = AppTheme.colors.textPrimary,
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
