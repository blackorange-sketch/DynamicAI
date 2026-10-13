package com.dynamicai.launcher.data

import android.content.Context

data class LauncherSettings(
    val iconSizeDp: Int = 48,
    val dockIconSizeDp: Int = 48,
    val fontSizeSp: Int = 18,
    val showLabels: Boolean = true
)

class SettingsStorage(context: Context) {
    private val prefs = context.getSharedPreferences("dynamicai_settings", Context.MODE_PRIVATE)

    fun load() = LauncherSettings(
        iconSizeDp = prefs.getInt("iconSizeDp", 48),
        dockIconSizeDp = prefs.getInt("dockIconSizeDp", 48),
        fontSizeSp = prefs.getInt("fontSizeSp", 18),
        showLabels = prefs.getBoolean("showLabels", true)
    )

    fun save(s: LauncherSettings) {
        prefs.edit()
            .putInt("iconSizeDp", s.iconSizeDp)
            .putInt("dockIconSizeDp", s.dockIconSizeDp)
            .putInt("fontSizeSp", s.fontSizeSp)
            .putBoolean("showLabels", s.showLabels)
            .apply()
    }
}
