package com.dynamicai.launcher.macro

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dynamicai.launcher.overlay.IslandHub
import com.dynamicai.launcher.suggestion.TelegramIndex

/** Передає вхідні сповіщення інших додатків у тригери макросів («notification:пакет:текст»). */
class MacroNotificationListener : NotificationListenerService() {
    companion object {
        @Volatile var instance: MacroNotificationListener? = null
    }

    override fun onListenerConnected() {
        instance = this
        try { IslandHub.syncAll(this) } catch (_: Throwable) {}
    }
    override fun onListenerDisconnected() { if (instance === this) instance = null }
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        try { IslandHub.onRemoved(sbn) } catch (_: Throwable) {}
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Острівець отримує все (і постійні — таймери, дзвінки), фільтрує сам
        try { IslandHub.onPosted(this, sbn) } catch (_: Throwable) {}
        try {
            if (sbn.packageName == packageName) return
            val n = sbn.notification ?: return
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
            if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
            val ex = n.extras
            val title = ex?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = ex?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            if (TelegramIndex.isTelegram(sbn.packageName)) {
                val conv = ex?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
                val name = (conv?.takeIf { it.isNotBlank() } ?: title)
                val generic = Regex("\\d+\\s+(нов|new|повід|messag)", RegexOption.IGNORE_CASE).containsMatchIn(name)
                if (!generic) TelegramIndex.record(this, name, sbn.packageName, n.shortcutId)
            }
            MacroDispatcher.fire(
                this, "notification",
                mapOf("pkg" to sbn.packageName, "app" to AppUtils.labelFor(this, sbn.packageName), "title" to title, "text" to text)
            )
        } catch (_: Throwable) {}
    }
}
