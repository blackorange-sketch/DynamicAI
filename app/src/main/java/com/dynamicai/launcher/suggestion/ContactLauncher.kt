package com.dynamicai.launcher.suggestion

import android.content.Context
import android.content.Intent
import com.dynamicai.launcher.command.Command
import com.dynamicai.launcher.command.CommandExecutor
import com.dynamicai.launcher.command.CommandResult
import com.dynamicai.launcher.model.AppInfo

/** Виконання рядка контакту (дзвінок, месенджери, Instagram). Спільне для головного екрана і оверлея з плаваючого дока. */
object ContactLauncher {
    fun normalizePhone(raw: String): String {
        val d = raw.filter { it.isDigit() }
        return when {
            raw.trim().startsWith("+") -> d
            d.length == 10 && d.startsWith("0") -> "38$d"
            d.length == 11 && d.startsWith("80") -> "3$d"
            else -> d
        }
    }

    private fun startView(app: Context, intent: Intent): Boolean = try {
        app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: Throwable) { false }

    fun run(
        ctx: Context, s: Suggestion.Contact, contacts: List<ContactInfo>,
        apps: List<AppInfo>, executor: CommandExecutor
    ): CommandResult {
        val app = ctx.applicationContext
        when (s.actionType) {
            ContactAction.APP_DATA -> {
                val parts = s.target.split('|', limit = 3)
                val id = parts.getOrNull(0)?.toLongOrNull()
                val mime = parts.getOrNull(1)
                val accountPkg = parts.getOrNull(2).orEmpty()
                val pm = app.packageManager
                fun viewIntent() = Intent(Intent.ACTION_VIEW).setDataAndType(
                    android.content.ContentUris.withAppendedId(android.provider.ContactsContract.Data.CONTENT_URI, id ?: 0L), mime ?: ""
                )
                val known = mapOf(
                    "telegram" to listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram"),
                    "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
                    "viber" to listOf("com.viber.voip"), "signal" to listOf("org.thoughtcrime.securesms")
                )
                val kindPkgs = known.entries.firstOrNull { (_, v) -> accountPkg in v }?.value
                    ?: known.entries.firstOrNull { (k, _) -> s.actionLabel.startsWith(k, ignoreCase = true) }?.value.orEmpty()
                val candidates = (listOf(accountPkg) + kindPkgs).filter { it.isNotBlank() }.distinct().filter {
                    try { pm.getPackageInfo(it, 0); true } catch (_: Throwable) { false }
                }
                var ok = false
                for (p in candidates) {
                    val i = viewIntent().setPackage(p)
                    if (id != null && mime != null && i.resolveActivity(pm) != null && startView(app, i)) { ok = true; break }
                }
                if (!ok && s.actionLabel.startsWith("Telegram", ignoreCase = true)) {
                    val phone = contacts.firstOrNull { it.name == s.name }?.phones?.firstOrNull()
                    val tgPkg = candidates.firstOrNull()
                    if (phone != null && tgPkg != null) {
                        ok = startView(
                            app,
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("tg://resolve?phone=${normalizePhone(phone)}")).setPackage(tgPkg)
                        )
                    }
                }
                return if (ok) CommandResult.Success(s.actionLabel)
                else CommandResult.Error("Не вдалося відкрити: ${s.actionLabel}")
            }
            ContactAction.INSTAGRAM, ContactAction.INSTAGRAM_CALL -> {
                val isHandle = s.target.matches(Regex("[A-Za-z0-9._]+"))
                val call = s.actionType == ContactAction.INSTAGRAM_CALL
                val msg = if (isHandle) {
                    startView(
                        app,
                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://ig.me/m/${s.target}")).setPackage("com.instagram.android")
                    )
                    if (call) com.dynamicai.launcher.llm.MessengerCaller(app).pressInstagramCall()
                    else "Відкриваю чат @${s.target}"
                } else if (call) com.dynamicai.launcher.llm.MessengerCaller(app).callInInstagram(s.target)
                else return executor.execute(Command.SearchInApp("insta", s.target), apps)
                return CommandResult.Success(msg)
            }
            else -> {}
        }
        val cmd = when (s.actionType) {
            ContactAction.CALL -> Command.Call(s.target)
            ContactAction.SMS -> Command.DeepLink("sms", s.target)
            ContactAction.TELEGRAM -> Command.DeepLink("telegram", normalizePhone(s.target))
            ContactAction.WHATSAPP -> Command.DeepLink("whatsapp", normalizePhone(s.target))
            ContactAction.VIBER -> Command.DeepLink("viber", "+" + normalizePhone(s.target))
            ContactAction.SIGNAL -> Command.DeepLink("signal", s.target)
            ContactAction.EMAIL -> Command.DeepLink("email", s.target)
            else -> return CommandResult.Success(s.actionLabel)
        }
        return executor.execute(cmd, apps)
    }
}
