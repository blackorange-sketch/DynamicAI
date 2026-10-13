package com.dynamicai.launcher.data

import android.content.Context

data class LauncherSettings(
    val iconSizeDp: Int = 48,
    val dockIconSizeDp: Int = 48,
    val fontSizeSp: Int = 18,
    val showLabels: Boolean = true,
    // Оверлей
    val overlayIconSizeDp: Int = 54,
    val overlayPosition: String = "right",   // "right" | "left"
    val overlayOffsetYPercent: Int = 50,     // 0..100, вертикальний центр дока
    val overlayHandleLengthDp: Int = 140,
    val overlayHandleAlphaPercent: Int = 55,
    val overlayAutoCollapseMs: Int = 0       // 0 = вимкнено
)

class SettingsStorage(context: Context) {
    private val prefs = context.getSharedPreferences("dynamicai_settings", Context.MODE_PRIVATE)

    fun load() = LauncherSettings(
        iconSizeDp = prefs.getInt("iconSizeDp", 48),
        dockIconSizeDp = prefs.getInt("dockIconSizeDp", 48),
        fontSizeSp = prefs.getInt("fontSizeSp", 18),
        showLabels = prefs.getBoolean("showLabels", true),
        overlayIconSizeDp = prefs.getInt("overlayIconSizeDp", 54),
        overlayPosition = prefs.getString("overlayPosition", "right") ?: "right",
        overlayOffsetYPercent = prefs.getInt("overlayOffsetYPercent", 50),
        overlayHandleLengthDp = prefs.getInt("overlayHandleLengthDp", 140),
        overlayHandleAlphaPercent = prefs.getInt("overlayHandleAlphaPercent", 55),
        overlayAutoCollapseMs = prefs.getInt("overlayAutoCollapseMs", 0)
    )

    fun save(s: LauncherSettings) {
        prefs.edit()
            .putInt("iconSizeDp", s.iconSizeDp)
            .putInt("dockIconSizeDp", s.dockIconSizeDp)
            .putInt("fontSizeSp", s.fontSizeSp)
            .putBoolean("showLabels", s.showLabels)
            .putInt("overlayIconSizeDp", s.overlayIconSizeDp)
            .putString("overlayPosition", s.overlayPosition)
            .putInt("overlayOffsetYPercent", s.overlayOffsetYPercent)
            .putInt("overlayHandleLengthDp", s.overlayHandleLengthDp)
            .putInt("overlayHandleAlphaPercent", s.overlayHandleAlphaPercent)
            .putInt("overlayAutoCollapseMs", s.overlayAutoCollapseMs)
            .apply()
        // Повідомляємо сервіс про зміну
        prefs.edit().putLong("updatedAt", System.currentTimeMillis()).apply()
    }
}
