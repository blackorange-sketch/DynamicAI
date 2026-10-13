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
    val showLabels: Boolean = true,
    // Оверлей
    val overlayIconSizeDp: Int = 54,
    val overlayPosition: String = "right",   // "right" | "left"
    val overlayOffsetYPercent: Int = 50,     // 0..100, вертикальний центр дока
    val overlayHandleLengthDp: Int = 140,
    val overlayHandleAlphaPercent: Int = 55,
    val overlayAutoCollapseMs: Int = 0,      // 0 = вимкнено
    val overlayEdgePaddingDp: Int = 14,      // відступ риски від краю екрана
    val backgroundAlphaPercent: Int = 100,   // 0 = прозорий, 100 = чорний
    val wpPauseOnScreenOff: Boolean = true,
    val wpPauseOnLowBattery: Boolean = true,
    val wpFps: Int = 30,
    val showWeather: Boolean = true,
    val weatherUnit: String = "C",   // "C" | "F"
    val weatherUseGps: Boolean = true,
    val weatherLat: Float = 50.4501f,
    val weatherLon: Float = 30.5234f,
    val cityName: String = ""
)

class SettingsStorage(context: Context) {
    private val prefs = context.getSharedPreferences("dynamicai_settings", Context.MODE_PRIVATE)

    fun load() = LauncherSettings(
        iconSizeDp = prefs.getInt("iconSizeDp", 48),
        dockIconSizeDp = prefs.getInt("dockIconSizeDp", 48),
        fontSizeSp = prefs.getInt("fontSizeSp", 18),
        menuIconSizeDp = prefs.getInt("menuIconSizeDp", 56),
        showSearchBar = prefs.getBoolean("showSearchBar", true),
        showRecentButton = prefs.getBoolean("showRecentButton", true),
        useLlm = prefs.getBoolean("useLlm", true),
        showLabels = prefs.getBoolean("showLabels", true),
        overlayIconSizeDp = prefs.getInt("overlayIconSizeDp", 54),
        overlayPosition = prefs.getString("overlayPosition", "right") ?: "right",
        overlayOffsetYPercent = prefs.getInt("overlayOffsetYPercent", 50),
        overlayHandleLengthDp = prefs.getInt("overlayHandleLengthDp", 140),
        overlayHandleAlphaPercent = prefs.getInt("overlayHandleAlphaPercent", 55),
        overlayAutoCollapseMs = prefs.getInt("overlayAutoCollapseMs", 0),
        overlayEdgePaddingDp = prefs.getInt("overlayEdgePaddingDp", 14),
        backgroundAlphaPercent = prefs.getInt("backgroundAlphaPercent", 100),
        wpPauseOnScreenOff = prefs.getBoolean("wpPauseOnScreenOff", true),
        wpPauseOnLowBattery = prefs.getBoolean("wpPauseOnLowBattery", true),
        wpFps = prefs.getInt("wpFps", 30),
        showWeather = prefs.getBoolean("showWeather", true),
        weatherUnit = prefs.getString("weatherUnit", "C") ?: "C",
        weatherUseGps = prefs.getBoolean("weatherUseGps", true),
        weatherLat = prefs.getFloat("weatherLat", 50.4501f),
        weatherLon = prefs.getFloat("weatherLon", 30.5234f),
        cityName = prefs.getString("cityName", "") ?: ""
    )

    fun save(s: LauncherSettings) {
        prefs.edit()
            .putInt("iconSizeDp", s.iconSizeDp)
            .putInt("dockIconSizeDp", s.dockIconSizeDp)
            .putInt("fontSizeSp", s.fontSizeSp)
            .putInt("menuIconSizeDp", s.menuIconSizeDp)
            .putBoolean("showSearchBar", s.showSearchBar)
            .putBoolean("showRecentButton", s.showRecentButton)
            .putBoolean("useLlm", s.useLlm)
            .putBoolean("showLabels", s.showLabels)
            .putInt("overlayIconSizeDp", s.overlayIconSizeDp)
            .putString("overlayPosition", s.overlayPosition)
            .putInt("overlayOffsetYPercent", s.overlayOffsetYPercent)
            .putInt("overlayHandleLengthDp", s.overlayHandleLengthDp)
            .putInt("overlayHandleAlphaPercent", s.overlayHandleAlphaPercent)
            .putInt("overlayAutoCollapseMs", s.overlayAutoCollapseMs)
            .putInt("overlayEdgePaddingDp", s.overlayEdgePaddingDp)
            .putInt("backgroundAlphaPercent", s.backgroundAlphaPercent)
            .putBoolean("wpPauseOnScreenOff", s.wpPauseOnScreenOff)
            .putBoolean("wpPauseOnLowBattery", s.wpPauseOnLowBattery)
            .putInt("wpFps", s.wpFps)
            .putBoolean("showWeather", s.showWeather)
            .putString("weatherUnit", s.weatherUnit)
            .putBoolean("weatherUseGps", s.weatherUseGps)
            .putFloat("weatherLat", s.weatherLat)
            .putFloat("weatherLon", s.weatherLon)
            .putString("cityName", s.cityName)
            .putLong("updatedAt", System.currentTimeMillis())
            .commit()
    }
}
