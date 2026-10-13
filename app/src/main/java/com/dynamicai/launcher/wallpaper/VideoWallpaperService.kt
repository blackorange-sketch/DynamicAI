package com.dynamicai.launcher.wallpaper

import android.content.Context
import android.content.SharedPreferences
import android.graphics.SurfaceTexture
import android.net.Uri
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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
        private var screenOn = true
        private var lowBattery = false
        private var paused = false
        private val mainHandler = Handler(Looper.getMainLooper())

        private val prefs: SharedPreferences by lazy {
            applicationContext.getSharedPreferences("dynamicai_wallpaper", Context.MODE_PRIVATE)
        }

        private val powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        screenOn = false
                        applyPowerState()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        screenOn = true
                        applyPowerState()
                    }
                    Intent.ACTION_BATTERY_LOW -> {
                        lowBattery = true
                        applyPowerState()
                    }
                    Intent.ACTION_BATTERY_OKAY -> {
                        lowBattery = false
                        applyPowerState()
                    }
                    Intent.ACTION_POWER_CONNECTED -> {
                        applyPowerState()
                    }
                }
            }
        }

        private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "videoUri", "muted" -> if (visible) restart()
                "wpPauseOnScreenOff", "wpPauseOnLowBattery" -> applyPowerState()
                "wpFps" -> {
                    val fps = prefs.getInt("wpFps", 30)
                    try { glThread?.setFps(fps) } catch (_: Exception) {}
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            prefs.registerOnSharedPreferenceChangeListener(prefsListener)
            // Слухаємо також головні налаштування
            applicationContext.getSharedPreferences("dynamicai_settings", Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(prefsListener)
            setTouchEventsEnabled(false)

            try {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_BATTERY_LOW)
                    addAction(Intent.ACTION_BATTERY_OKAY)
                    addAction(Intent.ACTION_POWER_CONNECTED)
                }
                applicationContext.registerReceiver(powerReceiver, filter)
            } catch (t: Throwable) {
                log("registerReceiver failed: ${t.message}")
            }
        }

        override fun onDestroy() {
            try {
                super.onDestroy()
                try { prefs.unregisterOnSharedPreferenceChangeListener(prefsListener) } catch (_: Exception) {}
                try { applicationContext.unregisterReceiver(powerReceiver) } catch (_: Exception) {}
                releaseAll()
            } catch (t: Throwable) {
                log("onDestroy error: ${t.message}")
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            try {
                log("visibility=$visible")
                this.visible = visible
                if (visible) {
                    if (player == null && prefs.getString("videoUri", null) != null) start()
                    applyPowerState()
                } else {
                    applyPowerState()
                }
            } catch (t: Throwable) {
                log("visibility error: ${t.message}")
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            try {
                super.onSurfaceCreated(holder)
                if (prefs.getString("videoUri", null) != null) start()
            } catch (t: Throwable) {
                log("surfaceCreated error: ${t.message}")
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            try {
                super.onSurfaceDestroyed(holder)
                releaseAll()
            } catch (t: Throwable) {
                log("surfaceDestroyed error: ${t.message}")
            }
        }

        private fun applyPowerState() {
            val pauseOnScreenOff = prefs.getBoolean("pauseOnScreenOff", true)
            val pauseOnLowBattery = prefs.getBoolean("pauseOnLowBattery", true)

            val shouldPause = !visible
                || (pauseOnScreenOff && !screenOn)
                || (pauseOnLowBattery && lowBattery)

            if (shouldPause && !paused) {
                paused = true
                mainHandler.post {
                    try { player?.pause() } catch (_: Exception) {}
                }
                try { glThread?.pause() } catch (_: Exception) {}
                log("paused (visible=$visible screenOn=$screenOn lowBattery=$lowBattery)")
            } else if (!shouldPause && paused) {
                paused = false
                mainHandler.post {
                    try { player?.play() } catch (_: Exception) {}
                }
                try { glThread?.resume() } catch (_: Exception) {}
                log("resumed")
            }
        }

        private fun restart() {
            releaseAll()
            start()
        }

        private fun start() {
            try {
                val uriStr = prefs.getString("videoUri", null)
                if (uriStr.isNullOrBlank()) {
                    log("no video set")
                    return
                }
                val muted = prefs.getBoolean("muted", true)

                val frame = surfaceHolder.surfaceFrame
                val w = frame.width()
                val h = frame.height()

                // Preview дає дуже маленький surface (<500 px) — пропускаємо
                if (w < 500 || h < 500) {
                    log("preview mode — skip GL (${w}x$h)")
                    return
                }

                val gl = GlRenderThread(surfaceHolder.surface, w, h)
                gl.onSurfaceTextureReady = { st ->
                    mainHandler.post {
                        try {
                            val surface = Surface(st)
                            val exo = ExoPlayer.Builder(applicationContext).build()

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
                }
                gl.setFps(prefs.getInt("wpFps", 30))
                gl.start()
                glThread = gl
            } catch (t: Throwable) {
                log("start failed: ${t.message}")
            }
        }

        private fun releaseAll() {
            mainHandler.post {
                try { player?.release() } catch (_: Exception) {}
                player = null
            }
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
