package com.dynamicai.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import com.dynamicai.launcher.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val cache = AppsCache(context)

    // Обмежує і паралелізм, і кількість тредів у пулі
    private val ioLimited = Dispatchers.IO.limitedParallelism(4)

    private val placeholderIcon: Drawable = android.graphics.drawable.ColorDrawable(0xFF1E1E1E.toInt())

    private val iconCache = object : LruCache<String, Drawable>(80) {
        override fun sizeOf(key: String, value: Drawable): Int = 1
    }


    private fun mapCategory(cat: Int): String = when (cat) {
        ApplicationInfo.CATEGORY_GAME -> "games"
        ApplicationInfo.CATEGORY_SOCIAL -> "social"
        ApplicationInfo.CATEGORY_AUDIO,
        ApplicationInfo.CATEGORY_VIDEO,
        ApplicationInfo.CATEGORY_IMAGE -> "media"
        ApplicationInfo.CATEGORY_PRODUCTIVITY,
        ApplicationInfo.CATEGORY_NEWS -> "tools"
        ApplicationInfo.CATEGORY_MAPS -> "maps"
        else -> "other"
    }

    suspend fun loadCached(): List<AppInfo> = withContext(Dispatchers.IO) {
        val list = cache.load()
        coroutineScope {
            list.map { cached ->
                async(ioLimited) {
                    val icon = loadIconOnly(cached.packageName)
                    val bmp = try { icon.toBitmap(72, 72) } catch (_: Throwable) { null }
                    val cat = try {
                        pm.getApplicationInfo(cached.packageName, 0).category
                    } catch (_: Throwable) { ApplicationInfo.CATEGORY_UNDEFINED }
                    AppInfo(
                        label = cached.label,
                        packageName = cached.packageName,
                        icon = placeholderIcon,
                        isSystem = cached.isSystem,
                        bitmap = bmp,
                        category = mapCategory(cat)
                    )
                }
            }.awaitAll()
        }
    }

    /** Дешевий підпис списку встановлених додатків (без завантаження іконок) — щоб не перезавантажувати все зайвий раз. */
    suspend fun packageSignature(): Int = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .joinToString(",") { it.activityInfo?.packageName.orEmpty() }.hashCode()
    }

    suspend fun loadInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)

        val seen = HashSet<String>()
        data class Raw(val label: String, val pkg: String, val isSystem: Boolean, val category: String)
        val unique = ArrayList<Raw>(resolveInfos.size)
        for (ri in resolveInfos) {
            val appInfo = ri.activityInfo?.applicationInfo ?: continue
            val pkg = appInfo.packageName
            if (!seen.add(pkg)) continue
            val label = ri.loadLabel(pm).toString()
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            unique.add(Raw(label, pkg, isSystem, mapCategory(appInfo.category)))
        }

        val result = coroutineScope {
            unique.map { r ->
                async(ioLimited) {
                    val icon = try { pm.getApplicationIcon(r.pkg) } catch (_: Throwable) { pm.defaultActivityIcon }
                    val bmp = try { icon.toBitmap(72, 72) } catch (_: Throwable) { null }
                    AppInfo(r.label, r.pkg, placeholderIcon, r.isSystem, bmp, r.category)
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
