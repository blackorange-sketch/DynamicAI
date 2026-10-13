package com.dynamicai.launcher.suggestion

import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactInfo(
    val name: String,
    val phone: String?
)

class ContactRepository(private val context: Context) {

    suspend fun loadAll(): List<ContactInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<ContactInfo>()
        val seen = mutableSetOf<String>()

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
                    val name = c.getString(nameIdx) ?: continue
                    val phone = c.getString(phoneIdx)
                    val key = "$name|${phone.orEmpty()}"
                    if (key in seen) continue
                    seen.add(key)
                    result.add(ContactInfo(name, phone))
                    if (result.size >= 500) break
                }
            }
        } catch (_: SecurityException) {
        }
        result
    }
}
