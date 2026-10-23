package com.dynamicai.launcher.command

import android.content.Context

/**
 * Singleton-провайдер CommandExecutor.
 * Замість створення нового екземпляра у кожному місці (MainActivity, FloatingDock, WakeWord, SearchOverlay)
 * використовуємо ОДИН з applicationContext.
 * 
 * Економія: ~50 MB RAM + виправляє AppContexts leak (6 → 2).
 */
object CommandExecutorProvider {
    @Volatile private var instance: CommandExecutor? = null

    fun get(context: Context): CommandExecutor {
        return instance ?: synchronized(this) {
            instance ?: CommandExecutor(context.applicationContext).also { instance = it }
        }
    }
}
