package com.dynamicai.launcher.system

import android.content.Context
import android.net.wifi.WifiManager
import android.bluetooth.BluetoothAdapter
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.Method

/**
 * Керування системними налаштуваннями через публічний API + reflection.
 * WiFi/BT на Android 10+ вимагають або Shizuku, або налаштувань користувача.
 */
class SystemSettingsManager(private val context: Context) {

    companion object {
        private const val TAG = "SystemSettings"
    }

    // ===== WiFi =====
    fun isWifiEnabled(): Boolean {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wm.isWifiEnabled
        } catch (t: Throwable) {
            Log.e(TAG, "isWifiEnabled failed", t)
            false
        }
    }

    /**
     * Увімкнути/вимкнути WiFi.
     * На Android 10+ setWifiEnabled deprecated і блокується системою.
     * Спроби:
     *  1. Публічний API (працює до Android 10)
     *  2. Reflection (Android 10-12)
     *  3. Intent до налаштувань (fallback — відкриває панель)
     */
    suspend fun setWifiEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        // Спроба 1: публічний API
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ok = wm.setWifiEnabled(enabled)
            if (ok) {
                Log.d(TAG, "WiFi ${if (enabled) "on" else "off"} via public API")
                return@withContext true
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Public API failed: ${t.message}")
        }

        // Спроба 2: через Shizuku
        try {
            val shizuku = ShizukuManager(context)
            if (shizuku.isShizukuAvailable() && shizuku.hasPermission()) {
                val ok = setWifiViaShizuku(enabled)
                if (ok) return@withContext true
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Shizuku WiFi failed: ${t.message}")
        }

        false
    }

    private fun setWifiViaShizuku(enabled: Boolean): Boolean {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val method: Method = wm.javaClass.getMethod("setWifiEnabled", Boolean::class.javaPrimitiveType)
            @Suppress("DEPRECATION")
            val result = method.invoke(wm, enabled) as? Boolean ?: false
            Log.d(TAG, "WiFi ${if (enabled) "on" else "off"} via reflection: $result")
            result
        } catch (t: Throwable) {
            Log.e(TAG, "setWifiViaShizuku failed", t)
            false
        }
    }

    // ===== Bluetooth =====
    fun isBluetoothEnabled(): Boolean {
        return try {
            BluetoothAdapter.getDefaultAdapter()?.isEnabled ?: false
        } catch (t: Throwable) {
            false
        }
    }

    suspend fun setBluetoothEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@withContext false

            @Suppress("MissingPermission")
            val ok = if (enabled) {
                adapter.enable()
            } else {
                adapter.disable()
            }
            Log.d(TAG, "BT ${if (enabled) "on" else "off"}: $ok")
            ok
        } catch (t: Throwable) {
            Log.e(TAG, "setBluetoothEnabled failed", t)
            false
        }
    }

    // ===== Яскравість =====
    fun getBrightness(): Int {
        return try {
            android.provider.Settings.System.getInt(
                context.contentResolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS,
                128
            )
        } catch (t: Throwable) { 128 }
    }

    fun setBrightness(level: Int): Boolean {
        return try {
            android.provider.Settings.System.putInt(
                context.contentResolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS,
                level.coerceIn(0, 255)
            )
            true
        } catch (t: Throwable) {
            Log.e(TAG, "setBrightness failed", t)
            false
        }
    }

    // ===== DND (Не турбувати) =====
    fun setDnd(enabled: Boolean): Boolean {
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as android.app.NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                if (!nm.isNotificationPolicyAccessGranted) {
                    // Треба дати дозвіл — відкриваємо налаштування
                    try {
                        context.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Throwable) {}
                    return false
                }
                nm.setInterruptionFilter(
                    if (enabled) android.app.NotificationManager.INTERRUPTION_FILTER_NONE
                    else android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                )
                true
            } else false
        } catch (t: Throwable) {
            Log.e(TAG, "setDnd failed", t)
            false
        }
    }
}
