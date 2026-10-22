package com.dynamicai.launcher.widget

data class PlacedWidget(
    val id: String,
    val type: String,  // "weather_clock" | "notes"
    val page: Int = 0
)
