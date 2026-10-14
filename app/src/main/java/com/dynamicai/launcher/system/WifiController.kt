package com.dynamicai.launcher.system

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

class WifiController(private val context: Context) {

    companion object {
        private const val TAG = "WifiController"
    }

    @Volatile var lastError: String = ""

    suspend fun setWifi(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
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
                SystemServiceHelper.getSystemService("wifi")
            } catch (t: Throwable) {
                lastError = "getSystemService: ${t.message}"
                return@withContext false
            }

            val wrapped = ShizukuBinderWrapper(binder)

            val stubClass = Class.forName("android.net.wifi.IWifiManager\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val wifiManager = asInterface.invoke(null, wrapped)
            if (wifiManager == null) {
                lastError = "asInterface returned null"
                return@withContext false
            }

            val method = wifiManager.javaClass.getMethod(
                "setWifiEnabled",
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            val result = method.invoke(wifiManager, context.packageName, enabled) as? Boolean
            if (result != true) {
                lastError = "setWifiEnabled returned $result"
            }
            result ?: false
        } catch (t: Throwable) {
            // Розгортаємо InvocationTargetException
            val cause = t.cause ?: t
            lastError = "${cause.javaClass.simpleName}: ${cause.message}"
            Log.e(TAG, "setWifi failed: $lastError", cause)
            false
        }
    }

    fun isWifiOn(): Boolean = try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
                as WifiManager
        wm.isWifiEnabled
    } catch (_: Throwable) { false }
}
