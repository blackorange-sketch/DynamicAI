package com.dynamicai.launcher

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

import com.dynamicai.launcher.voice.WakeWordService

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.platform.LocalContext
import com.dynamicai.launcher.ui.HomeScreen

class MainActivity : ComponentActivity() {

    override fun onResume() {
        super.onResume()
        try { com.dynamicai.launcher.overlay.FloatingDockService.hide(this) } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        try { com.dynamicai.launcher.overlay.FloatingDockService.show(this) } catch (_: Exception) {}
    }

    private fun handleAssistIntent(intent: android.content.Intent?) {
        val action = intent?.action ?: return
        if (action == android.content.Intent.ACTION_ASSIST ||
            action == "android.intent.action.VOICE_COMMAND") {
            // Невелика затримка щоб activity встигла стати foreground
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    com.dynamicai.launcher.overlay.AiHubOverlay.show(applicationContext)
                } catch (_: Throwable) {}
            }, 300)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleAssistIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAssistIntent(intent)
        setContent {
            // WakeWord: запит дозволу мікрофона + старт сервісу, якщо увімкнено
            val permLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    val on = com.dynamicai.launcher.data.SettingsStorage(applicationContext).load().wakeWordEnabled
                    if (on) {
                    com.dynamicai.launcher.voice.WakeWordService.start(applicationContext)
                    try {
                        applicationContext.startService(
                            android.content.Intent(applicationContext, com.dynamicai.launcher.overlay.CornerGestureService::class.java)
                        )
                    } catch (_: Throwable) {}
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
                HomeScreen()
            }
        }
    }
}
