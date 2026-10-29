package com.dynamicai.launcher.macro

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Які дозволи потрібні для тригера/кроку макросу і чи вони вже надані. */
object TriggerPermissions {
    data class Need(val title: String, val text: String, val runtime: String? = null, val settingsAction: String? = null)

    fun hasUsageAccess(ctx: Context): Boolean = try {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) == AppOpsManager.MODE_ALLOWED
    } catch (_: Throwable) { false }

    fun hasNotificationListener(ctx: Context): Boolean = try {
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    } catch (_: Throwable) { false }

    private fun granted(ctx: Context, perm: String) = ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    /** Повертає опис відсутнього дозволу або null, якщо все гаразд. */
    fun missing(ctx: Context, item: String): Need? {
        val kind = item.substringBefore(':').substringBefore('(')
        val arg = item.substringAfter(':', "")
        return when (kind) {
            "app_opened", "app_closed" ->
                if (hasUsageAccess(ctx)) null else Need(
                    "Доступ до статистики використання",
                    "Щоб знати, який додаток відкрито, потрібен спеціальний доступ. У списку знайдіть DynamicAI і увімкніть перемикач.",
                    settingsAction = Settings.ACTION_USAGE_ACCESS_SETTINGS
                )
            "notification" ->
                if (hasNotificationListener(ctx)) null else Need(
                    "Доступ до сповіщень",
                    "Щоб реагувати на сповіщення інших додатків, увімкніть DynamicAI у списку доступу до сповіщень.",
                    settingsAction = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                )
            "sms_received" ->
                if (granted(ctx, Manifest.permission.RECEIVE_SMS)) null else Need("Дозвіл на SMS", "Потрібен для реакції на вхідні SMS.", runtime = Manifest.permission.RECEIVE_SMS)
            "sendSms" ->
                if (granted(ctx, Manifest.permission.SEND_SMS)) null else Need("Дозвіл на надсилання SMS", "Потрібен для кроку «Надіслати SMS».", runtime = Manifest.permission.SEND_SMS)
            "call_incoming", "call_ended" ->
                if (granted(ctx, Manifest.permission.READ_PHONE_STATE)) null else Need("Дозвіл на стан телефону", "Потрібен, щоб знати про вхідні дзвінки.", runtime = Manifest.permission.READ_PHONE_STATE)
            "nfc" -> {
                val a = try { NfcAdapter.getDefaultAdapter(ctx) } catch (_: Throwable) { null }
                if (a == null || a.isEnabled) null else Need("NFC вимкнено", "Увімкніть NFC, щоб телефон міг зчитувати мітки.", settingsAction = Settings.ACTION_NFC_SETTINGS)
            }
            "wifi_connected", "wifi_disconnected" ->
                if (arg.isBlank() || granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)) null
                else Need("Дозвіл на геолокацію", "Android показує назву Wi-Fi мережі лише з дозволом на геолокацію.", runtime = Manifest.permission.ACCESS_FINE_LOCATION)
            "bt_connected", "bt_disconnected", "bt_on", "bt_off", "connectBluetooth" ->
                if (Build.VERSION.SDK_INT < 31 || granted(ctx, Manifest.permission.BLUETOOTH_CONNECT)) null
                else Need("Дозвіл Bluetooth", "Потрібен для роботи з Bluetooth-пристроями.", runtime = Manifest.permission.BLUETOOTH_CONNECT)
            else -> null
        }
    }
}
