package com.dynamicai.launcher.system

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

class BluetoothController(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothController"
        const val SHELL_PACKAGE = "com.android.shell"
    }

    @Volatile var lastError: String = ""

    suspend fun setBluetooth(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        lastError = ""
        try {
            if (!Shizuku.pingBinder()) {
                lastError = "pingBinder=false"
                return@withContext false
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                lastError = "no permission"
                return@withContext false
            }

            val binder: IBinder = try {
                SystemServiceHelper.getSystemService("bluetooth_manager")
            } catch (t: Throwable) {
                lastError = "getSystemService: ${t.message}"
                return@withContext false
            }

            val wrapped = ShizukuBinderWrapper(binder)

            val stubClass = Class.forName("android.bluetooth.IBluetoothManager\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val btManager = asInterface.invoke(null, wrapped)
                ?: run {
                    lastError = "asInterface returned null"
                    return@withContext false
                }

            // Створюємо AttributionSource для Android 13+
            val attributionSource = try {
                val asBuilder = Class.forName("android.content.AttributionSource\$Builder")
                val asClass = Class.forName("android.content.AttributionSource")
                val builder = asBuilder.getConstructor(
                    Context::class.java,
                    String::class.java,
                    asClass,
                    String::class.java
                ).newInstance(context, context.packageName, null, null)
                val buildMethod = asBuilder.getMethod("build")
                val asObj = buildMethod.invoke(builder)
                // Обгортаємо через asBinder() для передачі через binder
                val asBinderMethod = asClass.getMethod("asBinder")
                val asBinder = asBinderMethod.invoke(asObj) as? IBinder
                Log.d(TAG, "AttributionSource created: $asBinder")
                asBinder
            } catch (t: Throwable) {
                Log.d(TAG, "AttributionSource failed: ${t.message}")
                null
            }

            // Список спроб: (method, args)
            val attempts = mutableListOf<Pair<String, Array<Any?>>>()
            
            if (enabled) {
                if (attributionSource != null) {
                    attempts.add("enable" to arrayOf(SHELL_PACKAGE, attributionSource))
                }
                attempts.add("enable" to arrayOf(SHELL_PACKAGE))
                attempts.add("enableNoAutoConnect" to arrayOf(SHELL_PACKAGE))
                attempts.add("enable" to arrayOf())
            } else {
                if (attributionSource != null) {
                    attempts.add("disable" to arrayOf(SHELL_PACKAGE, attributionSource))
                }
                attempts.add("disable" to arrayOf(SHELL_PACKAGE))
                attempts.add("disable" to arrayOf())
            }

            for ((methodName, args) in attempts) {
                try {
                    val paramTypes = args.map { arg ->
                        when (arg) {
                            is String -> String::class.java
                            is IBinder -> IBinder::class.java
                            else -> Any::class.java
                        }
                    }.toTypedArray()
                    
                    val m = btManager.javaClass.getMethod(methodName, *paramTypes)
                    val result = m.invoke(btManager, *args)
                    Log.d(TAG, "$methodName(${args.size} args) → $result")
                    if (result == true) return@withContext true
                } catch (t: Throwable) {
                    Log.d(TAG, "$methodName failed: ${t.cause?.message ?: t.message}")
                }
            }

            lastError = "всі сигнатури провалились"
            false
        } catch (t: Throwable) {
            val cause = t.cause ?: t
            lastError = "${cause.javaClass.simpleName}: ${cause.message}"
            Log.e(TAG, "setBluetooth failed: $lastError", cause)
            false
        }
    }

    fun isBluetoothOn(): Boolean = try {
        BluetoothAdapter.getDefaultAdapter()?.isEnabled ?: false
    } catch (_: Throwable) { false }
}
