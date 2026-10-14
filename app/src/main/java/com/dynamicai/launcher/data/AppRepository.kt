package com.dynamicai.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
        val list = cache.load()
        coroutineScope {
            list.map { cached ->
                async(ioLimited) {
                    val icon = loadIconOnly(cached.packageName)
                    val bmp = try { icon.toBitmap(96, 96) } catch (_: Throwable) { null }
                    AppInfo(
                        label = cached.label,
                        packageName = cached.packageName,
                        icon = icon,
                        isSystem = cached.isSystem,
                        bitmap = bmp
                    )
                }
            }.awaitAll()
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

        val seen = HashSet<String>()
        val unique = ArrayList<Pair<String, String>>(resolveInfos.size)  // label to pkg
        for (ri in resolveInfos) {
            val appInfo = ri.activityInfo?.applicationInfo ?: continue
            val pkg = appInfo.packageName
            if (!seen.add(pkg)) continue
            val label = ri.loadLabel(pm).toString()
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            unique.add(label to "$pkg\u0000${if (isSystem) 1 else 0}")
        }

        val result = coroutineScope {
            unique.map { (label, packed) ->
                async(ioLimited) {
                    val parts = packed.split("\u0000")
                    val pkg = parts[0]
                    val isSystem = parts.getOrNull(1) == "1"
                    val icon = try { pm.getApplicationIcon(pkg) } catch (_: Throwable) { pm.defaultActivityIcon }
                    val bmp = try { icon.toBitmap(96, 96) } catch (_: Throwable) { null }
                    AppInfo(label, pkg, icon, isSystem, bmp)
                }
            }.awaitAll()
        }

        result.sortedBy { it.label.lowercase() }
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
