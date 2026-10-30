package com.dynamicai.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope

/**
 * Як LaunchedEffect, але корутина працює лише коли екран видимий (STARTED).
 * Циклічні опитування в LaunchedEffect без цього продовжують крутитись, поки лаунчер у фоні, і гріють батарею.
 */
@Composable
fun LaunchedWhileStarted(key: Any? = Unit, block: suspend CoroutineScope.() -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(key, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { block() }
    }
}
