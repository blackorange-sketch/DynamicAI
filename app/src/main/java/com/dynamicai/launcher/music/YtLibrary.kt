package com.dynamicai.launcher.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.net.Uri
import android.service.media.MediaBrowserService
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.URL

data class LibItem(val mediaId: String, val title: String, val subtitle: String, val iconBitmap: Bitmap?, val iconUri: Uri?, val browsable: Boolean, val playable: Boolean)

sealed interface LibState {
    object Idle : LibState
    object Connecting : LibState
    data class Items(val title: String, val items: List<LibItem>, val depth: Int) : LibState
    data class Error(val msg: String) : LibState
}

object ArtLoader {
    private val cache = LruCache<String, Bitmap>(120)
    suspend fun load(ctx: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val k = uri.toString()
        cache.get(k)?.let { return@withContext it }
        try {
            val raw = when (uri.scheme) {
                "http", "https" -> URL(k).openStream().use { BitmapFactory.decodeStream(it) }
                else -> ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } ?: return@withContext null
            Bitmap.createScaledBitmap(raw, 96, 96, true).also { cache.put(k, it) }
        } catch (_: Throwable) { null }
    }
}

/**
 * Бібліотека плеєра через його MediaBrowserService (так Android Auto бачить плейлисти та «вподобане»).
 * Додаток сам вирішує, що віддати стороннім клієнтам: якщо відмовить — покажемо помилку.
 */
class YtLibrary(private val ctx: Context, private val pkg: String) {
    private val _state = MutableStateFlow<LibState>(LibState.Idle)
    val state: StateFlow<LibState> = _state.asStateFlow()

    private var browser: MediaBrowser? = null
    private var controller: MediaController? = null
    private val stack = ArrayDeque<Pair<String, String>>()   // (id, назва)

    fun connect() {
        if (browser != null) return
        _state.value = LibState.Connecting
        val svc = try {
            ctx.packageManager.queryIntentServices(Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage(pkg), 0).firstOrNull()?.serviceInfo
        } catch (_: Throwable) { null }
        if (svc == null) { _state.value = LibState.Error("У додатку немає медіабраузера"); return }
        try {
            val b = MediaBrowser(ctx, ComponentName(svc.packageName, svc.name), object : MediaBrowser.ConnectionCallback() {
                override fun onConnected() {
                    try { controller = MediaController(ctx, browser!!.sessionToken) } catch (_: Throwable) {}
                    stack.clear()
                    val root = browser?.root ?: return
                    stack.addLast(root to "Бібліотека")
                    load()
                }
                override fun onConnectionFailed() { _state.value = LibState.Error("Додаток не дозволив підключення") }
                override fun onConnectionSuspended() { _state.value = LibState.Error("Зʼєднання перервано") }
            }, null)
            browser = b
            b.connect()
        } catch (t: Throwable) { _state.value = LibState.Error(t.message ?: "Помилка підключення") }
    }

    private fun load() {
        val b = browser ?: return
        val (id, title) = stack.last()
        _state.value = LibState.Connecting
        try { b.unsubscribe(id) } catch (_: Throwable) {}
        b.subscribe(id, object : MediaBrowser.SubscriptionCallback() {
            override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowser.MediaItem>) {
                val items = children.take(80).map {
                    val d = it.description
                    LibItem(d.mediaId.orEmpty(), d.title?.toString().orEmpty(), d.subtitle?.toString().orEmpty(),
                        d.iconBitmap, d.iconUri, it.isBrowsable, it.isPlayable)
                }
                _state.value = if (items.isEmpty() && stack.size == 1)
                    LibState.Error("Додаток не віддає бібліотеку сторонньому лаунчеру")
                else LibState.Items(title, items, stack.size - 1)
            }
            override fun onError(parentId: String) { _state.value = LibState.Error("Не вдалося завантажити список") }
        })
    }

    fun open(item: LibItem) {
        if (item.browsable) { stack.addLast(item.mediaId to item.title); load() }
        else if (item.playable) controller?.transportControls?.playFromMediaId(item.mediaId, null)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeLast(); load(); return true
    }

    fun playItem(item: LibItem) { controller?.transportControls?.playFromMediaId(item.mediaId, null) }

    fun release() {
        try { browser?.disconnect() } catch (_: Throwable) {}
        browser = null; controller = null; stack.clear(); _state.value = LibState.Idle
    }
}
