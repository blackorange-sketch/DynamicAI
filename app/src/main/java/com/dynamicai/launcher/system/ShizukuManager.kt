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
        val icon: android.graphics.drawable.Drawable?,
        val snapshot: android.graphics.Bitmap? = null
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

    fun getShizukuVersion(): Int {
        return try {
            Shizuku.getVersion()
        } catch (t: Throwable) {
            -1
        }
    }

    fun isShizukuInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

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

    @Volatile var lastDiagnostic: String = ""

    suspend fun getRecentTasks(maxCount: Int = 20): List<RecentTask> = withContext(Dispatchers.IO) {
        val diag = StringBuilder()
        diag.append("pingBinder=").append(isShizukuAvailable()).append("\n")
        diag.append("hasPermission=").append(hasPermission()).append("\n")

        if (!isShizukuAvailable() || !hasPermission()) {
            lastDiagnostic = diag.toString()
            return@withContext emptyList()
        }

        val result = mutableListOf<RecentTask>()

        try {
            val activityTaskManager = getActivityTaskManagerService()
            if (activityTaskManager == null) {
                diag.append("service=null\n")
                lastDiagnostic = diag.toString()
                return@withContext emptyList()
            }
            diag.append("service=").append(activityTaskManager.javaClass.name).append("\n")

            // Спробувати кілька сигнатур getTasks
            var tasks: List<*>? = null
            var methodUsed = ""

            // Спроба 1: getTasks(int)
            try {
                val m1 = activityTaskManager.javaClass.getMethod("getTasks", Int::class.javaPrimitiveType)
                tasks = m1.invoke(activityTaskManager, maxCount) as? List<*>
                methodUsed = "getTasks(int)"
            } catch (t: Throwable) {
                diag.append("getTasks(int) failed: ").append(t.message).append("\n")
            }

            // Спроба 2: getTasks(int, boolean, boolean)
            if (tasks == null) {
                try {
                    val m2 = activityTaskManager.javaClass.getMethod(
                        "getTasks",
                        Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType
                    )
                    tasks = m2.invoke(activityTaskManager, maxCount, false, false) as? List<*>
                    methodUsed = "getTasks(int,bool,bool)"
                } catch (t: Throwable) {
                    diag.append("getTasks(3args) failed: ").append(t.message).append("\n")
                }
            }

            // Спроба 3: getTasks(int, boolean, boolean, int)
            if (tasks == null) {
                try {
                    val m3 = activityTaskManager.javaClass.getMethod(
                        "getTasks",
                        Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    )
                    tasks = m3.invoke(activityTaskManager, maxCount, false, false, 0) as? List<*>
                    methodUsed = "getTasks(int,bool,bool,int)"
                } catch (t: Throwable) {
                    diag.append("getTasks(4args) failed: ").append(t.message).append("\n")
                }
            }

            diag.append("methodUsed=").append(methodUsed).append("\n")
            diag.append("tasksRaw=").append(tasks?.size ?: -1).append("\n")

            if (tasks == null) {
                lastDiagnostic = diag.toString()
                return@withContext emptyList()
            }

            for (taskObj in tasks) {
                try {
                    val taskInfo = taskObj ?: continue
                    val taskClass = taskInfo.javaClass

                    val idField = taskClass.getField("taskId")
                    val taskId = idField.getInt(taskInfo)

                    var pkg: String? = null
                    try {
                        val topField = taskClass.getField("topActivity")
                        val top = topField.get(taskInfo) as? android.content.ComponentName
                        pkg = top?.packageName
                    } catch (_: Throwable) {}

                    if (pkg.isNullOrBlank()) {
                        try {
                            val baseField = taskClass.getField("baseIntent")
                            val base = baseField.get(taskInfo) as? android.content.Intent
                            pkg = base?.component?.packageName
                        } catch (_: Throwable) {}
                    }

                    if (pkg.isNullOrBlank()) continue

                    val pm = context.packageManager
                    val appInfo = try {
                        pm.getApplicationInfo(pkg, 0)
                    } catch (_: Exception) { continue }
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)

                    // Спробувати отримати snapshot
                    val snapshot = try { getTaskSnapshot(taskId) } catch (_: Throwable) { null }

                    result.add(RecentTask(taskId, pkg, label, icon, snapshot))
                } catch (t: Throwable) {
                    diag.append("parseErr: ").append(t.message).append("\n")
                }
            }

            diag.append("final=").append(result.size)
        } catch (t: Throwable) {
            Log.e(TAG, "getRecentTasks failed", t)
            diag.append("outerErr=").append(t.message)
        }

        lastDiagnostic = diag.toString()
        result
    }

    private fun getTaskSnapshot(taskId: Int): android.graphics.Bitmap? {
        return try {
            val atm = getActivityTaskManagerService() ?: return null

            // Викликаємо getTaskSnapshot(taskId, isLowResolution, takeSnapshotIfNeeded)
            val method = atm.javaClass.getMethod(
                "getTaskSnapshot",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )
            val snapshot = method.invoke(atm, taskId, false, true) ?: return null

            // TaskSnapshot.getSnapshot() → HardwareBuffer
            val getSnapshotMethod = snapshot.javaClass.getMethod("getSnapshot")
            val hardwareBuffer = getSnapshotMethod.invoke(snapshot) ?: return null

            // HardwareBuffer → Bitmap
            val bitmapClass = Class.forName("android.graphics.Bitmap")
            val wrapMethod = bitmapClass.getMethod(
                "wrapHardwareBuffer",
                Class.forName("android.hardware.HardwareBuffer"),
                Class.forName("android.graphics.ColorSpace")
            )
            val bitmap = wrapMethod.invoke(null, hardwareBuffer, null) as? android.graphics.Bitmap
            bitmap
        } catch (t: Throwable) {
            Log.e(TAG, "getTaskSnapshot failed for taskId=$taskId: ${t.message}")
            null
        }
    }

    private fun getActivityTaskManagerService(): Any? {
        return try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = smClass.getMethod("getService", String::class.java)
            val binder = getServiceMethod.invoke(null, "activity_task") as? IBinder
                ?: run {
                    Log.e(TAG, "activity_task binder == null")
                    return null
                }

            Log.d(TAG, "binder: $binder")

            // КЛЮЧОВЕ: обгортаємо binder через ShizukuBinderWrapper
            val wrapped = rikka.shizuku.ShizukuBinderWrapper(binder)
            Log.d(TAG, "wrapped: $wrapped")

            val stubClass = Class.forName("android.app.IActivityTaskManager\$Stub")
            val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)
            val service = asInterfaceMethod.invoke(null, wrapped)
            Log.d(TAG, "service: $service")
            service
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
     * Встановити будильник напряму через AlarmManager (прихований API).
     * Не потребує відкриття додатку годинника.
     */
    suspend fun setAlarmViaShizuku(hour: Int, minute: Int, label: String?): Boolean = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable() || !hasPermission()) {
            Log.d(TAG, "Shizuku недоступний")
            return@withContext false
        }
        try {
            // Використовуємо прямий AlarmManager через контекст
            // (Shizuku тут допомагає обійти обмеження на API 31+)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val cal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
                if (timeInMillis < System.currentTimeMillis()) {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
            }

            val intent = android.content.Intent(context, com.dynamicai.launcher.alarm.AlarmReceiver::class.java).apply {
                putExtra("label", label ?: "DynamicAI Alarm")
                putExtra("hour", hour)
                putExtra("minute", minute)
            }
            val pending = android.app.PendingIntent.getBroadcast(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            if (android.os.Build.VERSION.SDK_INT >= 31) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.d(TAG, "Немає дозволу SCHEDULE_EXACT_ALARM")
                    return@withContext false
                }
            }
            alarmManager.setAlarmClock(
                android.app.AlarmManager.AlarmClockInfo(cal.timeInMillis, pending),
                pending
            )
            Log.d(TAG, "Будильник встановлено: %02d:%02d".format(hour, minute))
            true
        } catch (t: Throwable) {
            Log.e(TAG, "setAlarmViaShizuku failed", t)
            false
        }
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

    /**
     * Виконує shell-команду з правами Shizuku (ADB).
     */
    suspend fun execShell(command: String): String = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable() || !hasPermission()) {
            Log.e(TAG, "Shizuku недоступний для execShell")
            return@withContext "ERROR: No Shizuku permission"
        }
        try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val output = process.inputStream.bufferedReader().readText().trim()
            val error = process.errorStream.bufferedReader().readText().trim()
            process.waitFor()
            if (error.isNotEmpty()) {
                Log.e(TAG, "Shell error: $error")
                return@withContext "ERROR: $error"
            }
            Log.d(TAG, "Shell OK: $command -> $output")
            return@withContext if (output.isEmpty()) "OK" else output
        } catch (t: Throwable) {
            Log.e(TAG, "execShell failed: ${t.message}", t)
            return@withContext "ERROR: ${t.message}"
        }
    }
}
