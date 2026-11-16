package com.dynamicai.launcher.overlay

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import com.dynamicai.launcher.macro.AppUtils
import com.dynamicai.launcher.macro.MacroNotificationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Звичайне сповіщення для острівця. */
data class IslandNotif(
    val key: String,
    val pkg: String,
    val app: String,
    val title: String,
    val text: String,
    val time: Long,
    val icon: Bitmap?,
    val intent: PendingIntent?,
    val reply: Notification.Action?,
    val clearable: Boolean
)

/** «Жива активність» з постійного сповіщення: таймер, секундомір, дзвінок, навігація, завантаження. */
data class LiveItem(
    val key: String,
    val kind: Kind,
    val pkg: String,
    val app: String,
    val title: String,
    val text: String,
    /** Для таймера — момент закінчення, для секундоміра/дзвінка — момент старту (System.currentTimeMillis). */
    val chrono: Long,
    val countDown: Boolean,
    val progress: Float?,
    val icon: Bitmap?,
    val intent: PendingIntent?
) {
    enum class Kind { TIMER, STOPWATCH, CALL, NAV, PROGRESS }
}

/** Коротке «визирання» острівця (нове сповіщення, зарядка). */
data class IslandPeek(val id: Long, val icon: Bitmap?, val title: String, val text: String, val emoji: String? = null)

data class BatteryInfo(val level: Int, val charging: Boolean)

/**
 * Дані острівця: сповіщення, живі активності, зарядка. Наповнюється з MacroNotificationListener
 * (доступ до сповіщень) і з BroadcastReceiver батареї в FloatingOrbService.
 */
object IslandHub {
    private const val TAG = "IslandHub"
    private const val MAX_NOTIFS = 30

    private val _notifs = MutableStateFlow<List<IslandNotif>>(emptyList())
    val notifs: StateFlow<List<IslandNotif>> = _notifs.asStateFlow()

    private val _live = MutableStateFlow<List<LiveItem>>(emptyList())
    val live: StateFlow<List<LiveItem>> = _live.asStateFlow()

    private val _peek = MutableStateFlow<IslandPeek?>(null)
    val peek: StateFlow<IslandPeek?> = _peek.asStateFlow()

    private val _battery = MutableStateFlow<BatteryInfo?>(null)
    val battery: StateFlow<BatteryInfo?> = _battery.asStateFlow()

    private val iconCache = LruCache<String, Bitmap>(48)
    private var peekSeq = 0L
    private var lowWarned = false

    // ===== Сповіщення =====

