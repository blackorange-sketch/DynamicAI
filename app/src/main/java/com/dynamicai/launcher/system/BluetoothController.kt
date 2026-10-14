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

            // Створюємо AttributionSource (Android 12+)
            val attributionSourceObj: Any? = try {
                val asBuilderClass = Class.forName("android.content.AttributionSource\$Builder")
                val asClass = Class.forName("android.content.AttributionSource")
                val builder = asBuilderClass.getConstructor(
                    Context::class.java,
                    String::class.java,
                    asClass,
                    String::class.java
                ).newInstance(context, "com.android.shell", null, null)
                val buildMethod = asBuilderClass.getMethod("build")
                buildMethod.invoke(builder)
            } catch (t: Throwable) {
                Log.d(TAG, "AttributionSource failed: ${t.message}")
                null
            }
            
            val asClass = try {
                Class.forName("android.content.AttributionSource")
            } catch (_: Throwable) { null }

            val errors = StringBuilder()
            val method = if (enabled) "enable" else "disable"

            // Спроба 1: enable/disable(AttributionSource)
            if (attributionSourceObj != null && asClass != null) {
                try {
                    val m = btManager.javaClass.getMethod(method, asClass)
                    val result = m.invoke(btManager, attributionSourceObj)
                    Log.d(TAG, "$method(AttributionSource) → $result")
                    if (result == true) return@withContext true
                    errors.append("$method(AS)=$result; ")
                } catch (t: Throwable) {
                    val cause = t.cause ?: t
                    errors.append("$method(AS):${cause.javaClass.simpleName}:${cause.message?.take(40)} | ")
                }
            }

            // Спроба 2: enable/disable(AttributionSource, boolean)
            if (attributionSourceObj != null && asClass != null) {
                try {
                    val m = btManager.javaClass.getMethod(method, asClass, Boolean::class.javaPrimitiveType)
                    val result = m.invoke(btManager, attributionSourceObj, false)
                    Log.d(TAG, "$method(AS,boolean) → $result")
                    if (result == true) return@withContext true
                    errors.append("$method(AS,b)=$result; ")
                } catch (t: Throwable) {
                    val cause = t.cause ?: t
                    errors.append("$method(AS,b):${cause.javaClass.simpleName}:${cause.message?.take(40)} | ")
                }
            }

            // Спроба 3: enable/disable()
            try {
                val m = btManager.javaClass.getMethod(method)
                val result = m.invoke(btManager)
                if (result == true) return@withContext true
                errors.append("$method()=$result; ")
            } catch (t: Throwable) {
                val cause = t.cause ?: t
                errors.append("$method():${cause.javaClass.simpleName}:${cause.message?.take(40)} | ")
            }

            lastError = errors.toString().take(220)
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
