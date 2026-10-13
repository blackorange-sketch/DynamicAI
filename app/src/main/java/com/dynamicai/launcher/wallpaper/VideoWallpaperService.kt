package com.dynamicai.launcher.wallpaper

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder

class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {

        private var player: MediaPlayer? = null
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
            releasePlayer()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                if (player == null) start()
                else try { player?.start() } catch (_: Exception) {}
            } else {
                pause()
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

        private fun restart() {
            releasePlayer()
            start()
        }

        private fun start() {
            val uriStr = prefs.getString("videoUri", null)
            if (uriStr.isNullOrBlank()) {
                Log.d("VideoWallpaper", "no video set — waiting")
                return
            }
            val muted = prefs.getBoolean("muted", true)
            try {
                val uri = Uri.parse(uriStr)
                player = MediaPlayer().apply {
                    setDataSource(applicationContext, uri)
                    setSurface(surfaceHolder.surface)
                    isLooping = true
                    setVolume(
                        if (muted) 0f else 1f,
                        if (muted) 0f else 1f
                    )
                    setOnPreparedListener { it.start() }
                    setOnErrorListener { _, what, extra ->
                        Log.e("VideoWallpaper", "error what=$what extra=$extra")
                        true
                    }
                    prepareAsync()
                }
            } catch (t: Throwable) {
                Log.e("VideoWallpaper", "start failed: ${t.message}", t)
            }
        }

        private fun pause() {
            try { player?.pause() } catch (_: Exception) {}
        }

        private fun releasePlayer() {
            try {
                player?.stop()
            } catch (_: Exception) {}
            try {
                player?.release()
            } catch (_: Exception) {}
            player = null
        }
    }

    companion object {
        fun setVideo(ctx: Context, uri: Uri) {
            ctx.getSharedPreferences("dynamicai_wallpaper", Context.MODE_PRIVATE)
                .edit()
                .putString("videoUri", uri.toString())
                .commit()
        }

        fun setMuted(ctx: Context, muted: Boolean) {
            ctx.getSharedPreferences("dynamicai_wallpaper", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("muted", muted)
                .commit()
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
