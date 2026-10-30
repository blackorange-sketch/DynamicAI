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
            "calc" -> {
                val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("result", s.payload))
                android.widget.Toast.makeText(app, "Скопійовано: ${s.payload}", android.widget.Toast.LENGTH_SHORT).show()
            }
            "web" -> try {
                app.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(s.payload)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Throwable) {}
            "file" -> try {
                val uri = android.net.Uri.parse(s.payload)
                val mime = app.contentResolver.getType(uri) ?: "*/*"
                app.startActivity(
                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
            } catch (_: Throwable) {}
            "tgchat" -> {
                val parts = s.payload.split('|', limit = 2)
                TelegramIndex.open(app, parts[0], parts.getOrElse(1) { "" })
            }
            "note" -> return false
        }
        return true
    }
}
