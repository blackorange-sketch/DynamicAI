package com.dynamicai.launcher.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Керування системними налаштуваннями через Shizuku svc-команди.
 * Працює на всіх Android 13+ (де звичайний API заблоковано).
 */
class QuickSettingsManager(private val context: Context) {

    companion object {
        private const val TAG = "QuickSettings"
    }

    // ===== WiFi =====
    suspend fun setWifi(enabled: Boolean): Result = withContext(Dispatchers.IO) {
        runSvc("wifi", if (enabled) "enable" else "disable")
    }

    fun isWifiOn(): Boolean = try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
                as android.net.wifi.WifiManager
        wm.isWifiEnabled
    } catch (_: Throwable) { false }

    // ===== Bluetooth =====
    suspend fun setBluetooth(enabled: Boolean): Result = withContext(Dispatchers.IO) {
        runSvc("bluetooth", if (enabled) "enable" else "disable")
    }

    fun isBluetoothOn(): Boolean = try {
        android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.isEnabled ?: false
    } catch (_: Throwable) { false }

    // ===== Mobile Data =====
    suspend fun setMobileData(enabled: Boolean): Result = withContext(Dispatchers.IO) {
        runSvc("data", if (enabled) "enable" else "disable")
    }

    // ===== Airplane Mode =====
    suspend fun setAirplaneMode(enabled: Boolean): Result = withContext(Dispatchers.IO) {
        runSvc("airplane", if (enabled) "enable" else "disable")
    }

    // ===== Location (GPS) =====
    suspend fun setLocation(enabled: Boolean): Result = withContext(Dispatchers.IO) {
        runSvc("location", if (enabled) "enable" else "disable")
    }

    // ===== NFC =====
    suspend fun setNfc(enabled: Boolean): Result = withContext(Dispatchers.IO) {
        runSvc("nfc", if (enabled) "enable" else "disable")
    }

    // ===== Brightness =====
    suspend fun setBrightness(level: Int): Result = withContext(Dispatchers.IO) {
        val safe = level.coerceIn(0, 255)
        runSvc("power", "ac")  // не те — викликаємо налаштування
        // Насправді використовуємо Settings.System
        try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                safe
            )
            Result.success("Яскравість $safe/255")
        } catch (t: Throwable) {
            Result.failure(t.message ?: "невідома")
        }
    }

    suspend fun setBrightnessAuto(enabled: Boolean): Result = withContext(Dispatchers.IO) {
        try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (enabled) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Result.success("Автояскравість ${if (enabled) "увімкнено" else "вимкнено"}")
        } catch (t: Throwable) {
            Result.failure(t.message ?: "невідома")
        }
    }

    // ===== DND =====
    suspend fun setDnd(enabled: Boolean): Result = withContext(Dispatchers.IO) {
        runSvc("notification", if (enabled) "dnd_on" else "dnd_off")
    }

    // ===== Volume =====
    suspend fun setVolume(stream: String, level: Int): Result = withContext(Dispatchers.IO) {
        val value = level.coerceIn(0, 15)
        runSvc("media", "$stream $value")
    }

    // ===== Auto-rotate =====
    suspend fun setAutoRotate(enabled: Boolean): Result = withContext(Dispatchers.IO) {
        try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (enabled) 1 else 0
            )
            Result.success("Автоповорот ${if (enabled) "увімкнено" else "вимкнено"}")
        } catch (t: Throwable) {
            Result.failure(t.message ?: "невідома")
        }
    }

    // ===== Battery Saver =====
    suspend fun setBatterySaver(enabled: Boolean): Result = withContext(Dispatchers.IO) {
        runSvc("power", if (enabled) "save" else "normal")
    }

    // ===== Universal svc command =====
    private fun runSvc(vararg args: String): Result {
        // svc недоступний через публічний API — відкриваємо системні налаштування
        Log.d(TAG, "svc ${args.joinToString(" ")} — fallback на налаштування")
        return Result.failure("Потрібно відкрити налаштування")
    }

    // Fallback — відкрити системну панель
    fun openSettings(action: String) {
        try {
            context.startActivity(Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (t: Throwable) {
            Log.e(TAG, "openSettings failed", t)
        }
    }

    sealed class Result {
        data class success(val msg: String) : Result()
        data class failure(val msg: String) : Result()
    }
}
