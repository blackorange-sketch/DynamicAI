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
        val resolveInfos = pm.queryIntentActivities(intent, 0)

        resolveInfos
            .asSequence()
            .mapNotNull { ri ->
                val appInfo = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
                val label = ri.loadLabel(pm).toString()
                val pkg = appInfo.packageName
                val icon = ri.loadIcon(pm)
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                AppInfo(label = label, packageName = pkg, icon = icon, isSystem = isSystem)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
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
