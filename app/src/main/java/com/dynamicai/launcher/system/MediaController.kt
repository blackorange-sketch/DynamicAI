package com.dynamicai.launcher.system

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent

/**
 * Керування медіа через AudioManager + KeyEvent dispatch.
 */
class MediaController(private val context: Context) {

    companion object {
        private const val TAG = "MediaController"
    }

    fun play() = sendKey(KeyEvent.KEYCODE_MEDIA_PLAY)
    fun pause() = sendKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
    fun next() = sendKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    fun prev() = sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    fun stop() = sendKey(KeyEvent.KEYCODE_MEDIA_STOP)

    private fun sendKey(keyCode: Int): Boolean {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            Log.d(TAG, "Key sent: $keyCode")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "sendKey failed", t)
            false
        }
    }

    /**
     * Точна гучність (0-100%).
     */
    fun setVolumePercent(percent: Int): Boolean {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = ((percent.coerceIn(0, 100) / 100.0) * max).toInt()
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "setVolumePercent failed", t)
            false
        }
    }
}
