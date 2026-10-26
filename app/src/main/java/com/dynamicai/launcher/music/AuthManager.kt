package com.dynamicai.launcher.music

import android.content.Context
import com.tamed.music.innertube.YouTube

object AuthManager {
    private const val PREFS_NAME = "ytmusic_auth"
    private const val KEY_COOKIE = "cookie"

    fun saveCookie(context: Context, cookie: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_COOKIE, cookie).apply()
        YouTube.cookie = cookie
    }

    fun loadCookie(context: Context): String? {
        val cookie = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_COOKIE, null)
        if (cookie != null) YouTube.cookie = cookie
        return cookie
    }

    fun isLoggedIn(context: Context): Boolean = loadCookie(context) != null
}
