package com.dynamicai.launcher

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // WakeWord: старт/стоп за налаштуванням
            androidx.compose.runtime.LaunchedEffect(Unit) {
                val wakeEnabled = com.dynamicai.launcher.data.SettingsStorage(applicationContext).load().wakeWordEnabled
                if (wakeEnabled) {
                    com.dynamicai.launcher.voice.WakeWordService.start(applicationContext)
                } else {
                    com.dynamicai.launcher.voice.WakeWordService.stop(applicationContext)
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
