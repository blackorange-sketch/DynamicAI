package com.dynamicai.launcher.macro

data class Macro(
    val id: String,
    val name: String,
    val icon: String = "⚡",
    val colorHex: Long = 0xFF2196F3, // Синій за замовчуванням
    val trigger: String = "",        // голосові фрази через кому: «їду на роботу, режим роботи»
    val steps: List<String>,
    val delayMs: Long = 900L,        // пауза між кроками
    val stopOnError: Boolean = false, // зупинити макрос, якщо крок завершився помилкою
    val triggers: List<String> = emptyList(), // події-автозапуски: charging_start, bt_connected:Назва, time:07:30:12345 …
    val constraints: String = "",     // умови через кому: усі мають бути істинні, щоб автозапуск спрацював
    val enabled: Boolean = true       // автозапуск увімкнено
)
