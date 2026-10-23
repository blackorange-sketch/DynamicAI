package com.dynamicai.launcher

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

import com.dynamicai.launcher.voice.WakeWordService

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.platform.LocalContext
import com.dynamicai.launcher.ui.HomeScreen

data class HubEvent(val action: String, val query: String, val id: Long)

class MainActivity : ComponentActivity() {
    private val vm: AppListViewModel by viewModels()
    private var hubEvent = mutableStateOf<HubEvent?>(null)

    override fun onResume() {
        super.onResume()
        try { com.dynamicai.launcher.overlay.FloatingDockService.hide(this) } catch (_: Exception) {}
        try { vm.refreshIfStale() } catch (_: Throwable) {}
    }

    override fun onPause() {
        super.onPause()
        try { com.dynamicai.launcher.overlay.FloatingDockService.show(this) } catch (_: Exception) {}
    }

    private var aiQuery: String? = null

    private fun handleHubIntent(intent: android.content.Intent?) {
        val action = intent?.getStringExtra("hub_action") ?: return
        val q = intent.getStringExtra("hub_query") ?: ""
        hubEvent.value = HubEvent(action, q, System.currentTimeMillis())
    }

    private fun handleAssistIntent(intent: android.content.Intent?) {
        val action = intent?.action ?: return
        if (action == android.content.Intent.ACTION_ASSIST ||
            action == "android.intent.action.VOICE_COMMAND") {
            // Невелика затримка щоб activity встигла стати foreground
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    com.dynamicai.launcher.overlay.AiHubOverlay.show(this@MainActivity, vm)
                } catch (_: Throwable) {}
            }, 300)
        }
        
        // Читаємо запит від AI Hub
        val q = intent?.getStringExtra("ai_query")
        if (!q.isNullOrBlank()) {
            aiQuery = q
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleAssistIntent(intent)
        handleHubIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAssistIntent(intent)
        handleHubIntent(intent)
        
        // Детектор клавіатури → broadcast для FloatingDockService
        window.decorView.viewTreeObserver.addOnGlobalLayoutListener {
            try {
                val rect = android.graphics.Rect()
                window.decorView.getWindowVisibleDisplayFrame(rect)
                val screenHeight = resources.displayMetrics.heightPixels
                val keypadHeight = screenHeight - rect.bottom
                val isOpen = keypadHeight > screenHeight * 0.15
                val intent = android.content.Intent("com.dynamicai.KEYBOARD_STATE_CHANGED")
                    .setPackage(packageName)
                    .putExtra("is_open", isOpen)
                sendBroadcast(intent)
            } catch (_: Throwable) {}
        }
        
        setContent {
            // WakeWord: запит дозволу мікрофона + старт сервісу, якщо увімкнено
            val permLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    val on = com.dynamicai.launcher.data.SettingsStorage(applicationContext).load().wakeWordEnabled
                    if (on) {
                    com.dynamicai.launcher.voice.WakeWordService.start(applicationContext)
                }
                }
            }
            val appCtx = applicationContext
            androidx.compose.runtime.LaunchedEffect(Unit) {
                val granted = ContextCompat.checkSelfPermission(
                    appCtx, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    val on = com.dynamicai.launcher.data.SettingsStorage(appCtx).load().wakeWordEnabled
                    if (on) com.dynamicai.launcher.voice.WakeWordService.start(appCtx)
                }
            }

            val ctx = LocalContext.current
            val baseScheme = if (android.os.Build.VERSION.SDK_INT >= 31) {
                dynamicDarkColorScheme(ctx)
            } else {
                darkColorScheme()
            }
            val colorScheme = baseScheme.copy(
                background = androidx.compose.ui.graphics.Color.Transparent,
                surface = androidx.compose.ui.graphics.Color.Transparent
            )
            MaterialTheme(colorScheme = colorScheme) {
                HomeScreen(vm = vm, hubEvent = hubEvent.value)
            }
        }
    }
}
