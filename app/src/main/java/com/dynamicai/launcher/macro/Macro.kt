package com.dynamicai.launcher.macro

data class Macro(
    val id: String,
    val name: String,
    val trigger: String = "",
    val steps: List<String>,
    val delayMs: Long = 900L
)
