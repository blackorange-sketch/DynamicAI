package com.dynamicai.launcher.suggestion

import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactInfo(
    val name: String,
    val phones: List<String>
)

class ContactRepository(private val context: Context) {

    suspend fun loadAll(): List<ContactInfo> = withContext(Dispatchers.IO) {
        val byName = linkedMapOf<String, MutableList<String>>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null, null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { c ->
                val nameIdx = c.getColumnIndex(projection[0])
                val phoneIdx = c.getColumnIndex(projection[1])
                while (c.moveToNext()) {
                    val name = c.getString(nameIdx)?.trim() ?: continue
                    val phone = c.getString(phoneIdx)?.trim()?.replace("\\s".toRegex(), "")
                    if (phone.isNullOrBlank()) continue
                    val list = byName.getOrPut(name) { mutableListOf() }
                    if (phone !in list) list.add(phone)
                    if (byName.size > 800) break
                }
            }
        } catch (_: SecurityException) {
        }

        byName.map { (name, phones) -> ContactInfo(name, phones) }
    }
}
