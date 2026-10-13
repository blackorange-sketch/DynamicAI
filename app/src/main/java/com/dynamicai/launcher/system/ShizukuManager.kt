package com.dynamicai.launcher.system

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.lang.reflect.Method

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

    @Volatile private var binderReceived = false

    init {
        try {
            Shizuku.addBinderReceivedListenerSticky {
                binderReceived = true
                Log.d(TAG, "✅ Shizuku binder received (sticky)")
            }
            Shizuku.addBinderReceivedListener {
                binderReceived = true
                Log.d(TAG, "✅ Shizuku binder received")
            }
            Shizuku.addBinderDeadListener {
                binderReceived = false
                Log.d(TAG, "❌ Shizuku binder dead")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "listener add failed", t)
        }
    }

    fun isBinderReceived(): Boolean = binderReceived

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (t: Throwable) {
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

    fun requestPermission(requestCode: Int = PERMISSION_REQUEST_CODE): String {
        return try {
            if (!Shizuku.pingBinder()) {
                return "❌ Shizuku не запущено. Відкрий Shizuku і запусти (Root/ADB)"
            }
            if (Shizuku.isPreV11()) {
                return "❌ Shizuku pre-v11 не підтримується"
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                return "✅ Дозвіл вже надано"
            }
            Shizuku.requestPermission(requestCode)
            return "📩 Запит відправлено — перевір діалог Shizuku"
        } catch (t: Throwable) {
            Log.e(TAG, "requestPermission failed", t)
            return "❌ Помилка: ${t.message}"
        }
    }

    suspend fun getRecentTasks(maxCount: Int = 20): List<RecentTask> = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable() || !hasPermission()) {
            return@withContext emptyList()
        }

        val result = mutableListOf<RecentTask>()

        try {
            val activityTaskManager = getActivityTaskManagerService() ?: return@withContext emptyList()

            val getTasksMethod: Method = activityTaskManager.javaClass.getMethod(
                "getTasks",
                Int::class.javaPrimitiveType
            )

            val tasks = getTasksMethod.invoke(activityTaskManager, maxCount) as? List<*> ?: emptyList<Any>()

            for (taskObj in tasks) {
                try {
                    val taskInfo = taskObj ?: continue
                    val taskClass = taskInfo.javaClass

                    val idField = taskClass.getField("taskId")
                    val taskId = idField.getInt(taskInfo)

                    val baseIntentField = taskClass.getField("baseIntent")
                    val baseIntent = baseIntentField.get(taskInfo) as? android.content.Intent
                    val pkg = baseIntent?.component?.packageName ?: continue

                    if (pkg == context.packageName) continue
                    if (isOurOwnOrSystem(pkg)) continue

                    val pm = context.packageManager
                    val appInfo = try {
                        pm.getApplicationInfo(pkg, 0)
                    } catch (_: Exception) { continue }
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)

                    result.add(RecentTask(taskId, pkg, label, icon))
                } catch (_: Throwable) {}
            }
        } catch (t: Throwable) {
            Log.e(TAG, "getRecentTasks failed", t)
        }
        result
    }

    private fun getActivityTaskManagerService(): Any? {
        return try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = smClass.getMethod("getService", String::class.java)
            val binder = getServiceMethod.invoke(null, "activity_task") as? IBinder ?: return null
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

    suspend fun forceStop(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable() || !hasPermission()) return@withContext false
        try {
            val amClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = amClass.getMethod("getService", String::class.java)
            val binder = getServiceMethod.invoke(null, "activity") as? IBinder ?: return@withContext false
            val stubClass = Class.forName("android.app.IActivityManager\$Stub")
            val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)
            val am = asInterfaceMethod.invoke(null, binder) ?: return@withContext false
            val method = am.javaClass.getMethod("forceStopPackage", String::class.java)
            method.invoke(am, packageName)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "forceStop failed", t)
            false
        }
    }
}
