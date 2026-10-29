package com.dynamicai.launcher.voice

import android.content.Context
import android.util.Log
import com.dynamicai.launcher.llm.ModelDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.util.zip.ZipInputStream

/** Офлайн-розпізнавання української через Vosk. Модель завантажується з налаштувань. */
object VoskEngine {
    private const val TAG = "VoskEngine"
    const val MODEL_URL = "https://huggingface.co/Yehor/vosk-uk/resolve/main/uk_v3_dynamic_small.zip"
    private const val ZIP_NAME = "vosk-uk.zip"
    private const val DIR_NAME = "vosk-uk"

    @Volatile private var model: Model? = null

    private fun modelDir(ctx: Context) = File(ctx.filesDir, DIR_NAME)

    /** Каталог, у якому лежать am/ і conf/ (архів може мати вкладену папку). */
    private fun modelRoot(ctx: Context): File? =
        modelDir(ctx).takeIf { it.exists() }?.walkTopDown()?.maxDepth(3)
            ?.firstOrNull { it.isDirectory && File(it, "am").isDirectory && File(it, "conf").isDirectory }

    fun isInstalled(ctx: Context) = modelRoot(ctx) != null

    /** Чи треба зараз використати Vosk: "vosk" — завжди; "auto" — коли немає мережі; "google" — ніколи. */
    fun shouldUse(ctx: Context, mode: String): Boolean = isInstalled(ctx) && when (mode) {
        "vosk" -> true
        "auto" -> OfflineSpeech.preferOffline(ctx)
        else -> false
    }

    fun delete(ctx: Context) {
        release()
        modelDir(ctx).deleteRecursively()
    }

    fun release() {
        try { model?.close() } catch (_: Throwable) {}
        model = null
    }

    /** Завантажує й розпаковує модель. Повертає null при успіху або текст помилки. */
    suspend fun download(ctx: Context, onProgress: (String) -> Unit): String? = withContext(Dispatchers.IO) {
        var error: String? = null
        var zip: File? = null
        ModelDownloader(ctx).downloadUrl(MODEL_URL, ZIP_NAME).collect { p ->
            when (p) {
                is ModelDownloader.Progress.Downloading ->
                    onProgress(if (p.totalMb > 0) "Завантаження ${p.percent}% (${p.downloadedMb}/${p.totalMb} МБ)" else "Завантажено ${p.downloadedMb} МБ")
                is ModelDownloader.Progress.Complete -> zip = p.file
                is ModelDownloader.Progress.Error -> error = p.message
            }
        }
        if (error != null) return@withContext error
        val z = zip ?: return@withContext "Файл не отримано"
        onProgress("Розпаковування…")
        try {
            release()
            val dir = modelDir(ctx)
            dir.deleteRecursively()
            dir.mkdirs()
            val base = dir.canonicalPath + File.separator
            ZipInputStream(z.inputStream().buffered()).use { zin ->
                var e = zin.nextEntry
                while (e != null) {
                    val out = File(dir, e.name)
                    if (!out.canonicalPath.startsWith(base)) throw SecurityException("bad zip entry")
                    if (e.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zin.copyTo(it) }
                    }
                    e = zin.nextEntry
                }
            }
            z.delete()
            if (!isInstalled(ctx)) { dir.deleteRecursively(); return@withContext "Невірна структура архіву моделі" }
            null
        } catch (t: Throwable) {
            Log.e(TAG, "unzip failed", t)
            "Помилка розпаковування: ${t.message}"
        }
    }

    private fun loadModel(ctx: Context): Model? {
        model?.let { return it }
        val root = modelRoot(ctx) ?: return null
        return synchronized(this) {
            model ?: try { Model(root.absolutePath).also { model = it } } catch (t: Throwable) {
                Log.e(TAG, "Model load failed", t); null
            }
        }
    }

    private fun textOf(json: String?): String =
        try { JSONObject(json ?: "{}").optString("text", "").trim() } catch (_: Throwable) { "" }

    /** Одне розпізнавання: слухає до першої фрази й повертає її через onResult. Колбеки йдуть у головному потоці. */
    class Session(
        private val ctx: Context,
        private val onReady: () -> Unit,
        private val onPartial: (String) -> Unit,
        private val onResult: (String) -> Unit,
        private val onError: (String) -> Unit
    ) {
        @Volatile private var service: SpeechService? = null
        @Volatile private var cancelled = false
        private val main = android.os.Handler(android.os.Looper.getMainLooper())

        fun start() {
            Thread {
                val m = loadModel(ctx)
                main.post {
                    if (cancelled) return@post
                    if (m == null) { onError("Не вдалося завантажити модель Vosk"); return@post }
                    try {
                        val svc = SpeechService(Recognizer(m, 16000f), 16000f)
                        service = svc
                        svc.startListening(object : RecognitionListener {
                            override fun onPartialResult(hypothesis: String?) {
                                val t = try { JSONObject(hypothesis ?: "{}").optString("partial", "") } catch (_: Throwable) { "" }
                                if (t.isNotBlank()) onPartial(t)
                            }
                            override fun onResult(hypothesis: String?) { finish(textOf(hypothesis)) }
                            override fun onFinalResult(hypothesis: String?) { finish(textOf(hypothesis)) }
                            override fun onError(exception: Exception?) {
                                if (!cancelled) { stop(); onError(exception?.message ?: "Vosk error") }
                            }
                            override fun onTimeout() { if (!cancelled) { stop(); onError("timeout") } }
                        })
                        onReady()
                    } catch (t: Throwable) {
                        Log.e(TAG, "start failed", t)
                        onError(t.message ?: "Vosk start failed")
                    }
                }
            }.start()
        }

        private fun finish(text: String) {
            if (cancelled || text.isBlank()) return
            stop()
            onResult(text)
        }

        fun stop() {
            cancelled = true
            try { service?.stop(); service?.shutdown() } catch (_: Throwable) {}
            service = null
        }
    }
}
