package com.dynamicai.launcher.data

import android.content.Context

class DockStorage(context: Context) {

    private val prefs = context.getSharedPreferences("dynamicai_dock", Context.MODE_PRIVATE)

    fun getDockApps(): List<String> =
        prefs.getStringSet("apps", emptySet())?.toList() ?: emptyList()

    fun addToDock(pkg: String) {
        val current = getDockApps().toMutableSet()
        current.add(pkg)
        prefs.edit().putStringSet("apps", current).commit()
    }

    fun removeFromDock(pkg: String) {
        val current = getDockApps().toMutableSet()
        current.remove(pkg)
        prefs.edit().putStringSet("apps", current).commit()
    }

    fun toggle(pkg: String) {
        if (pkg in getDockApps()) removeFromDock(pkg) else addToDock(pkg)
    }
}
