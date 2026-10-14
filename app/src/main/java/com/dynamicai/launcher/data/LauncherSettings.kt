package com.dynamicai.launcher.data

import android.content.Context
import androidx.core.content.edit

data class LauncherSettings(
    val iconSizeDp: Int = 48,
    val dockIconSizeDp: Int = 48,
    val fontSizeSp: Int = 18,
    val menuIconSizeDp: Int = 56,
    val showSearchBar: Boolean = true,
    val showRecentButton: Boolean = true,
    val showLabels: Boolean = true,
    val backgroundAlphaPercent: Int = 30,

    val useLlm: Boolean = true,
    val useTts: Boolean = true,
    val ttsSpeed: Float = 1.0f,
    val ttsPitch: Float = 1.0f,

    val wakeWordEnabled: Boolean = false,
    val wakeWord: String = "hey",

    val showWeather: Boolean = true,
    val cityName: String = "",
    val weatherUseGps: Boolean = false,
    val weatherLat: Double = 0.0,
    val weatherLon: Double = 0.0,
    val weatherUnit: String = "celsius",

    val overlayPosition: String = "right",
    val overlayOffsetYPercent: Int = 50,
    val overlayIconSizeDp: Int = 40,
    val overlayHandleLengthDp: Int = 40,
    val overlayHandleAlphaPercent: Int = 40,
    val overlayEdgePaddingDp: Int = 8,

    val wpFps: Int = 30,
    val wpPauseOnScreenOff: Boolean = true,
    val wpPauseOnLowBattery: Boolean = true,
)

