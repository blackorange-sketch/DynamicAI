package com.dynamicai.launcher.data

import android.content.Context

data class LauncherSettings(
    val iconSizeDp: Int = 48,
    val dockIconSizeDp: Int = 48,
    val fontSizeSp: Int = 18,
    val menuIconSizeDp: Int = 56,
    val showSearchBar: Boolean = true,
    val showRecentButton: Boolean = true,
    val useLlm: Boolean = true,
    val useTts: Boolean = true,
    val wakeWordEnabled: Boolean = false,
