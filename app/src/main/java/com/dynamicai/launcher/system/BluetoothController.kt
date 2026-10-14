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

            // Пробуємо різні сигнатури
            val signatures = listOf(
                Triple("enable", arrayOf(SHELL_PACKAGE), "enable(String)"),
                Triple("enable", arrayOf(), "enable()"),
                Triple("enableNoAutoConnect", arrayOf(SHELL_PACKAGE), "enableNoAutoConnect(String)"),
                Triple("enableNoAutoConnect", arrayOf(), "enableNoAutoConnect()")
            )

            if (enabled) {
                for ((method, args, name) in signatures) {
                    try {
                        val m = btManager.javaClass.getMethod(method, *Array(args.size) { String::class.java })
                        val result = m.invoke(btManager, *args)
                        Log.d(TAG, "$name → $result")
                        if (result == true) return@withContext true
                    } catch (_: Throwable) {}
                }
            } else {
                try {
                    val m = btManager.javaClass.getMethod("disable", String::class.java)
                    val result = m.invoke(btManager, SHELL_PACKAGE)
                    Log.d(TAG, "disable(String) → $result")
                    if (result == true) return@withContext true
                } catch (_: Throwable) {}

                try {
                    val m = btManager.javaClass.getMethod("disable")
                    val result = m.invoke(btManager)
                    if (result == true) return@withContext true
                } catch (_: Throwable) {}
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
