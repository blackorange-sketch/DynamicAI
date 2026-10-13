package com.dynamicai.launcher.wallpaper

import android.content.Context
import android.content.SharedPreferences
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine(), SurfaceTexture.OnFrameAvailableListener {

        private var player: ExoPlayer? = null
        private var visible = false
        private val prefs: SharedPreferences by lazy {
            applicationContext.getSharedPreferences("dynamicai_wallpaper", Context.MODE_PRIVATE)
        }
        private val handler = Handler(Looper.getMainLooper())

        private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "videoUri" || key == "muted") {
                if (visible) restart()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            prefs.registerOnSharedPreferenceChangeListener(prefsListener)
            setTouchEventsEnabled(false)
        }

        override fun onDestroy() {
            super.onDestroy()
            try { prefs.unregisterOnSharedPreferenceChangeListener(prefsListener) } catch (_: Exception) {}
            releasePlayer()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                if (player == null) start() else player?.play()
            } else {
                player?.pause()
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            if (prefs.getString("videoUri", null) != null) start()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            releasePlayer()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            Log.d("VideoWallpaper", "surfaceChanged ${width}x$height")
        }

        override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {}

        private fun restart() {
            releasePlayer()
            start()
        }

        private fun start() {
            val uriStr = prefs.getString("videoUri", null)
            if (uriStr.isNullOrBlank()) {
                Log.d("VideoWallpaper", "no video set")
                return
            }
            val muted = prefs.getBoolean("muted", true)

            try {
                val p = ExoPlayer.Builder(applicationContext).build().apply {
                    val item = MediaItem.fromUri(Uri.parse(uriStr))
                    setMediaItem(item)
                    repeatMode = Player.REPEAT_MODE_ALL
                    volume = if (muted) 0f else 1f
                    playWhenReady = true

                    // Змінюємо розмір surface, щоб відео не розтягувалось
                    try {
                        val frame = surfaceHolder.surfaceFrame
                        val surfaceW = frame.width()
                        val surfaceH = frame.height()
                        val videoW = 1080  // буде перевизначено нижче
                        val videoH = 1920
                        Log.d("VideoWallpaper", "surface=${surfaceW}x$surfaceH")
                    } catch (_: Exception) {}

                    // Прив'язка до surface WallpaperService
                    setVideoSurface(surfaceHolder.surface)

                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            Log.d("VideoWallpaper", "state=$state")
                        }
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            Log.e("VideoWallpaper", "error: ${error.message}")
                        }
                    })

                    prepare()
                }
                player = p
            } catch (t: Throwable) {
                Log.e("VideoWallpaper", "start failed: ${t.message}", t)
            }
        }

        private fun releasePlayer() {
            try { player?.release() } catch (_: Exception) {}
            player = null
        }
    }

    companion object {
        fun setVideo(ctx: Context, uri: Uri) {
            ctx.getSharedPreferences("dynamicai_wallpaper", Context.MODE_PRIVATE)
                .edit().putString("videoUri", uri.toString()).commit()
        }

        fun setMuted(ctx: Context, muted: Boolean) {
            ctx.getSharedPreferences("dynamicai_wallpaper", Context.MODE_PRIVATE)
                .edit().putBoolean("muted", muted).commit()
        }

        fun getMuted(ctx: Context): Boolean =
            ctx.getSharedPreferences("dynamicai_wallpaper", Context.MODE_PRIVATE)
                .getBoolean("muted", true)

        fun hasVideo(ctx: Context): Boolean =
            ctx.getSharedPreferences("dynamicai_wallpaper", Context.MODE_PRIVATE)
                .getString("videoUri", null) != null

        fun clear(ctx: Context) {
            ctx.getSharedPreferences("dynamicai_wallpaper", Context.MODE_PRIVATE)
                .edit().remove("videoUri").commit()
        }
    }
}
