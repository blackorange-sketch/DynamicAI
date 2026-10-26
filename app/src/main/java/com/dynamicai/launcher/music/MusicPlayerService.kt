package com.dynamicai.launcher.music

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.*
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

class MusicPlayerService : MediaSessionService() {
    companion object {
        @Volatile var isPlaying: Boolean = false
        @Volatile var currentTrackName: String = "Нічого не грає"
        @Volatile var instance: MusicPlayerService? = null
    }

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this
        try { NewPipe.init(DownloaderImpl()) } catch (_: Throwable) {}

        val player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                MusicWidget.updateAll(this@MusicPlayerService)
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentTrackName = mediaItem?.mediaMetadata?.title?.toString() ?: "Невідомий трек"
                MusicWidget.updateAll(this@MusicPlayerService)
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    fun playByQuery(query: String) {
        scope.launch {
            try {
                currentTrackName = "Завантаження..."
                MusicWidget.updateAll(this@MusicPlayerService)

                val extractor = ServiceList.YouTube.getSearchExtractor(query)
                extractor.fetchPage()
                val firstItem = extractor.initialPage.items.firstOrNull() ?: run {
                    currentTrackName = "Нічого не знайдено"
                    MusicWidget.updateAll(this@MusicPlayerService)
                    return@launch
                }

                val info = StreamInfo.getInfo(ServiceList.YouTube, firstItem.url)
                val audioStream = info.audioStreams
                    .filter { it.isUrl && it.averageBitrate > 0 }
                    .maxByOrNull { it.averageBitrate } ?: run {
                        currentTrackName = "Помилка потоку"
                        MusicWidget.updateAll(this@MusicPlayerService)
                        return@launch
                    }

                val mediaItem = MediaItem.Builder()
                    .setUri(audioStream.url)
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(info.name)
                            .setArtist(info.uploaderName ?: "")
                            .build()
                    ).build()

                mediaSession?.player?.setMediaItem(mediaItem)
                mediaSession?.player?.prepare()
                mediaSession?.player?.play()
            } catch (e: Exception) {
                currentTrackName = "Помилка: ${e.message}"
                MusicWidget.updateAll(this@MusicPlayerService)
            }
        }
    }

    fun togglePlayPause() {
        mediaSession?.player?.let { player ->
            if (player.isPlaying) player.pause() else player.play()
        }
    }

    override fun onDestroy() {
        instance = null
        mediaSession?.run { player.release(); release() }
        mediaSession = null
        scope.cancel()
        super.onDestroy()
    }
}
