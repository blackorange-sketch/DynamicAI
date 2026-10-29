package com.dynamicai.launcher.macro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.dynamicai.launcher.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Автозапуск макросів за подіями (як у MacroDroid): зарядка, батарея, Bluetooth, Wi-Fi, навушники,
 * екран, режим польоту, час, запуск телефону. Працює як foreground-служба лише коли є макроси з тригерами.
 */
class MacroTriggerService : Service() {

    companion object {
        private const val TAG = "MacroTriggers"
        private const val CHANNEL_ID = "macro_triggers"
        private const val NOTIF_ID = 7722

        fun needed(ctx: Context): Boolean = try {
            val st = MacroStorage(ctx)
            st.autoEnabled() && st.get().any { it.enabled && it.triggers.isNotEmpty() }
        } catch (_: Throwable) { false }

        fun sync(ctx: Context, fromBoot: Boolean = false) {
            if (needed(ctx)) start(ctx, fromBoot) else stop(ctx)
        }

        fun start(ctx: Context, fromBoot: Boolean = false) {
            try {
                val i = Intent(ctx, MacroTriggerService::class.java).putExtra("boot", fromBoot)
                ctx.startForegroundService(i)
            } catch (t: Throwable) { Log.e(TAG, "start failed: ${t.message}") }
        }

        fun stop(ctx: Context) {
            try { ctx.stopService(Intent(ctx, MacroTriggerService::class.java)) } catch (_: Throwable) {}
        }

        /** Чи збігається тригер макросу з подією. */
        fun matches(trigger: String, event: String, p: Map<String, String>): Boolean {
            val kind = trigger.substringBefore(':')
            val arg = trigger.substringAfter(':', "")
            return when (kind) {
                "bt_connected", "bt_disconnected" ->
                    event == kind && (arg.isBlank() || p["device"]?.contains(arg, ignoreCase = true) == true)
                "wifi_connected", "wifi_disconnected" ->
                    event == kind && (arg.isBlank() || p["ssid"]?.contains(arg, ignoreCase = true) == true)
                "app_opened", "app_closed" -> event == kind && (arg.isBlank() || p["pkg"] == arg)
                "notification" -> {
                    val pkg = arg.substringBefore(':'); val txt = arg.substringAfter(':', "")
                    event == "notification" && (pkg.isBlank() || p["pkg"] == pkg) &&
                        (txt.isBlank() || ((p["title"] ?: "") + " " + (p["text"] ?: "")).contains(txt, ignoreCase = true))
                }
                "sms_received" -> event == kind && (arg.isBlank() || p["sender"]?.contains(arg, ignoreCase = true) == true)
                "nfc" -> event == kind && (arg.isBlank() || p["id"].equals(arg, ignoreCase = true))
                "every" -> {
                    val n = arg.toLongOrNull() ?: return false
                    event == "every" && n > 0 && (p["min"]?.toLongOrNull() ?: 1L) % n == 0L
                }
                "battery_low" -> {
                    val n = arg.toIntOrNull() ?: 20
                    event == "battery_low" && (p["prev"]?.toIntOrNull() ?: 0) >= n && (p["level"]?.toIntOrNull() ?: 100) < n
                }
                "time" -> {
                    if (event != "time") return false
                    val parts = arg.split(':')
                    val h = parts.getOrNull(0)?.toIntOrNull() ?: return false
                    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    val days = parts.getOrNull(2).orEmpty()
                    h == p["h"]?.toIntOrNull() && m == p["m"]?.toIntOrNull() && (days.isBlank() || days.contains(p["dow"] ?: "?"))
                }
                else -> event == kind
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val lastRun = HashMap<String, Long>()
    private var startedAt = 0L
    private var lastLevel = -1
    private var netCb: ConnectivityManager.NetworkCallback? = null
    private var audioCb: AudioDeviceCallback? = null
    private var lastSsid = ""
    private var telRegistered = false
    private var sensorListener: android.hardware.SensorEventListener? = null
    private var lastCallState = ""

    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_POWER_CONNECTED -> fire("charging_start")
                Intent.ACTION_POWER_DISCONNECTED -> fire("charging_stop")
                Intent.ACTION_SCREEN_ON -> fire("screen_on")
                Intent.ACTION_SCREEN_OFF -> fire("screen_off")
                Intent.ACTION_USER_PRESENT -> fire("unlock")
                Intent.ACTION_AIRPLANE_MODE_CHANGED -> fire(if (i.getBooleanExtra("state", false)) "airplane_on" else "airplane_off")
                BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val name = try {
                        @Suppress("MissingPermission")
                        i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)?.name ?: ""
                    } catch (_: Throwable) { "" }
                    fire(if (i.action == BluetoothDevice.ACTION_ACL_CONNECTED) "bt_connected" else "bt_disconnected", mapOf("device" to name))
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = i.getIntExtra("level", -1) * 100 / i.getIntExtra("scale", 100).coerceAtLeast(1)
                    if (lastLevel >= 0 && level != lastLevel) fire("battery_low", mapOf("level" to level.toString(), "prev" to lastLevel.toString()))
                    if (lastLevel in 0..99 && level >= 100) fire("battery_full", mapOf("level" to "100"))
                    lastLevel = level
                }
                android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (i.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, -1)) {
                        android.bluetooth.BluetoothAdapter.STATE_ON -> fire("bt_on")
                        android.bluetooth.BluetoothAdapter.STATE_OFF -> fire("bt_off")
                    }
                }
                android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                    val st = i.getStringExtra(android.telephony.TelephonyManager.EXTRA_STATE).orEmpty()
                    if (st != lastCallState) {
                        if (st == android.telephony.TelephonyManager.EXTRA_STATE_RINGING) fire("call_incoming")
                        if (st == android.telephony.TelephonyManager.EXTRA_STATE_IDLE && lastCallState.isNotEmpty() && lastCallState != android.telephony.TelephonyManager.EXTRA_STATE_IDLE) fire("call_ended")
                        lastCallState = st
                    }
                }
                android.provider.Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                    try {
                        val msgs = android.provider.Telephony.Sms.Intents.getMessagesFromIntent(i)
                        val sender = msgs.firstOrNull()?.originatingAddress.orEmpty()
                        val body = msgs.joinToString("") { it.messageBody.orEmpty() }
                        fire("sms_received", mapOf("sender" to sender, "body" to body))
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startedAt = System.currentTimeMillis()
        createChannel()
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIF_ID, n)

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED); addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED); addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        registerReceiver(rx, f, Context.RECEIVER_EXPORTED)
        registerTelephony()
        refreshSensors()
        startAppWatcher()

        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            netCb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    lastSsid = currentSsid()
                    fire("wifi_connected", mapOf("ssid" to lastSsid))
                }
                override fun onLost(network: Network) { fire("wifi_disconnected", mapOf("ssid" to lastSsid)) }
            }
            cm.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), netCb!!)
        } catch (t: Throwable) { Log.e(TAG, "net cb: ${t.message}") }

        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            fun wired(d: AudioDeviceInfo) = d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || d.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || d.type == AudioDeviceInfo.TYPE_USB_HEADSET
            val cb = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) { if (added.any { wired(it) }) fire("headset_plugged") }
                override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) { if (removed.any { wired(it) }) fire("headset_unplugged") }
            }
            audioCb = cb
            am.registerAudioDeviceCallback(cb, null)
        } catch (t: Throwable) { Log.e(TAG, "audio cb: ${t.message}") }

        // Тригери за часом: перевірка раз на 15 с, спрацьовує один раз на хвилину
        scope.launch {
            var lastKey = ""
            while (true) {
                val c = Calendar.getInstance()
                val key = "${c.get(Calendar.DAY_OF_YEAR)}-${c.get(Calendar.HOUR_OF_DAY)}-${c.get(Calendar.MINUTE)}"
                if (key != lastKey) {
                    lastKey = key
                    val dow = ((c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1).toString() // 1 = Пн … 7 = Нд
                    fire("time", mapOf("h" to c.get(Calendar.HOUR_OF_DAY).toString(), "m" to c.get(Calendar.MINUTE).toString(), "dow" to dow))
                    fire("every", mapOf("min" to (System.currentTimeMillis() / 60000L).toString()))
                }
                delay(15_000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        registerTelephony()
        refreshSensors()
        if (intent?.getBooleanExtra("boot", false) == true) fire("boot", force = true)
        return START_STICKY
    }

    private fun fire(event: String, payload: Map<String, String> = emptyMap(), force: Boolean = false) {
        // Початкові «повідомлення про поточний стан» від системних колбеків ігноруємо
        if (!force && event != "time" && event != "every" && System.currentTimeMillis() - startedAt < 4000) return
        MacroDispatcher.fire(this, event, payload)
    }

    @Suppress("DEPRECATION")
    private fun currentSsid(): String = try {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val raw = wm.connectionInfo?.ssid.orEmpty().trim('"')
        if (raw == "<unknown ssid>") "" else raw
    } catch (_: Throwable) { "" }

    private fun anyTrigger(prefix: String): Boolean = try {
        MacroStorage(this).get().any { m -> m.enabled && m.triggers.any { it.startsWith(prefix) } }
    } catch (_: Throwable) { false }

    /** SMS і стан дзвінків реєструються лише коли надано дозволи (повторний виклик безпечний). */
    private fun registerTelephony() {
        if (telRegistered) return
        val f = IntentFilter()
        var any = false
        if (checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            f.addAction(android.provider.Telephony.Sms.Intents.SMS_RECEIVED_ACTION); any = true
        }
        if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            f.addAction(android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED); any = true
        }
        if (any) {
            try { registerReceiver(rx, f, Context.RECEIVER_EXPORTED); telRegistered = true } catch (t: Throwable) { Log.e(TAG, "tel rx: ${t.message}") }
        }
    }

    /** Датчик струсу вмикається лише коли є макрос із тригером shake. */
    private fun refreshSensors() {
        val need = anyTrigger("shake")
        if (need && sensorListener == null) {
            try {
                val sm = getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
                val acc = sm.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER) ?: return
                var lastPeak = 0L; var peaks = 0
                val l = object : android.hardware.SensorEventListener {
                    override fun onSensorChanged(e: android.hardware.SensorEvent) {
                        val g = Math.sqrt((e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]).toDouble()) / 9.81
                        val now = System.currentTimeMillis()
                        if (g > 2.6) {
                            if (now - lastPeak > 900) peaks = 0
                            if (now - lastPeak > 150) peaks++
                            lastPeak = now
                            if (peaks >= 3) { peaks = 0; fire("shake") }
                        }
                    }
                    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
                }
                sm.registerListener(l, acc, android.hardware.SensorManager.SENSOR_DELAY_UI)
                sensorListener = l
            } catch (t: Throwable) { Log.e(TAG, "sensor: ${t.message}") }
        } else if (!need && sensorListener != null) {
            try { (getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager).unregisterListener(sensorListener) } catch (_: Throwable) {}
            sensorListener = null
        }
    }

    /** Відкриття/закриття додатків: опитування UsageStats раз на 2 с, лише коли є відповідні макроси і надано доступ. */
    private fun startAppWatcher() {
        scope.launch {
            var current = ""
            var lastTs = System.currentTimeMillis()
            var tick = 0
            var needed = false
            while (true) {
                if (tick++ % 5 == 0) needed = anyTrigger("app_opened") || anyTrigger("app_closed")
                if (needed && TriggerPermissions.hasUsageAccess(this@MacroTriggerService)) {
                    try {
                        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
                        val now = System.currentTimeMillis()
                        val ev = usm.queryEvents(lastTs - 500, now)
                        val e = android.app.usage.UsageEvents.Event()
                        while (ev.hasNextEvent()) {
                            ev.getNextEvent(e)
                            if (e.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND && e.packageName != current) {
                                val prev = current
                                current = e.packageName
                                if (prev.isNotEmpty()) fire("app_closed", mapOf("pkg" to prev, "app" to AppUtils.labelFor(this@MacroTriggerService, prev)))
                                fire("app_opened", mapOf("pkg" to current, "app" to AppUtils.labelFor(this@MacroTriggerService, current)))
                            }
                        }
                        lastTs = now
                    } catch (t: Throwable) { Log.e(TAG, "usage: ${t.message}") }
                    delay(2000)
                } else {
                    lastTs = System.currentTimeMillis()
                    delay(4000)
                }
            }
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(rx) } catch (_: Throwable) {}
        try { sensorListener?.let { (getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager).unregisterListener(it) } } catch (_: Throwable) {}
        try { netCb?.let { (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it) } } catch (_: Throwable) {}
        try { audioCb?.let { (getSystemService(Context.AUDIO_SERVICE) as AudioManager).unregisterAudioDeviceCallback(it) } } catch (_: Throwable) {}
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Автоматичні макроси", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("Макроси активні")
            .setContentText("Слідкую за подіями для автозапуску")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }
}
