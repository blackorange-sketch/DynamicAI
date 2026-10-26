package com.dynamicai.launcher.music

import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
// import com.tamed.music.innertube.YouTube
// import com.tamed.music.innertube.models.SongItem
import kotlinx.coroutines.*

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
        AuthManager.loadCookie(this) // Завантажуємо cookie при старті

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

                // Пошук через Inner
                val searchResult: Any? = null
                val firstSong = searchResult?.items?.firstOrNull { it is SongItem } as? SongItem
                if (firstSong == null) {
                    currentTrackName = "Нічого не знайдено"
                    MusicWidget.updateAll(this@MusicPlayerService)
                    return@launch
                }

                // Отримуємо аудіопотік
                val playerResponse: Any? = null
                val audioUrl = playerResponse?.streamingData?.adaptiveFormats
                    ?.filter { it.mimeType.contains("audio") }
                    ?.maxByOrNull { it.bitrate }
                    ?.url
                if (audioUrl == null) {
                    currentTrackName = "Помилка отримання потоку"
                    MusicWidget.updateAll(this@MusicPlayerService)
                    return@launch
                }

                val mediaItem = MediaItem.Builder()
                    .setUri(audioUrl)
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(firstSong.title)
                            .setArtist(firstSong.artists?.joinToString { it.name } ?: "")
                            .build()
                    ).build()

                mediaSession?.player?.setMediaItem(mediaItem)
                mediaSession?.player?.prepare()
                mediaSession?.player?.play()
            } catch (e: Exception) {
                currentTrackName = "Помилка"
                MusicWidget.updateAll(this@MusicPlayerService)
                e.printStackTrace()
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
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        scope.cancel()
        super.onDestroy()
    }
}
