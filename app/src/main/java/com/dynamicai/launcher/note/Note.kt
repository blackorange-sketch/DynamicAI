package com.dynamicai.launcher.note

data class Note(
    val id: String,
    val text: String,
    val createdAt: Long,
    val done: Boolean = false,
    val reminderAt: Long? = null
)
