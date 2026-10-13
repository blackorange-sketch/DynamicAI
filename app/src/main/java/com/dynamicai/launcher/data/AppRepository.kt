package com.dynamicai.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.dynamicai.launcher.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    suspend fun loadInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(
            intent,
            android.content.pm.PackageManager.MATCH_ALL
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
