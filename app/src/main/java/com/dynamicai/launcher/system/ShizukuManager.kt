package com.dynamicai.launcher.system

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.lang.reflect.Method

/**
 * Обгортка над Shizuku для отримання привілейованих даних.
 */
class ShizukuManager(private val context: Context) {

    companion object {
        private const val TAG = "ShizukuManager"
        const val PERMISSION_REQUEST_CODE = 1001
    }

    data class RecentTask(
        val id: Int,
        val packageName: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable?
    )

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (t: Throwable) {
            Log.e(TAG, "pingBinder failed", t)
            false
        }
    }

    fun hasPermission(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (t: Throwable) {
            false
        }
    }

    fun requestPermission(requestCode: Int = PERMISSION_REQUEST_CODE) {
        try {
            if (Shizuku.isPreV11()) {
                Log.d(TAG, "Shizuku pre-v11 — не підтримується")
                return
            }
            Shizuku.requestPermission(requestCode)
        } catch (t: Throwable) {
            Log.e(TAG, "requestPermission failed", t)
        }
    }

    /**
     * Отримати список нещодавніх завдань через IActivityTaskManager (прихований API).
     * Потребує Shizuku з дозволом.
     */
    suspend fun getRecentTasks(maxCount: Int = 20): List<RecentTask> = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable() || !hasPermission()) {
            Log.d(TAG, "Shizuku недоступний або без дозволу")
            return@withContext emptyList()
        }

        val result = mutableListOf<RecentTask>()

        try {
            val activityManager = getActivityTaskManagerService() ?: run {
                Log.e(TAG, "Не вдалось отримати IActivityTaskManager")
                return@withContext emptyList()
            }

            // Викликаємо getTasks(int maxNum)
            val getTasksMethod: Method = activityManager.javaClass.getMethod(
                "getTasks",
                Int::class.javaPrimitiveType
            )

            val tasks = getTasksMethod.invoke(activityManager, maxCount) as? List<*> ?: emptyList<Any>()

            for (taskObj in tasks) {
                try {
                    // taskObj — ActivityManager.RunningTaskInfo
                    val taskInfo = taskObj ?: continue
                    val taskClass = taskInfo.javaClass
                    
                    val idField = taskClass.getField("taskId")
                    val taskId = idField.getInt(taskInfo)
                    
                    // Отримуємо baseIntent → component → packageName
                    val baseIntentField = taskClass.getField("baseIntent")
                    val baseIntent = baseIntentField.get(taskInfo) as? android.content.Intent
                    val pkg = baseIntent?.component?.packageName ?: continue
                    
                    // Пропускаємо наш власний застосунок
                    if (pkg == context.packageName) continue
                    
                    // Пропускаємо системні
                    if (isOurOwnOrSystem(pkg)) continue
                    
                    // Завантажуємо іконку і назву
                    val pm = context.packageManager
                    val appInfo = try {
                        pm.getApplicationInfo(pkg, 0)
                    } catch (_: Exception) {
                        continue
                    }
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)
                    
                    result.add(RecentTask(taskId, pkg, label, icon))
                } catch (t: Throwable) {
                    Log.e(TAG, "task parse failed", t)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "getRecentTasks failed", t)
        }

        result
    }

    /**
     * Отримати IActivityTaskManager через reflection + Shizuku binder.
     */
    private fun getActivityTaskManagerService(): Any? {
        return try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            val binder = getServiceMethod.invoke(null, "activity_task") as? IBinder
                ?: return null

            val stubClass = Class.forName("android.app.IActivityTaskManager\$Stub")
            val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)
            asInterfaceMethod.invoke(null, binder)
        } catch (t: Throwable) {
            Log.e(TAG, "getActivityTaskManagerService failed", t)
            null
        }
    }

    private fun isOurOwnOrSystem(pkg: String): Boolean {
        val systemPkgs = setOf(
            "com.android.systemui",
            "com.android.settings",
            "android",
            "com.google.android.gms",
            "com.android.launcher3"
        )
        return pkg in systemPkgs
    }

    /**
     * Вбити застосунок (потребує Shizuku).
     */
    suspend fun forceStop(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable() || !hasPermission()) return@withContext false
        try {
            val activityManager = getActivityManagerService() ?: return@withContext false
            val method = activityManager.javaClass.getMethod(
                "forceStopPackage",
                String::class.java
            )
            method.invoke(activityManager, packageName)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "forceStop failed", t)
            false
        }
    }

    private fun getActivityManagerService(): Any? {
        return try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            val binder = getServiceMethod.invoke(null, "activity") as? IBinder
                ?: return null

            val stubClass = Class.forName("android.app.IActivityManager\$Stub")
            val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)
            asInterfaceMethod.invoke(null, binder)
        } catch (t: Throwable) {
            Log.e(TAG, "getActivityManagerService failed", t)
            null
        }
    }
}
