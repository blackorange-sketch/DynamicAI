package com.dynamicai.launcher.suggestion

import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Рядок даних месенджера в контакті. Telegram/WhatsApp/Viber самі додають у книгу контактів Android
 * «дзвінок» і «профіль» — їх відкриває ACTION_VIEW на content://…/data/<id> з потрібним MIME.
 * Так дзвінок іде саме тому користувачеві, який збережений у месенджері, без пошуку за іменем.
 */
data class ContactApp(val kind: String, val action: String, val dataId: Long, val mime: String, val pkg: String = "")  // action: call | video | chat

data class ContactInfo(
    val name: String,
    val phones: List<String>,
    val apps: List<ContactApp> = emptyList(),
    val instagram: String? = null
)

class ContactRepository(private val context: Context) {

    private fun kindOf(mime: String): String? = when {
        mime.contains("telegram") -> "telegram"
        mime.contains("whatsapp") -> "whatsapp"
        mime.contains("viber") && !mime.contains("viber_out") -> "viber"
        mime.contains("thoughtcrime") || mime.contains("signal") -> "signal"
        else -> null
    }

    private fun actionOf(mime: String): String = when {
        mime.contains("video") -> "video"
        mime.contains("call") || mime.contains("voip") -> "call"
        else -> "chat"
    }

    suspend fun loadAll(): List<ContactInfo> = withContext(Dispatchers.IO) {
        class Acc(var name: String) {
            val phones = linkedMapOf<String, String>()   // ключ — останні 9 цифр
            val apps = mutableListOf<ContactApp>()
            var insta: String? = null
        }
        val byId = linkedMapOf<Long, Acc>()
        val cr = context.contentResolver

        try {
            cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1)?.trim() ?: continue
                    val phone = c.getString(2)?.trim()?.replace("\\s".toRegex(), "")
                    if (phone.isNullOrBlank()) continue
                    val acc = byId.getOrPut(id) { Acc(name) }
                    val key = phone.filter { it.isDigit() }.takeLast(9)
                    if (key.isNotEmpty()) acc.phones.putIfAbsent(key, phone)
                    if (byId.size > 4000) break
                }
            }
        } catch (_: SecurityException) {
            return@withContext emptyList()
        }

        // Дії месенджерів (дзвінок / відеодзвінок / чат), які месенджер сам прописав у контакті
        for (withAcc in listOf(true, false)) try {
            cr.query(
                ContactsContract.Data.CONTENT_URI,
                if (withAcc) arrayOf(ContactsContract.Data._ID, ContactsContract.Data.CONTACT_ID, ContactsContract.Data.MIMETYPE, ContactsContract.RawContacts.ACCOUNT_TYPE)
                else arrayOf(ContactsContract.Data._ID, ContactsContract.Data.CONTACT_ID, ContactsContract.Data.MIMETYPE),
                "${ContactsContract.Data.MIMETYPE} LIKE ? OR ${ContactsContract.Data.MIMETYPE} LIKE ? OR " +
                    "${ContactsContract.Data.MIMETYPE} LIKE ? OR ${ContactsContract.Data.MIMETYPE} LIKE ?",
                arrayOf("%telegram%", "%whatsapp%", "%viber%", "%thoughtcrime%"),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val dataId = c.getLong(0)
                    val contactId = c.getLong(1)
                    val mime = c.getString(2) ?: continue
                    // Тип акаунта синхронізації = пакет месенджера, який створив запис (відрізняє форки Telegram тощо)
                    val accountType = if (withAcc) try { c.getString(3).orEmpty() } catch (_: Throwable) { "" } else ""
                    val acc = byId[contactId] ?: continue
                    val kind = kindOf(mime.lowercase()) ?: continue
                    val action = actionOf(mime.lowercase())
                    if (acc.apps.none { it.kind == kind && it.action == action }) acc.apps.add(ContactApp(kind, action, dataId, mime, accountType))
                }
            }
            break   // успішно — запасний варіант без account_type не потрібен
        } catch (_: Throwable) {}

        // Нік в Instagram: IM-запис або посилання/згадка в контакті
        try {
            cr.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1),
                "(${ContactsContract.Data.MIMETYPE}=? AND (${ContactsContract.Data.DATA1} LIKE ? OR ${ContactsContract.Data.DATA6} LIKE ?)) OR " +
                    "(${ContactsContract.Data.MIMETYPE} IN (?, ?) AND ${ContactsContract.Data.DATA1} LIKE ?)",
                arrayOf(
                    ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE, "%instagram%", "%instagram%",
                    ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE, "%instagram%"
                ),
                null
            )?.use { c ->
                val urlRx = Regex("instagram\\.com/([A-Za-z0-9._]+)", RegexOption.IGNORE_CASE)
                while (c.moveToNext()) {
                    val acc = byId[c.getLong(0)] ?: continue
                    if (acc.insta != null) continue
                    val mime = c.getString(1) ?: continue
                    val v = c.getString(2)?.trim() ?: continue
                    acc.insta = urlRx.find(v)?.groupValues?.get(1)
                        ?: if (mime == ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE)
                            v.removePrefix("@").takeIf { it.matches(Regex("[A-Za-z0-9._]+")) }
                        else Regex("@([A-Za-z0-9._]+)").find(v)?.groupValues?.get(1)
                }
            }
        } catch (_: Throwable) {}

        // Об'єднуємо контакти з однаковим іменем (Telegram створює власні копії)
        val merged = linkedMapOf<String, Acc>()
        for (acc in byId.values) {
            val key = acc.name.lowercase()
            val m = merged.getOrPut(key) { Acc(acc.name) }
            acc.phones.forEach { (k, v) -> m.phones.putIfAbsent(k, v) }
            acc.apps.forEach { a -> if (m.apps.none { it.kind == a.kind && it.action == a.action }) m.apps.add(a) }
            if (m.insta == null) m.insta = acc.insta
        }
        merged.values.map { ContactInfo(it.name, it.phones.values.toList(), it.apps, it.insta) }
    }
}
