package com.dynamicai.launcher.music

import android.content.Context

object GoogleAuthManager {
    private const val PREFS_NAME = "google_auth"
    private const val KEY_COOKIE = "cookie"
    private const val KEY_EMAIL = "email"

    fun save(context: Context, cookie: String, email: String = "") {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COOKIE, cookie)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    fun getCookie(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_COOKIE, null)
    }

    fun getEmail(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_EMAIL, null)
    }

    fun isLoggedIn(context: Context): Boolean = getCookie(context) != null

    fun logout(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
