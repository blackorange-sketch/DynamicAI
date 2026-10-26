package com.dynamicai.launcher.music

import android.content.Context

object AuthManager {
    private const val PREFS_NAME = "ytmusic_auth"
    private const val KEY_COOKIE = "cookie"

    fun saveCookie(context: Context, cookie: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_COOKIE, cookie).apply()
    }

    fun loadCookie(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_COOKIE, null)
    }

    fun isLoggedIn(context: Context): Boolean = loadCookie(context) != null
}
