package com.dynamicai.launcher.macro

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Передає вхідні сповіщення інших додатків у тригери макросів («notification:пакет:текст»). */
class MacroNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            if (sbn.packageName == packageName) return
            val n = sbn.notification ?: return
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
            if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
            val ex = n.extras
            val title = ex?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = ex?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            MacroDispatcher.fire(
                this, "notification",
                mapOf("pkg" to sbn.packageName, "app" to AppUtils.labelFor(this, sbn.packageName), "title" to title, "text" to text)
            )
        } catch (_: Throwable) {}
    }
}
