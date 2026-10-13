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
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer

class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {

        private var player: ExoPlayer? = null
        private var glThread: GlRenderThread? = null
        private var visible = false
        private val mainHandler = Handler(Looper.getMainLooper())

        private val prefs: SharedPreferences by lazy {
            applicationContext.getSharedPreferences("dynamicai_wallpaper", Context.MODE_PRIVATE)
        }

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
            releaseAll()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            log("visibility=$visible")
            this.visible = visible
            if (visible) {
                if (player == null) start() else player?.play()
                            }
            // НЕ паузимо — хай відео грає
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            if (prefs.getString("videoUri", null) != null) start()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            releaseAll()
        }

        private fun restart() {
            releaseAll()
            start()
        }

        private fun start() {
            val uriStr = prefs.getString("videoUri", null)
            if (uriStr.isNullOrBlank()) {
                log("no video set")
                return
            }
            val muted = prefs.getBoolean("muted", true)

            try {
                val frame = surfaceHolder.surfaceFrame
                val w = frame.width().takeIf { it > 0 } ?: 1080
                val h = frame.height().takeIf { it > 0 } ?: 2400

                val gl = GlRenderThread(surfaceHolder.surface, w, h)
                gl.onSurfaceTextureReady = { st ->
                    try {
                        val surface = Surface(st)
                        val exo = ExoPlayer.Builder(applicationContext)
                            .setLooper(mainHandler.looper)
                            .build()

                        exo.setVideoSurface(surface)
                        exo.setMediaItem(MediaItem.fromUri(Uri.parse(uriStr)))
                        exo.repeatMode = Player.REPEAT_MODE_ALL
                        exo.volume = if (muted) 0f else 1f
                        exo.playWhenReady = true

                        exo.addListener(object : Player.Listener {
                            override fun onVideoSizeChanged(videoSize: VideoSize) {
                                val vw = videoSize.width
                                val vh = videoSize.height
                                gl.updateVideoSize(vw, vh)
                                log("videoSize=${vw}x$vh surface=${w}x$h")
                            }

                            override fun onPlaybackStateChanged(state: Int) {
                                log("state=$state")
                            }

                            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                log("error: ${error.message}")
                            }
                        })

                        exo.prepare()
                        player = exo
                    } catch (t: Throwable) {
                        log("exo init failed: ${t.message}")
                    }
                }
                gl.start()
                glThread = gl
            } catch (t: Throwable) {
                log("start failed: ${t.message}")
            }
        }

        private fun releaseAll() {
            try { player?.release() } catch (_: Exception) {}
            player = null
            try { glThread?.stop() } catch (_: Exception) {}
            glThread = null
        }

        private fun log(msg: String) {
            VideoWallpaperService.log(msg)
        }
    }

    companion object {
        @Volatile var lastLog: String = "—"
            private set

        internal fun log(msg: String) {
            lastLog = msg
            Log.d("VideoWallpaper", msg)
        }

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
