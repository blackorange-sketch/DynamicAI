package com.dynamicai.launcher.data

import android.content.Context

class HiddenAppsStorage(context: Context) {
    private val prefs = context.getSharedPreferences("dynamicai_hidden", Context.MODE_PRIVATE)

    fun get(): Set<String> = prefs.getStringSet("apps", emptySet()) ?: emptySet()

    fun hide(pkg: String) {
        val cur = get().toMutableSet()
        cur.add(pkg)
        prefs.edit().putStringSet("apps", cur).apply()
    }

    fun show(pkg: String) {
        val cur = get().toMutableSet()
        cur.remove(pkg)
        prefs.edit().putStringSet("apps", cur).apply()
    }

    fun clear() {
        prefs.edit().remove("apps").apply()
    }
}
