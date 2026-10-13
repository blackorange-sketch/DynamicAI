package com.dynamicai.launcher.wallpaper

import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.net.Uri
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout

class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {

        private var player: ExoPlayer? = null
        private var visible = false
        private var container: FrameLayout? = null
        private var surfaceView: SurfaceView? = null
        private var aspectFrame: AspectRatioFrameLayout? = null

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

            // Створюємо ієрархію View для правильних пропорцій
            val containerLayout = FrameLayout(this@VideoWallpaperService).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.BLACK)
            }

            val aspect = AspectRatioFrameLayout(this@VideoWallpaperService).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    gravity = Gravity.CENTER
                }
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }

            val surface = SurfaceView(this@VideoWallpaperService).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                holder.setFormat(PixelFormat.TRANSLUCENT)
            }

            aspect.addView(surface)
            containerLayout.addView(aspect)

            container = containerLayout
            surfaceView = surface
            aspectFrame = aspect
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
            val surface = surfaceView?.holder?.surface ?: return

            try {
                val p = ExoPlayer.Builder(applicationContext).build().apply {
                    setMediaItem(MediaItem.fromUri(Uri.parse(uriStr)))
                    repeatMode = Player.REPEAT_MODE_ALL
                    volume = if (muted) 0f else 1f
                    playWhenReady = true
                    setVideoSurface(surface)

                    addListener(object : Player.Listener {
                        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                            val aspect = if (videoSize.height > 0)
                                videoSize.width.toFloat() / videoSize.height.toFloat()
                            else 0f
                            aspectFrame?.setAspectRatio(aspect)
                            Log.d("VideoWallpaper", "videoSize=${videoSize.width}x${videoSize.height} aspect=$aspect")
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
