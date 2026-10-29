package com.dynamicai.launcher.macro

import android.content.Context
import android.content.Intent

/** Допоміжні функції для роботи зі списком додатків у макросах. */
object AppUtils {
    private fun launcherApps(ctx: Context): List<Pair<String, String>> = try {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(i, 0).map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
    } catch (_: Throwable) { emptyList() }

    /** Назва додатка за пакетом; якщо не знайдено — сам пакет. */
    fun labelFor(ctx: Context, pkg: String): String {
        if (pkg.isBlank()) return ""
        return try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Throwable) { pkg }
    }

    /** Пакет за назвою (для макросів, створених голосом/LLM). Якщо це вже пакет — повертає його. */
    fun packageFor(ctx: Context, name: String): String? {
        val n = name.trim()
        if (n.isBlank()) return null
        val apps = launcherApps(ctx)
        fun norm(x: String) = x.replace(" ", "").lowercase()
        val q = norm(n)
        apps.firstOrNull { it.second == n }?.let { return it.second }
        apps.firstOrNull { norm(it.first) == q }?.let { return it.second }
        return apps.firstOrNull { norm(it.first).contains(q) }?.second
    }
}
