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
                    lastLevel = level
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
        }
        registerReceiver(rx, f, Context.RECEIVER_EXPORTED)

        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            netCb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { fire("wifi_connected") }
                override fun onLost(network: Network) { fire("wifi_disconnected") }
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
                }
                delay(15_000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("boot", false) == true) fire("boot", force = true)
        return START_STICKY
    }

    private fun fire(event: String, payload: Map<String, String> = emptyMap(), force: Boolean = false) {
        // Початкові «повідомлення про поточний стан» від системних колбеків ігноруємо
        if (!force && event != "time" && System.currentTimeMillis() - startedAt < 4000) return
        scope.launch {
            val storage = MacroStorage(this@MacroTriggerService)
            if (!storage.autoEnabled()) return@launch
            val runnerForChecks = MacroRunner(this@MacroTriggerService, { "" })
            for (m in storage.get()) {
                if (!m.enabled || m.triggers.none { matches(it, event, payload) }) continue
                if (m.constraints.isNotBlank() && !m.constraints.split(',').all { runnerForChecks.check(it) }) continue
                val now = System.currentTimeMillis()
                if (now - (lastRun[m.id] ?: 0L) < 3000) continue
                lastRun[m.id] = now
                launch {
                    Log.e(TAG, "▶ macro '${m.name}' on $event $payload")
                    try {
                        val agent = com.dynamicai.launcher.llm.AgentEngine(this@MacroTriggerService, null)
                        val tools = MacroTools(this@MacroTriggerService)
                        val vars = mutableMapOf("trigger" to event, "device" to (payload["device"] ?: ""), "level" to (payload["level"] ?: ""))
                        MacroRunner(this@MacroTriggerService, { agent.runTool(it) }, { tools.notify(m.name, it) }, vars).run(m)
                    } catch (t: Throwable) { Log.e(TAG, "macro failed", t) }
                }
            }
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(rx) } catch (_: Throwable) {}
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