    fun onPosted(ctx: Context, sbn: StatusBarNotification, peek: Boolean = true) {
        try {
            val n = sbn.notification ?: return
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
            val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0 || n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0
            if (ongoing) {
                classifyLive(ctx, sbn, n)?.let { item ->
                    _live.value = (_live.value.filterNot { it.key == item.key } + item)
                } ?: run { _live.value = _live.value.filterNot { it.key == sbn.key } }
                return
            }
            if (sbn.packageName == ctx.packageName) return
            if (isMedia(n)) return
            val ex = n.extras ?: Bundle.EMPTY
            val title = (ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                ?: ex.getCharSequence(Notification.EXTRA_TITLE))?.toString().orEmpty().trim()
            val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty().trim()
            if (title.isBlank() && text.isBlank()) return
            val app = AppUtils.labelFor(ctx, sbn.packageName)
            val existed = _notifs.value.any { it.key == sbn.key }
            val item = IslandNotif(
                key = sbn.key, pkg = sbn.packageName, app = app, title = title.ifBlank { app }, text = text,
                time = sbn.postTime, icon = iconFor(ctx, sbn, n), intent = n.contentIntent,
                reply = n.actions?.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true },
                clearable = sbn.isClearable
            )
            _notifs.value = (listOf(item) + _notifs.value.filterNot { it.key == sbn.key })
                .sortedByDescending { it.time }.take(MAX_NOTIFS)
            val onlyOnce = n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0
            if (peek && !(existed && onlyOnce)) {
                showPeek(item.icon, item.title, item.text.ifBlank { app })
            }
        } catch (t: Throwable) {
            Log.w(TAG, "onPosted: ${t.message}")
        }
    }

    fun onRemoved(sbn: StatusBarNotification) {
        _notifs.value = _notifs.value.filterNot { it.key == sbn.key }
        _live.value = _live.value.filterNot { it.key == sbn.key }
    }

    /** Повна синхронізація з активними сповіщеннями (при підключенні слухача/старті острівця). */
    fun syncAll(ctx: Context) {
        val l = MacroNotificationListener.instance ?: return
        val active = try { l.activeNotifications.toList() } catch (_: Throwable) { return }
        _notifs.value = emptyList()
        _live.value = emptyList()
        active.sortedBy { it.postTime }.forEach { onPosted(ctx, it, peek = false) }
    }

    private fun isMedia(n: Notification): Boolean =
        n.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true ||
            n.extras?.getString(Notification.EXTRA_TEMPLATE)?.contains("MediaStyle") == true

    private fun classifyLive(ctx: Context, sbn: StatusBarNotification, n: Notification): LiveItem? {
        if (isMedia(n)) return null
        val ex = n.extras ?: return null
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().trim()
        val chronometer = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
        val countDown = ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)
        val pMax = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val pCur = ex.getInt(Notification.EXTRA_PROGRESS, 0)
        val indeterminate = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
        val kind = when {
            n.category == Notification.CATEGORY_CALL -> LiveItem.Kind.CALL
            n.category == Notification.CATEGORY_NAVIGATION -> LiveItem.Kind.NAV
            chronometer && countDown -> LiveItem.Kind.TIMER
            chronometer -> LiveItem.Kind.STOPWATCH
            n.category == "stopwatch" -> LiveItem.Kind.STOPWATCH
            pMax > 0 && !indeterminate && sbn.packageName != ctx.packageName -> LiveItem.Kind.PROGRESS
            else -> return null
        }
        if (sbn.packageName == ctx.packageName && kind != LiveItem.Kind.TIMER && kind != LiveItem.Kind.STOPWATCH) return null
        return LiveItem(
            key = sbn.key, kind = kind, pkg = sbn.packageName, app = AppUtils.labelFor(ctx, sbn.packageName),
            title = title, text = text, chrono = n.`when`, countDown = countDown,
            progress = if (pMax > 0 && !indeterminate) (pCur.toFloat() / pMax).coerceIn(0f, 1f) else null,
            icon = iconFor(ctx, sbn, n), intent = n.contentIntent
        )
    }

    private fun iconFor(ctx: Context, sbn: StatusBarNotification, n: Notification): Bitmap? {
        // Аватар відправника (месенджери), інакше іконка програми
        try {
            n.getLargeIcon()?.loadDrawable(ctx)?.let { return it.toBitmap(96, 96) }
        } catch (_: Throwable) {}
        iconCache.get(sbn.packageName)?.let { return it }
        return try {
            ctx.packageManager.getApplicationIcon(sbn.packageName).toBitmap(96, 96).also { iconCache.put(sbn.packageName, it) }
        } catch (_: Throwable) { null }
    }

    // ===== Peek =====

    fun showPeek(icon: Bitmap?, title: String, text: String, emoji: String? = null) {
        _peek.value = IslandPeek(++peekSeq, icon, title, text, emoji)
    }

    fun clearPeek(id: Long) {
        if (_peek.value?.id == id) _peek.value = null
    }

    // ===== Батарея =====

    fun setBattery(level: Int, charging: Boolean) {
        val prev = _battery.value
        _battery.value = BatteryInfo(level, charging)
        if (prev != null && charging && !prev.charging) showPeek(null, "Заряджання", "$level%", "⚡")
        if (prev != null && !charging && prev.charging) showPeek(null, "Від мережі відключено", "$level%", "🔋")
        if (!charging && level <= 15 && !lowWarned) { lowWarned = true; showPeek(null, "Низький заряд", "$level%", "🪫") }
        if (charging || level > 20) lowWarned = false
    }

    // ===== Дії =====

    fun open(ctx: Context, intent: PendingIntent?, pkg: String) {
        try {
            if (intent != null) {
                if (Build.VERSION.SDK_INT >= 34) {
                    val opts = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    intent.send(ctx, 0, null, null, null, null, opts.toBundle())
                } else intent.send()
                return
            }
        } catch (t: Throwable) {
            Log.w(TAG, "contentIntent failed: ${t.message}")
        }
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { ctx.startActivity(it) } catch (_: Throwable) {}
        }
    }

    fun dismiss(key: String) {
        try { MacroNotificationListener.instance?.cancelNotification(key) } catch (_: Throwable) {}
        _notifs.value = _notifs.value.filterNot { it.key == key }
    }

    fun dismissAll() {
        val l = MacroNotificationListener.instance
        _notifs.value.filter { it.clearable }.forEach { try { l?.cancelNotification(it.key) } catch (_: Throwable) {} }
        _notifs.value = _notifs.value.filterNot { it.clearable }
    }

    /** Відповідь прямо з острівця (RemoteInput месенджера). */
    fun reply(ctx: Context, notif: IslandNotif, text: String): Boolean {
        val action = notif.reply ?: return false
        val inputs = action.remoteInputs ?: return false
        return try {
            val intent = Intent()
            val results = Bundle()
            inputs.forEach { results.putCharSequence(it.resultKey, text) }
            RemoteInput.addResultsToIntent(inputs, intent, results)
            action.actionIntent.send(ctx, 0, intent)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "reply failed: ${t.message}")
            false
        }
    }
}
