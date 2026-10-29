package com.dynamicai.launcher.macro

data class Macro(
    val id: String,
    val name: String,
    val icon: String = "⚡",
    val colorHex: Long = 0xFF2196F3, // Синій за замовчуванням
    val trigger: String = "",        // голосові фрази через кому: «їду на роботу, режим роботи»
    val steps: List<String>,
    val delayMs: Long = 900L,        // пауза між кроками
    val stopOnError: Boolean = false // зупинити макрос, якщо крок завершився помилкою
)
