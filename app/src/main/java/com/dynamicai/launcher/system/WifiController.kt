package com.dynamicai.launcher.system

import android.content.Context
import android.net.wifi.WifiManager
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Пряме керування WiFi через Shizuku + IWifiManager Binder.
 */
class WifiController(private val context: Context) {

    companion object {
        private const val TAG = "WifiController"
    }

    suspend fun setWifi(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!Shizuku.pingBinder()) {
                Log.d(TAG, "Shizuku not running")
                return@withContext false
            }

            // Отримуємо binder сервісу "wifi"
            val binder: IBinder = try {
                SystemServiceHelper.getSystemService("wifi")
            } catch (t: Throwable) {
                Log.e(TAG, "getSystemService failed", t)
                return@withContext false
            }

            // Обгортаємо через ShizukuBinderWrapper — це дозволяє викликати
            // приховані методи від імені shell
            val wrapped = ShizukuBinderWrapper(binder)

            val stubClass = Class.forName("android.net.wifi.IWifiManager\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val wifiManager = asInterface.invoke(null, wrapped) ?: return@withContext false

            // Викликаємо setWifiEnabled(packageName, enabled)
            val method = wifiManager.javaClass.getMethod(
                "setWifiEnabled",
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            val result = method.invoke(wifiManager, context.packageName, enabled) as? Boolean
            Log.d(TAG, "setWifiEnabled($enabled) → $result")
            result ?: false
        } catch (t: Throwable) {
            Log.e(TAG, "setWifi failed", t)
            false
        }
    }

    fun isWifiOn(): Boolean = try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
                as WifiManager
        wm.isWifiEnabled
    } catch (_: Throwable) { false }
}
