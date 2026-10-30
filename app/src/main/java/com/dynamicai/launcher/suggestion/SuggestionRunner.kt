package com.dynamicai.launcher.suggestion

import android.content.Context
import android.content.Intent
import com.dynamicai.launcher.macro.MacroLauncher
import com.dynamicai.launcher.macro.MacroStorage
import com.dynamicai.launcher.overlay.ChatHistoryManager

/** Виконання універсальних результатів пошуку (спільне для головного екрана та пошукового оверлея). */
object SuggestionRunner {
    /** Повертає true, якщо дію виконано тут; false для нотатки (її відкриває екран, що має доступ до UI). */
    fun run(ctx: Context, s: Suggestion.Extra): Boolean {
        val app = ctx.applicationContext
        when (s.kind) {
            "macro" -> MacroStorage(app).get().firstOrNull { it.id == s.payload }?.let { MacroLauncher.run(app, it) }
            "chat" -> {
                ChatHistoryManager.init(app)
                ChatHistoryManager.restore(s.payload)
                app.startActivity(Intent(app, com.dynamicai.launcher.VoiceChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            "ask" -> app.startActivity(
                Intent(app, com.dynamicai.launcher.VoiceChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("ask", s.payload)
            )
            "note" -> return false
        }
        return true
    }
}
