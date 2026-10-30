package com.dynamicai.launcher.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class HistoryTrack(val title: String, val artist: String, val pkg: String, val key: String)

/** Історія відтворених треків (з обкладинками) — локальна «бібліотека» для швидкого повтору. */
object MusicHistory {
    private const val PREFS = "music_history"
    private const val MAX = 40

    private fun key(t: String, a: String): String =
        MessageDigest.getInstance("MD5").digest("$t|$a".toByteArray()).joinToString("") { "%02x".format(it) }

    private fun dir(ctx: Context) = File(ctx.filesDir, "music_art").apply { mkdirs() }

    fun get(ctx: Context): List<HistoryTrack> = try {
        val arr = JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("items", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            HistoryTrack(o.getString("t"), o.optString("a"), o.optString("p"), o.getString("k"))
        }
    } catch (_: Throwable) { emptyList() }

    fun add(ctx: Context, pkg: String, title: String, artist: String, art: Bitmap?) {
        try {
            val k = key(title, artist)
            val list = (listOf(HistoryTrack(title, artist, pkg, k)) + get(ctx).filter { it.key != k }).take(MAX)
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().put("t", it.title).put("a", it.artist).put("p", it.pkg).put("k", it.key)) }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("items", arr.toString()).apply()
            if (art != null) {
                val f = File(dir(ctx), "$k.jpg")
                if (!f.exists()) Thread {
                    try {
                        val s = Bitmap.createScaledBitmap(art, 96, 96, true)
                        f.outputStream().use { s.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                    } catch (_: Throwable) {}
                }.start()
            }
            // чистимо обкладинки, яких більше немає в історії
            val keep = list.map { "${it.key}.jpg" }.toSet()
            dir(ctx).listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
        } catch (_: Throwable) {}
    }

    fun art(ctx: Context, key: String): Bitmap? = try {
        File(dir(ctx), "$key.jpg").takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    } catch (_: Throwable) { null }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        dir(ctx).listFiles()?.forEach { it.delete() }
    }
}
