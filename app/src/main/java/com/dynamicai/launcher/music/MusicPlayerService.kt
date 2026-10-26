package com.dynamicai.launcher.music

import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import kotlinx.coroutines.*

class MusicPlayerService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        // Ініціалізація NewPipe (потрібен Downloader)
        NewPipe.init(DownloaderImpl())
        
        val player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    // Функція для запуску треку за назвою
    fun playByQuery(query: String) {
        scope.launch {
            try {
                val extractor = ServiceList.YouTube.getSearchExtractor(query)
                extractor.fetchPage()
                val firstItem = extractor.initialPage.items.firstOrNull() ?: return@launch
                val url = firstItem.url
                
                val info = StreamInfo.getInfo(ServiceList.YouTube, url)
                val audioStream = info.audioStreams
                    .filter { it.isUrl && it.averageBitrate > 0 }
                    .maxByOrNull { it.averageBitrate }
                    ?: return@launch
                
                val mediaItem = MediaItem.fromUri(audioStream.url)
                mediaSession?.player?.setMediaItem(mediaItem)
                mediaSession?.player?.prepare()
                mediaSession?.player?.play()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        scope.cancel()
        super.onDestroy()
    }
}
