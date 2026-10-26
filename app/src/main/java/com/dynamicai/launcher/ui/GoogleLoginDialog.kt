package com.dynamicai.launcher.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.dynamicai.launcher.music.GoogleAuthManager

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GoogleLoginDialog(
    context: android.content.Context,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var showWebView by remember { mutableStateOf(true) }

    if (showWebView) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Вхід у Google") },
            text = {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(500.dp),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    if (url?.contains("myaccount.google.com") == true || url?.contains("music.youtube.com") == true) {
                                        val cookie = CookieManager.getInstance().getCookie(url)
                                        if (cookie != null && (cookie.contains("SAPISID") || cookie.contains("SID"))) {
                                            GoogleAuthManager.save(context, cookie)
                                            showWebView = false
                                            onSuccess()
                                        }
                                    }
                                }
                            }
                            loadUrl("https://accounts.google.com/ServiceLogin?service=youtube")
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Скасувати") }
            }
        )
    }
}
