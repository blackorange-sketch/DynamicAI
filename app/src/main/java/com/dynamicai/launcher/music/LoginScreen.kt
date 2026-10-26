package com.dynamicai.launcher.music

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeMusicLoginScreen(
    onLoginSuccess: (cookie: String) -> Unit,
    onCancel: () -> Unit
) {
    var hasCompletedLogin by remember { mutableStateOf(false) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (url?.startsWith("https://music.youtube.com") == true && !hasCompletedLogin) {
                            val cookie = CookieManager.getInstance().getCookie(url)
                            if (cookie != null && cookie.contains("SAPISID")) {
                                hasCompletedLogin = true
                                onLoginSuccess(cookie)
                            }
                        }
                    }
                }
                loadUrl("https://music.youtube.com")
            }
        }
    )
}
