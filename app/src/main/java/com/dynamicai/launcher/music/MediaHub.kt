package com.dynamicai.launcher.music

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent
import com.dynamicai.launcher.macro.MacroNotificationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QueueEntry(val id: Long, val title: String, val subtitle: String, val icon: Bitmap?)

data class NowPlaying(
    val pkg: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
    val position: Long,      // мс на момент posAt
    val posAt: Long,         // elapsedRealtime, коли position актуальний
    val speed: Float,
    val duration: Long,
    val queue: List<QueueEntry>,
    val activeQueueId: Long
) {
    fun positionNow(now: Long): Long {
        val p = if (playing) position + ((now - posAt) * speed).toLong() else position
        return if (duration > 0) p.coerceIn(0, duration) else p.coerceAtLeast(0)
    }
}

/**
 * Єдине джерело «що зараз грає»: активні медіасесії системи (працює з YT Music, ReVanced, Spotify тощо).
 * Потрібен доступ до сповіщень — сервіс MacroNotificationListener вже є в застосунку.
 */
object MediaHub {
    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state.asStateFlow()

    private var appCtx: Context? = null
    private var mgr: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var started = false
    private var lastRecorded = ""
    private val main = Handler(Looper.getMainLooper())

    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onQueueChanged(queue: MutableList<android.media.session.MediaSession.QueueItem>?) = refresh()
        override fun onSessionDestroyed() { pick() }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }

    /** true — доступ до сповіщень є і підписка активна. Безпечно викликати повторно. */
    fun ensureStarted(ctx: Context): Boolean {
        val app = ctx.applicationContext
        appCtx = app
        if (started) return true
        return try {
            val m = app.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val cn = ComponentName(app, MacroNotificationListener::class.java)
            val sessions = m.getActiveSessions(cn)           // SecurityException без доступу до сповіщень
            mgr = m
            m.addOnActiveSessionsChangedListener(sessionsListener, cn, main)
            started = true
            main.post { pick(sessions) }
            true
        } catch (_: SecurityException) { false } catch (_: Throwable) { false }
    }

    private fun pick(list: List<MediaController>? = null) {
        val sessions = list ?: try {
            mgr?.getActiveSessions(ComponentName(appCtx!!, MacroNotificationListener::class.java))
        } catch (_: Throwable) { null } ?: emptyList()
        val best = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
            ?: sessions.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            try { controller?.unregisterCallback(cb) } catch (_: Throwable) {}
            controller = best
            try { best?.registerCallback(cb, main) } catch (_: Throwable) {}
        }
        refresh()
    }

    private fun refresh() {
        val c = controller
        if (c == null) { _state.value = null; return }
        val md = c.metadata
        val ps = c.playbackState
        val title = (md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_TITLE)).orEmpty()
        val artist = (md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)).orEmpty()
        val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val playing = ps?.state == PlaybackState.STATE_PLAYING || ps?.state == PlaybackState.STATE_BUFFERING
        val queue = try {
            c.queue?.take(60)?.map {
                QueueEntry(
                    it.queueId,
                    it.description.title?.toString().orEmpty(),
                    it.description.subtitle?.toString().orEmpty(),
                    it.description.iconBitmap
                )
            } ?: emptyList()
        } catch (_: Throwable) { emptyList() }
        _state.value = NowPlaying(
            pkg = c.packageName, title = title, artist = artist, art = art, playing = playing,
            position = ps?.position ?: 0L, posAt = ps?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            speed = ps?.playbackSpeed ?: 1f, duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            queue = queue, activeQueueId = ps?.activeQueueItemId ?: -1L
        )
        val key = "$title|$artist"
        if (playing && title.isNotBlank() && key != lastRecorded) {
            lastRecorded = key
            appCtx?.let { MusicHistory.add(it, c.packageName, title, artist, art) }
        }
    }

    // --- керування ---
    private fun key(keyCode: Int) {
        val am = appCtx?.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    fun togglePlay() {
        val c = controller
        if (c != null) { if (_state.value?.playing == true) c.transportControls.pause() else c.transportControls.play() }
        else key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }
    fun next() { controller?.transportControls?.skipToNext() ?: key(KeyEvent.KEYCODE_MEDIA_NEXT) }
    fun previous() { controller?.transportControls?.skipToPrevious() ?: key(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
    fun seekTo(ms: Long) { controller?.transportControls?.seekTo(ms) }
    fun skipToQueueItem(id: Long) { controller?.transportControls?.skipToQueueItem(id) }

    private val ytPackages = listOf(
        "app.revanced.android.apps.youtube.music", "anddea.youtube.music", "com.google.android.apps.youtube.music"
    )

    /** Пакет плеєра для бібліотеки та запуску: поточна сесія → інстальований YT Music / ReVanced. */
    fun musicPackage(ctx: Context): String? {
        _state.value?.pkg?.let { return it }
        return ytPackages.firstOrNull { p -> try { ctx.packageManager.getPackageInfo(p, 0); true } catch (_: Throwable) { false } }
    }

    fun openPlayer(ctx: Context) {
        val pkg = musicPackage(ctx) ?: return
        try {
            ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } catch (_: Throwable) {}
    }

    /** Запускає трек за назвою (як голосовий запит): працює і коли плеєр закритий. */
    fun playFromSearch(ctx: Context, title: String, artist: String, pkgHint: String?) {
        val q = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
        val c = controller
        if (c != null && (pkgHint == null || c.packageName == pkgHint)) {
            c.transportControls.playFromSearch(q, null); return
        }
        val pkg = pkgHint ?: musicPackage(ctx) ?: return
        try {
            ctx.startActivity(
                Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                    .setPackage(pkg)
                    .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                    .putExtra(SearchManager.QUERY, q)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {}
    }
}
