package com.dynamicai.launcher

import android.content.Intent
import androidx.compose.ui.graphics.Color
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
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0.5f)

        setContent {
            AiHubScreen(vm = vm, onClose = { finish() })
        }
    }
}

@Composable
fun AiHubScreen(vm: AppListViewModel, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var aiResponse by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var webViewUrl by remember { mutableStateOf<String?>(null) }
    
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Launcher для мікрофона
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) {
                query = text
                // Автоматично відправляємо на AI
                if (!isLoading) {
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
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onClose() }
    ) {
        if (webViewUrl != null) {
            // Екран WebView
            Column(modifier = Modifier.fillMaxSize().padding(top = 40.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Пошук", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Закрити", color = Color(0xFF64B5F6), modifier = Modifier.clickable { webViewUrl = null }, fontSize = 16.sp)
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

                // Чіпи
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionChip("Пошук", Color(0xFF4285F4)) {
                        if (query.isNotBlank()) {
                            webViewUrl = "https://duckduckgo.com/?q=${android.net.Uri.encode(query)}"
                        }
                    }
                    ActionChip("Переклад", Color(0xFFFF9800)) {
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
                    ActionChip("AI", Color(0xFF2196F3)) {
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
