package com.dynamicai.launcher.wallpaper

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.net.Uri
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder

class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {

        private var player: MediaPlayer? = null
        private var glThread: GlRenderThread? = null
        private var visible = false

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
            this.visible = visible
            if (visible) {
                if (player == null) start() else try { player?.start() } catch (_: Exception) {}
                            } else {
                try { player?.pause() } catch (_: Exception) {}
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            if (prefs.getString("videoUri", null) != null) start()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            Log.d("VideoWallpaper", "surfaceChanged ${width}x$height")
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
                Log.d("VideoWallpaper", "no video set")
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
                        val mp = MediaPlayer().apply {
                            setDataSource(applicationContext, Uri.parse(uriStr))
                            setSurface(android.view.Surface(st))
                            isLooping = true
                            setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f)

                            setOnVideoSizeChangedListener { _, vw, vh ->
                                gl.updateVideoSize(vw, vh)
                                Log.d("VideoWallpaper", "videoSize=${vw}x$vh surface=${w}x$h")
                            }

                            setOnPreparedListener { it.start() }

                            setOnErrorListener { _, what, extra ->
                                Log.e("VideoWallpaper", "error what=$what extra=$extra")
                                true
                            }

                            prepareAsync()
                        }
                        player = mp
                    } catch (t: Throwable) {
                        Log.e("VideoWallpaper", "player init failed", t)
                    }
                }
                gl.start()
                glThread = gl
            } catch (t: Throwable) {
                Log.e("VideoWallpaper", "start failed", t)
            }
        }

        private fun releaseAll() {
            try { player?.stop() } catch (_: Exception) {}
            try { player?.release() } catch (_: Exception) {}
            player = null
            try { glThread?.stop() } catch (_: Exception) {}
            glThread = null
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
