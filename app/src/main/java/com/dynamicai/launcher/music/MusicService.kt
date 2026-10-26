package com.dynamicai.launcher.music

import android.util.Log
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.*
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

class MusicService : MediaSessionService() {
    companion object {
        private const val TAG = "MusicService"
        @Volatile var currentTitle: String = ""
        @Volatile var currentArtist: String = ""
        @Volatile var isPlaying: Boolean = false
        @Volatile var instance: MusicService? = null
    }

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this
        try { NewPipe.init(DownloaderImpl()) } catch (_: Throwable) {}

        player = ExoPlayer.Builder(this).build().also { p ->
            mediaSession = MediaSession.Builder(this, p).build()
            p.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                    currentTitle = item?.mediaMetadata?.title?.toString() ?: ""
                    currentArtist = item?.mediaMetadata?.artist?.toString() ?: ""
                }
            })
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val query = intent?.getStringExtra("query")
        if (!query.isNullOrBlank()) {
            playByQuery(query)
        }
        return START_STICKY
    }

    fun playByQuery(query: String) {
        scope.launch {
            try {
                currentTitle = "Завантаження..."
                currentArtist = ""
                val extractor = ServiceList.YouTube.getSearchExtractor(query)
                extractor.fetchPage()
                val item = extractor.initialPage.items.firstOrNull() ?: run {
                    currentTitle = "Нічого не знайдено"; return@launch
                }
                val info = StreamInfo.getInfo(ServiceList.YouTube, item.url)
                val stream = info.audioStreams
                    .filter { it.isUrl && it.averageBitrate > 0 }
                    .maxByOrNull { it.averageBitrate } ?: run {
                    currentTitle = "Помилка потоку"; return@launch
                }
                val mediaItem = MediaItem.Builder()
                    .setUri(stream.url)
                    .setMediaMetadata(MediaMetadata.Builder()
                        .setTitle(info.name)
                        .setArtist(info.uploaderName ?: "")
                        .build())
                    .build()
                player?.setMediaItem(mediaItem)
                player?.prepare()
                player?.play()
            } catch (e: Exception) {
                Log.e(TAG, "playByQuery failed", e)
                currentTitle = "Помилка: ${e.message}"
            }
        }
    }

    fun togglePlayPause() { player?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { player?.seekToNextMediaItem() }
    fun previous() { player?.seekToPreviousMediaItem() }

    override fun onDestroy() {
        instance = null
        mediaSession?.release()
        player?.release()
        mediaSession = null
        player = null
        scope.cancel()
        super.onDestroy()
    }
}
