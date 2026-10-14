package com.dynamicai.launcher.system

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.IBinder
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Керування Bluetooth через Shizuku (прихований API).
 */
class BluetoothManager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothManager"
    }

    data class PairedDevice(
        val name: String,
        val address: String,
        val isConnected: Boolean
    )

    private val adapter: BluetoothAdapter?
        get() = BluetoothAdapter.getDefaultAdapter()

    /**
     * Список спарених пристроїв.
     */
    fun getPairedDevices(): List<PairedDevice> {
        return try {
            @Suppress("MissingPermission")
            adapter?.bondedDevices?.map { device ->
                @Suppress("MissingPermission")
                PairedDevice(
                    name = device.name ?: "Невідомий",
                    address = device.address,
                    isConnected = isConnected(device)
                )
            } ?: emptyList()
        } catch (t: Throwable) {
            Log.e(TAG, "getPairedDevices failed", t)
            emptyList()
        }
    }

    private fun isConnected(device: BluetoothDevice): Boolean {
        return try {
            @Suppress("MissingPermission")
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    /**
     * Підключити пристрій за назвою (через прихований A2DP API).
     */
    suspend fun connect(name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            @Suppress("MissingPermission")
            val device = adapter?.bondedDevices?.firstOrNull {
                it.name?.contains(name, ignoreCase = true) == true
            } ?: run {
                Log.d(TAG, "Device '$name' not found")
                return@withContext false
            }

            Log.d(TAG, "Connecting to ${device.name} (${device.address})")

            // Спроба 1: через IBluetoothA2dp (прихований API)
            if (connectViaA2dp(device)) return@withContext true

            // Спроба 2: через BluetoothHeadset (HFP)
            if (connectViaHfp(device)) return@withContext true

            Log.e(TAG, "All connect methods failed")
            false
        } catch (t: Throwable) {
            Log.e(TAG, "connect failed", t)
            false
        }
    }

    private fun connectViaA2dp(device: BluetoothDevice): Boolean {
        return try {
            val binder = SystemServiceHelper.getSystemService("bluetooth_a2dp")
            val wrapped = ShizukuBinderWrapper(binder)

            val stubClass = Class.forName("android.bluetooth.IBluetoothA2dp\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val a2dp = asInterface.invoke(null, wrapped) ?: return false

            val connect = a2dp.javaClass.getMethod("connect", BluetoothDevice::class.java)
            connect.invoke(a2dp, device)
            Log.d(TAG, "A2DP connect OK")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "connectViaA2dp failed: ${t.message}")
            false
        }
    }

    private fun connectViaHfp(device: BluetoothDevice): Boolean {
        return try {
            val binder = SystemServiceHelper.getSystemService("bluetooth_headset")
            val wrapped = ShizukuBinderWrapper(binder)

            val stubClass = Class.forName("android.bluetooth.IBluetoothHeadset\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val headset = asInterface.invoke(null, wrapped) ?: return false

            val connect = headset.javaClass.getMethod("connect", BluetoothDevice::class.java)
            connect.invoke(headset, device)
            Log.d(TAG, "HFP connect OK")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "connectViaHfp failed: ${t.message}")
            false
        }
    }

    /**
     * Відключити пристрій.
     */
    suspend fun disconnect(name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            @Suppress("MissingPermission")
            val device = adapter?.bondedDevices?.firstOrNull {
                it.name?.contains(name, ignoreCase = true) == true
            } ?: return@withContext false

            // Спробувати через A2DP
            try {
                val serviceManager = Class.forName("android.os.ServiceManager")
                val getService = serviceManager.getMethod("getService", String::class.java)
                val binder = getService.invoke(null, "bluetooth_a2dp") as? IBinder ?: return@withContext false
                val stubClass = Class.forName("android.bluetooth.IBluetoothA2dp\$Stub")
                val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
                val a2dp = asInterface.invoke(null, binder) ?: return@withContext false
                val disconnect = a2dp.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                disconnect.invoke(a2dp, device)
                true
            } catch (t: Throwable) {
                Log.e(TAG, "disconnect failed", t)
                false
            }
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Діагностика — що доступно.
     */
    fun getDiagnostic(): String {
        return try {
            val devices = getPairedDevices()
            buildString {
                append("Adapter: ").append(if (adapter != null) "OK" else "NULL").append("\n")
                append("Enabled: ").append(adapter?.isEnabled ?: false).append("\n")
                append("Paired: ").append(devices.size).append("\n")
                devices.forEach { d ->
                    append("  • ").append(d.name)
                    append(" (").append(d.address).append(")")
                    append(" — ").append(if (d.isConnected) "connected" else "not connected")
                    append("\n")
                }
            }
        } catch (t: Throwable) {
            "Error: ${t.message}"
        }
    }
}
