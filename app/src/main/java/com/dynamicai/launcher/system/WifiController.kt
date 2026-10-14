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

    @Volatile var lastError: String = ""

    suspend fun setWifi(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        lastError = ""
        try {
            // 1. Перевірка Shizuku
            if (!Shizuku.pingBinder()) {
                lastError = "pingBinder=false"
                Log.d(TAG, lastError)
                return@withContext false
            }
            Log.d(TAG, "pingBinder=OK")

            // 2. Перевірка дозволу
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                lastError = "no permission"
                Log.d(TAG, lastError)
                return@withContext false
            }
            Log.d(TAG, "permission=OK")

            // 3. Отримуємо binder
            val binder: IBinder = try {
                SystemServiceHelper.getSystemService("wifi")
            } catch (t: Throwable) {
                lastError = "getSystemService: ${t.message}"
                Log.e(TAG, lastError, t)
                return@withContext false
            }
            Log.d(TAG, "binder obtained: $binder")

            // 4. Обгортаємо
            val wrapped = ShizukuBinderWrapper(binder)

            // 5. IWifiManager$Stub
            val stubClass = Class.forName("android.net.wifi.IWifiManager\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val wifiManager = asInterface.invoke(null, wrapped)
                ?: run {
                    lastError = "asInterface returned null"
                    return@withContext false
                }
            Log.d(TAG, "wifiManager: ${wifiManager.javaClass.name}")

            // 6. Виклик setWifiEnabled
            val method = wifiManager.javaClass.getMethod(
                "setWifiEnabled",
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            val result = method.invoke(wifiManager, context.packageName, enabled) as? Boolean
            Log.d(TAG, "setWifiEnabled($enabled) → $result")
            if (result != true) {
                lastError = "setWifiEnabled returned $result"
            }
            result ?: false
        } catch (t: Throwable) {
            lastError = "${t.javaClass.simpleName}: ${t.message}"
            Log.e(TAG, "setWifi failed: $lastError", t)
            false
        }
    } withContext(Dispatchers.IO) {
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
