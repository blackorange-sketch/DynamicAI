package com.dynamicai.launcher.wallpaper

import android.content.Context
import android.content.SharedPreferences
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
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
                if (player == null) start() else try { player?.start() } catch (_: Exception) {}
            } else {
                try { player?.pause() } catch (_: Exception) {}
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
                Log.d("VideoWallpaper", "no video set")
                return
            }
            val muted = prefs.getBoolean("muted", true)

            try {
                val uri = Uri.parse(uriStr)
                player = MediaPlayer().apply {
                    setDataSource(applicationContext, uri)
                    setSurface(surfaceHolder.surface)
                    isLooping = true
                    setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f)

                    setOnPreparedListener { mp ->
                        try {
                            val videoW = mp.videoWidth
                            val videoH = mp.videoHeight
                            val surfaceFrame = surfaceHolder.surfaceFrame
                            val surfaceW = surfaceFrame.width()
                            val surfaceH = surfaceFrame.height()

                            Log.d("VideoWallpaper", "video=${videoW}x$videoH surface=${surfaceW}x$surfaceH")

                            if (videoW > 0 && videoH > 0 && surfaceW > 0 && surfaceH > 0) {
                                val videoAspect = videoW.toFloat() / videoH
                                val surfaceAspect = surfaceW.toFloat() / surfaceH

                                // FILL: масштабуємо так, щоб відео ЗАПОВНИЛО екран,
                                // зберігаючи пропорції. Зайве обрізається (краї не видно).
                                val newW: Int
                                val newH: Int
                                if (videoAspect > surfaceAspect) {
                                    // Відео ширше за екран — обмежуємо по ВИСОТІ
                                    // (обрізаємо по боках, видно центральну частину)
                                    newH = surfaceH
                                    newW = (surfaceH * videoAspect).toInt()
                                } else {
                                    // Відео вище за екран — обмежуємо по ШИРИНІ
                                    // (обрізаємо зверху/знизу, видно центральну частину)
                                    newW = surfaceW
                                    newH = (surfaceW / videoAspect).toInt()
                                }

                                surfaceHolder.setFixedSize(newW, newH)
                                Log.d("VideoWallpaper", "FILL ${newW}x$newH (video aspect=$videoAspect)")
                            }
                        } catch (t: Throwable) {
                            Log.e("VideoWallpaper", "resize failed: ${t.message}", t)
                        }
                        mp.start()
                    }

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

        private fun releasePlayer() {
            try { player?.stop() } catch (_: Exception) {}
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
