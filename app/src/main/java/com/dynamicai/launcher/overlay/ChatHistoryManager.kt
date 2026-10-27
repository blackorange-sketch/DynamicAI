package com.dynamicai.launcher.overlay

import androidx.compose.runtime.mutableStateListOf

data class ChatMessage(val role: String, val content: String)

object ChatHistoryManager {
    val history = mutableStateListOf<ChatMessage>()
    
    fun addMessage(role: String, content: String) {
        history.add(ChatMessage(role, content))
        // Обмежуємо історію 50 повідомленнями, щоб не жерти пам'ять
        if (history.size > 50) history.removeAt(0)
    }
    
    fun clear() {
        history.clear()
    }
}
