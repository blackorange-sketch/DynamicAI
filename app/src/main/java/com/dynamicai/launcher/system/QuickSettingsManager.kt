package com.dynamicai.launcher.system

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Керування системними налаштуваннями.
 */
class QuickSettingsManager(private val context: Context) {

    companion object {
        private const val TAG = "QuickSettings"
    }

    private val wifiController by lazy { WifiController(context) }

    sealed class Result {
        data class success(val msg: String) : Result()
        data class failure(val msg: String) : Result()
    }

    // ===== WiFi =====
    suspend fun setWifi(enabled: Boolean): Result = withContext(Dispatchers.IO) {
        // 1. Shizuku
        try {
            val ok = wifiController.setWifi(enabled)
            if (ok) return@withContext Result.success("OK")
        } catch (t: Throwable) {
            Log.d(TAG, "Shizuku wifi failed: ${t.message}")
        }

        // 2. Публічний API
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
                    as android.net.wifi.WifiManager
            @Suppress("DEPRECATION")
            val ok = wm.setWifiEnabled(enabled)
            if (ok) return@withContext Result.success("OK")
        } catch (_: Throwable) {}

        // 3. Fallback
        openSettings(Settings.ACTION_WIFI_SETTINGS)
        Result.failure("Shizuku не активний — відкрив налаштування")
    }

    fun isWifiOn(): Boolean = try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
                as android.net.wifi.WifiManager
        wm.isWifiEnabled
    } catch (_: Throwable) { false }

    // ===== Bluetooth =====
    suspend fun setBluetooth(enabled: Boolean): Result = withContext(Dispatchers.IO) {
        try {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure("BT недоступний")
            @Suppress("MissingPermission")
            val ok = if (enabled) adapter.enable() else adapter.disable()
            if (ok) Result.success("OK") else Result.failure("Не вдалось")
        } catch (t: Throwable) {
            openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
            Result.failure("Відкрив налаштування Bluetooth")
        }
    }

    fun isBluetoothOn(): Boolean = try {
        android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.isEnabled ?: false
    } catch (_: Throwable) { false }

    // ===== Mobile Data =====
    suspend fun setMobileData(enabled: Boolean): Result = withContext(Dispatchers.IO) {
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE)
                    as android.telephony.TelephonyManager
            val method = tm.javaClass.getMethod("setDataEnabled", Boolean::class.javaPrimitiveType)
            method.invoke(tm, enabled)
            Result.success("OK")
        } catch (t: Throwable) {
            openSettings(Settings.ACTION_DATA_ROAMING_SETTINGS)
            Result.failure("Відкрив налаштування")
        }
    }

    // ===== Airplane =====
    suspend fun setAirplaneMode(enabled: Boolean): Result = withContext(Dispatchers.IO) {
        try {
            openSettings(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            Result.failure("Відкрив налаштування авіарежиму")
        } catch (t: Throwable) {
            Result.failure(t.message ?: "помилка")
        }
    }

    // ===== Location =====
    suspend fun setLocation(enabled: Boolean): Result = withContext(Dispatchers.IO) {
        try {
            openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            Result.failure("Відкрив налаштування локації")
        } catch (t: Throwable) {
            Result.failure(t.message ?: "помилка")
        }
    }

    // ===== NFC =====
    suspend fun setNfc(enabled: Boolean): Result = withContext(Dispatchers.IO) {
        try {
            openSettings(Settings.ACTION_NFC_SETTINGS)
            Result.failure("Відкрив налаштування NFC")
        } catch (t: Throwable) {
            Result.failure(t.message ?: "помилка")
        }
    }

    // ===== DND =====
    suspend fun setDnd(enabled: Boolean): Result = withContext(Dispatchers.IO) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as android.app.NotificationManager
            if (Build.VERSION.SDK_INT >= 23) {
                if (!nm.isNotificationPolicyAccessGranted) {
                    openSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    return@withContext Result.failure("Дай дозвіл і повтори")
                }
                nm.setInterruptionFilter(
                    if (enabled) android.app.NotificationManager.INTERRUPTION_FILTER_NONE
                    else android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                )
                Result.success("OK")
            } else Result.failure("Не підтримується")
        } catch (t: Throwable) {
            Result.failure(t.message ?: "помилка")
        }
    }

    // ===== Brightness =====
    suspend fun setBrightness(level: Int): Result = withContext(Dispatchers.IO) {
        try {
            if (!Settings.System.canWrite(context)) {
                openSettings(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                return@withContext Result.failure("Дай дозвіл WRITE_SETTINGS")
            }
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                level.coerceIn(0, 255)
            )
            Result.success("Яскравість $level/255")
        } catch (t: Throwable) {
            Result.failure(t.message ?: "помилка")
        }
    }

    suspend fun setBrightnessAuto(enabled: Boolean): Result = withContext(Dispatchers.IO) {
        try {
            if (!Settings.System.canWrite(context)) {
                openSettings(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                return@withContext Result.failure("Дай дозвіл WRITE_SETTINGS")
            }
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (enabled) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Result.success("OK")
        } catch (t: Throwable) {
            Result.failure(t.message ?: "помилка")
        }
    }

    // ===== Auto-rotate =====
    suspend fun setAutoRotate(enabled: Boolean): Result = withContext(Dispatchers.IO) {
        try {
            if (!Settings.System.canWrite(context)) {
                openSettings(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                return@withContext Result.failure("Дай дозвіл WRITE_SETTINGS")
            }
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (enabled) 1 else 0
            )
            Result.success("OK")
        } catch (t: Throwable) {
            Result.failure(t.message ?: "помилка")
        }
    }

    // ===== Battery Saver =====
    suspend fun setBatterySaver(enabled: Boolean): Result = withContext(Dispatchers.IO) {
        try {
            openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            Result.failure("Відкрив налаштування батареї")
        } catch (t: Throwable) {
            Result.failure(t.message ?: "помилка")
        }
    }

    private fun openSettings(action: String) {
        try {
            context.startActivity(Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (t: Throwable) {
            Log.e(TAG, "openSettings failed", t)
        }
    }
}
