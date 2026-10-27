package com.dynamicai.launcher.macro

data class Macro(
    val id: String,
    val name: String,
    val icon: String = "⚡",
    val colorHex: Long = 0xFF2196F3, // Синій за замовчуванням
    val trigger: String = "",
    val steps: List<String>,
    val delayMs: Long = 900L
)
