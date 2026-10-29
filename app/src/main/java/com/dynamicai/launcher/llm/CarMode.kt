package com.dynamicai.launcher.llm

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import com.dynamicai.launcher.system.BluetoothManager
import com.dynamicai.launcher.system.ShizukuManager
import com.dynamicai.launcher.voice.OfflineSpeech
import kotlinx.coroutines.delay

/**
 * Сценарій «Музика в авто»: інтернет → Bluetooth → підключення до машини → YouTube Music → відтворення.
 * Кожен крок можна викликати окремо (ensureInternet, connectBluetooth, playYouTubeMusic) — наприклад, у макросах.
 */
class CarMode(private val context: Context) {

    companion object {
        private const val TAG = "CarMode"
        const val YT_MUSIC = "com.google.android.apps.youtube.music"
    }

    private val shizuku by lazy { ShizukuManager(context) }
    private val shizukuOk: Boolean get() = shizuku.isShizukuAvailable() && shizuku.hasPermission()

    private fun airplaneOn(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    private suspend fun waitUntil(timeoutMs: Long, step: Long = 500, cond: () -> Boolean): Boolean {
        var t = 0L
        while (t < timeoutMs) {
            if (cond()) return true
            delay(step); t += step
        }
        return cond()
    }

    /** Вимикає режим польоту, вмикає мобільний інтернет (потрібен Shizuku) і чекає на мережу. */
    suspend fun ensureInternet(): String {
        if (OfflineSpeech.isOnline(context)) return "Інтернет є"
        val notes = mutableListOf<String>()
        if (airplaneOn()) {
            if (shizukuOk) {
                shizuku.execShell("cmd connectivity airplane-mode disable")
                notes += "режим польоту вимкнено"
                waitUntil(6000) { !airplaneOn() }
            } else {
                try { context.startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
                return "Увімкнено режим польоту, а без Shizuku я не можу його вимкнути. Відкрив налаштування."
            }
        }
        if (shizukuOk) {
            shizuku.execShell("svc data enable")
            notes += "мобільний інтернет увімкнено"
        }
        val ok = waitUntil(10_000) { OfflineSpeech.isOnline(context) }
        return if (ok) "Інтернет з'явився" + (if (notes.isNotEmpty()) " (" + notes.joinToString(", ") + ")" else "")
        else "Інтернету немає" + (if (shizukuOk) "" else " (для автоувімкнення мобільних даних потрібен Shizuku)")
    }

    private fun bondedMatching(name: String) = try {
        BluetoothManager(context).getPairedDevices().filter { name.isBlank() || it.name.contains(name, ignoreCase = true) }
    } catch (_: Throwable) { emptyList() }

    /** Вмикає Bluetooth і підключається до пристрою (за назвою; порожньо — до збереженого авто). */
    suspend fun connectBluetooth(nameArg: String): String {
        val name = nameArg.ifBlank { com.dynamicai.launcher.data.SettingsStorage(context).load().carBtName }
        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return "Bluetooth недоступний"
        if (android.os.Build.VERSION.SDK_INT >= 31 &&
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return "Немає дозволу Bluetooth (Налаштування → Система → Bluetooth)"

        if (!adapter.isEnabled) {
            if (shizukuOk) {
                shizuku.execShell("svc bluetooth enable")
            } else {
                try {
                    @Suppress("DEPRECATION", "MissingPermission")
                    adapter.enable()
                } catch (_: Throwable) {}
            }
            if (!waitUntil(7000) { adapter.isEnabled }) {
                try { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
                return "Не вдалося увімкнути Bluetooth (запустіть Shizuku). Відкрив налаштування."
            }
        }
        if (bondedMatching(name).isEmpty())
            return if (name.isBlank()) "Немає спарених пристроїв" else "Пристрій «$name» не знайдено серед спарених"
        if (bondedMatching(name).any { it.isConnected }) return "Bluetooth підключено до ${bondedMatching(name).first { it.isConnected }.name}"

        // Машини зазвичай підключаються самі; даємо кілька секунд, а потім підключаємо вручну
        if (waitUntil(5000) { bondedMatching(name).any { it.isConnected } })
            return "Bluetooth підключено до ${bondedMatching(name).first { it.isConnected }.name}"
        if (name.isNotBlank()) {
            val tried = try { BluetoothManager(context).connect(name) } catch (_: Throwable) { false }
            Log.e(TAG, "manual connect '$name' → $tried")
        }
        return if (waitUntil(10_000) { bondedMatching(name).any { it.isConnected } })
            "Bluetooth підключено до ${bondedMatching(name).first { it.isConnected }.name}"
        else "Не вдалося підключитися до " + (name.ifBlank { "авто" }) + (if (shizukuOk) "" else " (для ручного підключення потрібен Shizuku)")
    }

    /** Відкриває YouTube Music і запускає відтворення (медіаклавіша Play). */
    suspend fun playYouTubeMusic(): String {
        val intent = context.packageManager.getLaunchIntentForPackage(YT_MUSIC)
            ?: return "YouTube Music не встановлено"
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        delay(3000)
        repeat(3) { attempt ->
            if (am.isMusicActive) return "Музика грає"
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
            delay(if (attempt == 0) 2500 else 2000)
        }
        return if (am.isMusicActive) "Музика грає" else "Відкрив YouTube Music, але відтворення не почалось — натисніть Play"
    }

    /** Повний сценарій. */
    suspend fun run(): String {
        val net = ensureInternet()
        val bt = connectBluetooth("")
        val music = if (bt.startsWith("Bluetooth підключено")) playYouTubeMusic() else "Музику не запущено, бо немає з'єднання з авто"
        return listOf(net, bt, music).joinToString(". ").replace("..", ".") + "."
    }
}