class SettingsStorage(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): LauncherSettings = LauncherSettings(
        iconSizeDp = prefs.getInt(KEY_ICON_SIZE_DP, 48),
        dockIconSizeDp = prefs.getInt(KEY_DOCK_ICON_SIZE_DP, 48),
        fontSizeSp = prefs.getInt(KEY_FONT_SIZE_SP, 18),
        menuIconSizeDp = prefs.getInt(KEY_MENU_ICON_SIZE_DP, 56),
        showSearchBar = prefs.getBoolean(KEY_SHOW_SEARCH_BAR, true),
        showRecentButton = prefs.getBoolean(KEY_SHOW_RECENT_BUTTON, true),
        showLabels = prefs.getBoolean(KEY_SHOW_LABELS, true),
        backgroundAlphaPercent = prefs.getInt(KEY_BACKGROUND_ALPHA, 30),
        useLlm = prefs.getBoolean(KEY_USE_LLM, true),
        useTts = prefs.getBoolean(KEY_USE_TTS, true),
        ttsSpeed = prefs.getFloat(KEY_TTS_SPEED, 1.0f),
        ttsPitch = prefs.getFloat(KEY_TTS_PITCH, 1.0f),
        wakeWordEnabled = prefs.getBoolean(KEY_WAKE_WORD_ENABLED, false),
        wakeWord = prefs.getString(KEY_WAKE_WORD, "hey") ?: "hey",
        showWeather = prefs.getBoolean(KEY_SHOW_WEATHER, true),
        cityName = prefs.getString(KEY_CITY_NAME, "") ?: "",
        weatherUseGps = prefs.getBoolean(KEY_WEATHER_USE_GPS, false),
        weatherLat = prefs.getString(KEY_WEATHER_LAT, null)?.toDoubleOrNull() ?: 0.0,
        weatherLon = prefs.getString(KEY_WEATHER_LON, null)?.toDoubleOrNull() ?: 0.0,
        weatherUnit = prefs.getString(KEY_WEATHER_UNIT, "celsius") ?: "celsius",
        overlayPosition = prefs.getString(KEY_OVERLAY_POSITION, "right") ?: "right",
        overlayOffsetYPercent = prefs.getInt(KEY_OVERLAY_OFFSET_Y, 50),
        overlayIconSizeDp = prefs.getInt(KEY_OVERLAY_ICON_SIZE, 40),
        overlayHandleLengthDp = prefs.getInt(KEY_OVERLAY_HANDLE_LEN, 40),
        overlayHandleAlphaPercent = prefs.getInt(KEY_OVERLAY_HANDLE_ALPHA, 40),
        overlayEdgePaddingDp = prefs.getInt(KEY_OVERLAY_EDGE_PADDING, 8),
        wpFps = prefs.getInt(KEY_WP_FPS, 30),
        wpPauseOnScreenOff = prefs.getBoolean(KEY_WP_PAUSE_SCREEN_OFF, true),
        wpPauseOnLowBattery = prefs.getBoolean(KEY_WP_PAUSE_LOW_BATTERY, true),
    )

    fun save(settings: LauncherSettings) {
        prefs.edit {
            putInt(KEY_ICON_SIZE_DP, settings.iconSizeDp)
            putInt(KEY_DOCK_ICON_SIZE_DP, settings.dockIconSizeDp)
            putInt(KEY_FONT_SIZE_SP, settings.fontSizeSp)
            putInt(KEY_MENU_ICON_SIZE_DP, settings.menuIconSizeDp)
            putBoolean(KEY_SHOW_SEARCH_BAR, settings.showSearchBar)
            putBoolean(KEY_SHOW_RECENT_BUTTON, settings.showRecentButton)
            putBoolean(KEY_SHOW_LABELS, settings.showLabels)
            putInt(KEY_BACKGROUND_ALPHA, settings.backgroundAlphaPercent)
            putBoolean(KEY_USE_LLM, settings.useLlm)
            putBoolean(KEY_USE_TTS, settings.useTts)
            putFloat(KEY_TTS_SPEED, settings.ttsSpeed)
            putFloat(KEY_TTS_PITCH, settings.ttsPitch)
            putBoolean(KEY_WAKE_WORD_ENABLED, settings.wakeWordEnabled)
            putString(KEY_WAKE_WORD, settings.wakeWord)
            putBoolean(KEY_SHOW_WEATHER, settings.showWeather)
            putString(KEY_CITY_NAME, settings.cityName)
            putBoolean(KEY_WEATHER_USE_GPS, settings.weatherUseGps)
            putString(KEY_WEATHER_LAT, settings.weatherLat.toString())
            putString(KEY_WEATHER_LON, settings.weatherLon.toString())
            putString(KEY_WEATHER_UNIT, settings.weatherUnit)
            putString(KEY_OVERLAY_POSITION, settings.overlayPosition)
            putInt(KEY_OVERLAY_OFFSET_Y, settings.overlayOffsetYPercent)
            putInt(KEY_OVERLAY_ICON_SIZE, settings.overlayIconSizeDp)
            putInt(KEY_OVERLAY_HANDLE_LEN, settings.overlayHandleLengthDp)
            putInt(KEY_OVERLAY_HANDLE_ALPHA, settings.overlayHandleAlphaPercent)
            putInt(KEY_OVERLAY_EDGE_PADDING, settings.overlayEdgePaddingDp)
            putInt(KEY_WP_FPS, settings.wpFps)
            putBoolean(KEY_WP_PAUSE_SCREEN_OFF, settings.wpPauseOnScreenOff)
            putBoolean(KEY_WP_PAUSE_LOW_BATTERY, settings.wpPauseOnLowBattery)
        }
    }

    companion object {
        private const val PREFS_NAME = "launcher_settings"

        private const val KEY_ICON_SIZE_DP = "iconSizeDp"
        private const val KEY_DOCK_ICON_SIZE_DP = "dockIconSizeDp"
        private const val KEY_FONT_SIZE_SP = "fontSizeSp"
        private const val KEY_MENU_ICON_SIZE_DP = "menuIconSizeDp"
        private const val KEY_SHOW_SEARCH_BAR = "showSearchBar"
        private const val KEY_SHOW_RECENT_BUTTON = "showRecentButton"
        private const val KEY_SHOW_LABELS = "showLabels"
        private const val KEY_BACKGROUND_ALPHA = "backgroundAlphaPercent"
        private const val KEY_USE_LLM = "useLlm"
        private const val KEY_USE_TTS = "useTts"
        private const val KEY_TTS_SPEED = "ttsSpeed"
        private const val KEY_TTS_PITCH = "ttsPitch"
        private const val KEY_WAKE_WORD_ENABLED = "wakeWordEnabled"
        private const val KEY_WAKE_WORD = "wakeWord"
        private const val KEY_SHOW_WEATHER = "showWeather"
        private const val KEY_CITY_NAME = "cityName"
        private const val KEY_WEATHER_USE_GPS = "weatherUseGps"
        private const val KEY_WEATHER_LAT = "weatherLat"
        private const val KEY_WEATHER_LON = "weatherLon"
        private const val KEY_WEATHER_UNIT = "weatherUnit"
        private const val KEY_OVERLAY_POSITION = "overlayPosition"
        private const val KEY_OVERLAY_OFFSET_Y = "overlayOffsetYPercent"
        private const val KEY_OVERLAY_ICON_SIZE = "overlayIconSizeDp"
        private const val KEY_OVERLAY_HANDLE_LEN = "overlayHandleLengthDp"
        private const val KEY_OVERLAY_HANDLE_ALPHA = "overlayHandleAlphaPercent"
        private const val KEY_OVERLAY_EDGE_PADDING = "overlayEdgePaddingDp"
        private const val KEY_WP_FPS = "wpFps"
        private const val KEY_WP_PAUSE_SCREEN_OFF = "wpPauseOnScreenOff"
        private const val KEY_WP_PAUSE_LOW_BATTERY = "wpPauseOnLowBattery"
    }
}
