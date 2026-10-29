package com.dynamicai.launcher.voice

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Допоміжне для розпізнавання мовлення без інтернету. */
object OfflineSpeech {
    fun isOnline(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (_: Throwable) { true }

    /** Без мережі просимо розпізнавач працювати офлайн (потрібен завантажений українській пакет у Google). */
    fun preferOffline(ctx: Context): Boolean = !isOnline(ctx)

    // ERROR_LANGUAGE_NOT_SUPPORTED = 12, ERROR_LANGUAGE_UNAVAILABLE = 13
    fun isMissingLanguage(error: Int) = error == 12 || error == 13

    const val HINT = "Для голосу без інтернету завантажте українську мову: Налаштування → Google → Голосовий ввід → Офлайн-розпізнавання"
}
