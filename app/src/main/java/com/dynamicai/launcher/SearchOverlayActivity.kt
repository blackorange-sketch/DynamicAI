package com.dynamicai.launcher

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.command.Command
import com.dynamicai.launcher.command.CommandExecutor
import com.dynamicai.launcher.command.CommandParser
import com.dynamicai.launcher.command.CommandResult
import com.dynamicai.launcher.data.AppRepository
import com.dynamicai.launcher.model.AppInfo
import kotlinx.coroutines.launch

class SearchOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Прозорий фон для актівності
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0.6f)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                OverlaySearchScreen(onClose = { finish() })
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Закриваємо при втраті фокусу (якщо відкрився інший застосунок)
        if (!isFinishing) finish()
    }
}

@Composable
private fun OverlaySearchScreen(onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    val parser = remember { CommandParser() }
    val executor = remember { CommandExecutor(ctx) }
    val repo = remember { AppRepository(ctx) }

    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var response by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        apps = try { repo.loadInstalledApps() } catch (_: Throwable) { emptyList() }
        kotlinx.coroutines.delay(100)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    fun submit(text: String) {
        if (text.isBlank()) return
        val cmd = parser.parse(text)
        val result = executor.execute(cmd, apps)
        when (result) {
            is CommandResult.Success -> {
                onClose()
            }
            is CommandResult.Error -> response = result.message
            is CommandResult.ChooseApp -> {
                // Обираємо перший
                val app = result.candidates.firstOrNull()
                if (app != null) {
                    executor.launchApp(app)
                    onClose()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .pointerInput(Unit) {
                detectTapGestures { onClose() }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF01A1A1A))
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            // Поле пошуку
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            response = null
                        },
                        textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                        cursorBrush = SolidColor(Color.White),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit(query) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp)
                            .focusRequester(focusRequester),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text("Команда або пошук…", color = Color.Gray, fontSize = 18.sp)
                            }
                            inner()
                        }
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "Закрити", tint = Color.White)
                }
            }

            if (response != null) {
                Text(
                    response!!,
                    color = Color(0xFF64B5F6),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Підказки — топ-5 застосунків
            if (query.isNotBlank()) {
                val matches = remember(query, apps) {
                    apps.filter { it.label.contains(query, ignoreCase = true) }.take(5)
                }
                if (matches.isNotEmpty()) {
                    androidx.compose.material3.HorizontalDivider(color = Color.DarkGray)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(matches, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(app.packageName) {
                                        detectTapGestures {
                                            executor.launchApp(app)
                                            onClose()
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Icon(
                                    bitmap = androidx.core.graphics.drawable.toBitmap(app.icon, 96, 96).asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = Color.Unspecified
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(app.label, color = Color.White, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
