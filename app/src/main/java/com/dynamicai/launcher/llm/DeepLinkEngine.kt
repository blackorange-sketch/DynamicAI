package com.dynamicai.launcher.llm

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.dynamicai.launcher.suggestion.ContactRepository

class DeepLinkEngine(private val context: Context) {
    private val contactRepo = ContactRepository(context)

    private fun launch(intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun findContactNumber(name: String): String? {
        return try {
            val contacts = contactRepo.loadAll()
            val contact = contacts.find { it.name.contains(name, ignoreCase = true) }
            contact?.phones?.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    fun openTelegramChat(contactName: String): String {
        // Спочатку пробуємо знайти username (якщо є), інакше - за номером
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$contactName"))
        return if (launch(intent)) "Відкриваю Telegram: $contactName"
        else "Не вдалося відкрити Telegram. Перевірте чи встановлено додаток."
    }

    suspend fun openWhatsAppChat(contactName: String): String {
        val number = findContactNumber(contactName)
        if (number == null) return "Контакт '$contactName' не знайдено в телефонній книзі."
        val cleanNumber = number.replace(Regex("[^0-9+]"), "")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber"))
        return if (launch(intent)) "Відкриваю WhatsApp: $contactName"
        else "Не вдалося відкрити WhatsApp."
    }

    suspend fun openViberChat(contactName: String): String {
        val number = findContactNumber(contactName)
        if (number == null) return "Контакт '$contactName' не знайдено."
        val cleanNumber = number.replace(Regex("[^0-9+]"), "")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("viber://chat?number=$cleanNumber"))
        return if (launch(intent)) "Відкриваю Viber: $contactName"
        else "Viber не встановлено."
    }

    fun openYouTubeSearch(query: String): String {
        // Спроба 1: Спеціальний URI для YouTube
        val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube://results?search_query=${Uri.encode(query)}"))
        if (launch(ytIntent)) return "Шукаю на YouTube: $query"
        
        // Спроба 2: Через веб-версію
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}"))
        return if (launch(webIntent)) "Відкриваю YouTube (браузер): $query"
        else "Помилка відкриття YouTube."
    }

    fun openInstagramProfile(username: String): String {
        val cleanUser = username.replace("@", "")
        val igIntent = Intent(Intent.ACTION_VIEW, Uri.parse("instagram://user?username=$cleanUser"))
        if (launch(igIntent)) return "Відкриваю Instagram: @$cleanUser"
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/$cleanUser"))
        return if (launch(webIntent)) "Відкриваю Instagram (браузер): @$cleanUser"
        else "Instagram не встановлено."
    }

    fun openMaps(destination: String, mode: String = "driving"): String {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(destination)}&mode=$mode"))
        if (launch(intent)) return "Будую маршрут до: $destination"
        // Fallback
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(destination)}"))
        return if (launch(webIntent)) "Відкриваю мапи: $destination"
        else "Не вдалося відкрити мапи."
    }

    fun sendEmail(to: String, subject: String, body: String): String {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${Uri.encode(to)}")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return if (launch(intent)) "Відкриваю пошту: $to"
        else "Поштовий клієнт не знайдено."
    }

    suspend fun sendSms(contactName: String, message: String): String {
        val number = findContactNumber(contactName) ?: return "Контакт '$contactName' не знайдено."
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$number")
            putExtra("sms_body", message)
        }
        return if (launch(intent)) "Відкриваю SMS для $contactName"
        else "SMS-додаток не знайдено."
    }

    fun openSpotifySearch(query: String): String {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(query)}"))
        if (launch(intent)) return "Шукаю в Spotify: $query"
        return "Spotify не встановлено."
    }
}
