package com.dynamicai.launcher.music

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

class ReVancedController(private val context: Context) {
    private val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private var mediaController: MediaController? = null

    companion object {
        @Volatile var currentTrackName: String = "Нічого не грає"
        @Volatile var isPlaying: Boolean = false
        @Volatile var instance: ReVancedController? = null
    }

    init {
        instance = this
        connectToReVanced()
    }

    private fun connectToReVanced() {
        try {
            // Отримуємо список активних медіа-сесій
            val sessions = mediaSessionManager.getActiveSessions(null)
            
            // Шукаємо сесію YouTube Music ReVanced (пакет може відрізнятися)
            // Non-Root версія: anddea.youtube.music
            // Root версія: com.google.android.apps.youtube.music
            val targetPackages = listOf(
                "anddea.youtube.music",
                "com.google.android.apps.youtube.music",
                "app.revanced.android.apps.youtube.music"
            )
            
            mediaController = sessions.find { session ->
                session.packageName in targetPackages
            }
            
            if (mediaController != null) {
                updateState()
                mediaController?.registerCallback(object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        isPlaying = state?.state == PlaybackState.STATE_PLAYING
                        MusicWidget.updateAll(context)
                    }
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        currentTrackName = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Невідомий трек"
                        MusicWidget.updateAll(context)
                    }
                })
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun updateState() {
        mediaController?.let { controller ->
            isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
            currentTrackName = controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Невідомий трек"
        }
    }

    fun togglePlayPause() {
        if (mediaController?.playbackState?.state == PlaybackState.STATE_PLAYING) {
            mediaController?.transportControls?.pause()
        } else {
            mediaController?.transportControls?.play()
        }
    }

    fun playNext() {
        mediaController?.transportControls?.skipToNext()
    }

    fun playPrevious() {
        mediaController?.transportControls?.skipToPrevious()
    }

    // Для запуску конкретного треку в ReVanced через пошук
    fun playByQuery(query: String) {
        // Відкриваємо ReVanced з пошуковим запитом
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse("https://music.youtube.com/search?q=$query")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        
        // Після відкриття натискаємо Play через AccessibilityService (якщо потрібно)
        // Це можна залишити для автоматизації
    }
}
