package com.dynamicai.launcher.macro

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.dynamicai.launcher.system.QuickSettingsManager
import com.dynamicai.launcher.system.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Додаткові дії макросів: вібрація, режим дзвінка, DND, дані, політ, сповіщення, озвучення, HTTP, shell (Shizuku). */
class MacroTools(private val context: Context) {

    private val shizuku by lazy { ShizukuManager(context) }
    private val shizukuOk: Boolean get() = shizuku.isShizukuAvailable() && shizuku.hasPermission()

    fun vibrate(ms: Long): String {
        val v = context.getSystemService(Vibrator::class.java) ?: return "Немає вібромотора"
        v.vibrate(VibrationEffect.createOneShot(ms.coerceIn(50, 5000), VibrationEffect.DEFAULT_AMPLITUDE))
        return "Вібрація"
    }

    fun setRingerMode(mode: String): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return try {
            am.ringerMode = when (mode.lowercase()) {
                "silent", "тихо" -> AudioManager.RINGER_MODE_SILENT
                "vibrate", "вібро" -> AudioManager.RINGER_MODE_VIBRATE
                else -> AudioManager.RINGER_MODE_NORMAL
            }
            "Режим дзвінка: $mode"
        } catch (_: SecurityException) {
            try { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
            "Немає дозволу «Не турбувати» — відкрив налаштування"
        }
    }

    suspend fun setDnd(on: Boolean): String {
        return when (val r = QuickSettingsManager(context).setDnd(on)) {
            is QuickSettingsManager.Result.success -> "Не турбувати ${if (on) "увімкнено" else "вимкнено"}"
            is QuickSettingsManager.Result.failure -> "Не вдалося: ${r.msg}"
        }
    }

    suspend fun setMobileData(on: Boolean): String {
        if (shizukuOk) {
            shizuku.execShell("svc data ${if (on) "enable" else "disable"}")
            return "Мобільні дані ${if (on) "увімкнено" else "вимкнено"}"
        }
        return when (val r = QuickSettingsManager(context).setMobileData(on)) {
            is QuickSettingsManager.Result.success -> "Мобільні дані ${if (on) "увімкнено" else "вимкнено"}"
            is QuickSettingsManager.Result.failure -> "Не вдалося: ${r.msg}"
        }
    }

    suspend fun setAirplane(on: Boolean): String {
        if (shizukuOk) {
            shizuku.execShell("cmd connectivity airplane-mode ${if (on) "enable" else "disable"}")
            return "Режим польоту ${if (on) "увімкнено" else "вимкнено"}"
        }
        try { context.startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
        return "Не вдалося змінити режим польоту без Shizuku — відкрив налаштування"
    }

    suspend fun shell(cmd: String): String {
        if (!shizukuOk) return "Немає Shizuku (запустіть і дайте дозвіл)"
        return shizuku.execShell(cmd).trim().take(300).ifBlank { "Виконано" }
    }

    suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000; readTimeout = 8_000
                setRequestProperty("User-Agent", "DynamicAI/1.0")
            }
            val code = conn.responseCode
            val body = try { conn.inputStream.bufferedReader().use { it.readText() } } catch (_: Throwable) { "" }
            conn.disconnect()
            "HTTP $code" + (if (body.isNotBlank()) ": " + body.trim().take(200) else "")
        } catch (t: Throwable) {
            "Помилка: ${t.message}"
        }
    }

    fun notify(title: String, text: String): String {
        if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            return "Немає дозволу на сповіщення"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel("macro_notify") == null)
            nm.createNotificationChannel(NotificationChannel("macro_notify", "Макроси", NotificationManager.IMPORTANCE_DEFAULT))
        val n = android.app.Notification.Builder(context, "macro_notify")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title.ifBlank { "DynamicAI" })
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis() % 100000).toInt() + 9000, n)
        return "Сповіщення: $title"
    }

    fun speak(text: String): String {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context.applicationContext) { status ->
            val t = tts
            if (status == TextToSpeech.SUCCESS && t != null) {
                t.language = Locale("uk", "UA")
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { t.shutdown() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { t.shutdown() }
                })
                t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "macro_say")
            } else t?.shutdown()
        }
        return "Озвучую: $text"
    }

    fun sendSms(number: String, text: String): String {
        if (number.isBlank() || text.isBlank()) return "Потрібні номер і текст"
        if (context.checkSelfPermission(android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            return "Немає дозволу на надсилання SMS"
        return try {
            val sm = if (android.os.Build.VERSION.SDK_INT >= 31) context.getSystemService(android.telephony.SmsManager::class.java)
            else @Suppress("DEPRECATION") android.telephony.SmsManager.getDefault()
            sm.sendMultipartTextMessage(number, null, sm.divideMessage(text), null, null)
            "SMS надіслано на $number"
        } catch (t: Throwable) { "Не вдалося надіслати SMS: ${t.message}" }
    }

    fun copyText(text: String): String = try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("macro", text))
        "Скопійовано"
    } catch (t: Throwable) { "Не вдалося скопіювати: ${t.message}" }

    suspend fun setNfc(on: Boolean): String {
        if (shizukuOk) {
            shizuku.execShell("svc nfc ${if (on) "enable" else "disable"}")
            return "NFC ${if (on) "увімкнено" else "вимкнено"}"
        }
        try { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
        return "Без Shizuku NFC не перемикається автоматично — відкрив налаштування"
    }

    suspend fun setLocation(on: Boolean): String {
        if (shizukuOk) {
            shizuku.execShell("cmd location set-location-enabled ${if (on) "true" else "false"}")
            return "Геолокація ${if (on) "увімкнена" else "вимкнена"}"
        }
        try { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
        return "Без Shizuku геолокація не перемикається автоматично — відкрив налаштування"
    }

    fun setAutoRotate(on: Boolean): String {
        if (!Settings.System.canWrite(context)) {
            try { context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
            return "Немає дозволу змінювати налаштування системи"
        }
        Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
        return "Автоповорот ${if (on) "увімкнено" else "вимкнено"}"
    }

    suspend fun forceStop(pkg: String): String {
        if (pkg.isBlank()) return "Не вказано додаток"
        if (!shizukuOk) return "Потрібен Shizuku, щоб примусово закрити додаток"
        shizuku.execShell("am force-stop $pkg")
        return "Додаток закрито: ${AppUtils.labelFor(context, pkg)}"
    }
}
