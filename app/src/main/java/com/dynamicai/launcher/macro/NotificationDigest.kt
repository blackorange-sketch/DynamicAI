package com.dynamicai.launcher.macro

import android.app.Notification
import android.content.Context

/** Короткий підсумок активних сповіщень (за додатками) — для голосу, чату та макросів. */
object NotificationDigest {

    /** Кількість активних сповіщень за пакетами (для бейджів на іконках). */
    fun counts(ctx: Context): Map<String, Int> {
        val l = MacroNotificationListener.instance ?: return emptyMap()
        return try {
            l.activeNotifications
                .filter {
                    it.packageName != ctx.packageName &&
                        it.notification.flags and Notification.FLAG_ONGOING_EVENT == 0 &&
                        it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
                }
                .groupingBy { it.packageName }.eachCount()
        } catch (_: Throwable) { emptyMap() }
    }

    fun build(ctx: Context): String {
        val l = MacroNotificationListener.instance
            ?: return if (!TriggerPermissions.hasNotificationListener(ctx))
                "Щоб я міг читати сповіщення, дайте доступ: Налаштування Android → Доступ до сповіщень → DynamicAI."
            else "Сервіс сповіщень ще підключається, спробуйте за кілька секунд."
        val list = try {
            l.activeNotifications.filter {
                it.packageName != ctx.packageName &&
                    it.notification.flags and Notification.FLAG_ONGOING_EVENT == 0 &&
                    it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
            }
        } catch (_: Throwable) { emptyList() }
        if (list.isEmpty()) return "Нових сповіщень немає."

        val groups = list.groupBy { it.packageName }.entries.sortedByDescending { e -> e.value.maxOf { it.postTime } }
        val parts = groups.take(6).map { (pkg, items) ->
            val app = AppUtils.labelFor(ctx, pkg)
            val latest = items.maxByOrNull { it.postTime }!!
            val ex = latest.notification.extras
            val title = ex?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
            val text = ex?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().trim()
            val preview = listOf(title, text).filter { it.isNotBlank() }.joinToString(": ").take(90)
            if (items.size > 1) "$app — ${items.size}" + (if (preview.isNotBlank()) ", останнє: $preview" else "")
            else "$app" + (if (preview.isNotBlank()) " — $preview" else "")
        }
        val more = groups.size - parts.size
        return "Сповіщень: ${list.size}. " + parts.joinToString(". ") + (if (more > 0) ". Ще $more додатків." else ".")
    }
}
