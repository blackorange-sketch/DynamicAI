package com.dynamicai.launcher.widget

data class PlacedWidget(
    val id: String,
    val type: String,
    val page: Int = 0,
    val size: String = "medium",  // "small" | "medium" | "large"
    val group: String = ""        // однаковий group на одній сторінці = стек віджетів
)
