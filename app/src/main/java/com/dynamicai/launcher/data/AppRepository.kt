package com.dynamicai.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.dynamicai.launcher.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val cache = AppsCache(context)

    // Кеш іконок у пам'яті — прискорює скрол/рекомпозиції
    private val iconCache = object : LruCache<String, Drawable>(250) {
        override fun sizeOf(key: String, value: Drawable): Int = 1
    }

    /**
     * Швидке завантаження:
     * 1. Якщо є кеш — повертаємо його одразу (label без іконок).
     * 2. Іконки підвантажуються окремо.
     */
    suspend fun loadCached(): List<AppInfo> = withContext(Dispatchers.IO) {
        cache.load().map { cached ->
            AppInfo(
                label = cached.label,
                packageName = cached.packageName,
                icon = loadIconOnly(cached.packageName),
                isSystem = cached.isSystem
            )
        }
    }

    /**
     * Повне сканування всіх застосунків (для оновлення кешу).
     */
    suspend fun loadInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(
            intent,
            PackageManager.MATCH_ALL
        )

        val result = ArrayList<AppInfo>(resolveInfos.size)
        val seen = HashSet<String>()

        for (ri in resolveInfos) {
            val appInfo = ri.activityInfo?.applicationInfo ?: continue
            val pkg = appInfo.packageName
            if (!seen.add(pkg)) continue
            val label = ri.loadLabel(pm).toString()
            val icon = ri.loadIcon(pm)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            result.add(AppInfo(label, pkg, icon, isSystem))
        }

        result.sortBy { it.label.lowercase() }
        result
    }

    private fun loadIconOnly(packageName: String): Drawable {
        iconCache.get(packageName)?.let { return it }
        val icon = try {
            pm.getApplicationIcon(packageName)
        } catch (_: Throwable) {
            pm.defaultActivityIcon
        }
        iconCache.put(packageName, icon)
        return icon
    }

    fun clearIconCache() = iconCache.evictAll()

    fun launchApp(packageName: String): Boolean {
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
