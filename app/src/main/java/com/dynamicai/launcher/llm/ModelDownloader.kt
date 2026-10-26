package com.dynamicai.launcher.llm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import android.util.Log
import java.io.File
import android.util.Log
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class ModelDownloader(private val context: Context) {

    companion object {
        const val MODEL_FILE = "gemma-4-E2B-it.litertlm"
        const val MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
    }

    val modelFile: File get() = File(context.filesDir, MODEL_FILE)
    val isDownloaded: Boolean get() = modelFile.exists() && modelFile.length() > 100_000_000L

    sealed class Progress {
        data class Downloading(val percent: Int, val downloadedMb: Long, val totalMb: Long) : Progress()
        data class Complete(val file: File) : Progress()
        data class Error(val message: String) : Progress()
    }


    fun downloadUrl(url: String, fileName: String): Flow<Progress> = flow {
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .header("Accept-Encoding", "identity")
            .build()
        val file = File(context.filesDir, fileName)
        val tempFile = File(context.filesDir, "$fileName.tmp")

        try {
            val response = client.newCall(request).execute()
            Log.d("ModelDownloader", "Response code: ${response.code}, URL: $url")
            if (!response.isSuccessful) {
                emit(Progress.Error("Сервер ${response.code}"))
                return@flow
            }
            val body = response.body ?: run {
                emit(Progress.Error("Порожня відповідь"))
                return@flow
            }
            val total = body.contentLength()
            var downloaded: Long = 0

            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        if (total > 0) {
                            val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                            emit(Progress.Downloading(percent, downloaded / 1_000_000, total / 1_000_000))
                        } else {
                            // Якщо сервер не вказав розмір (наприклад, AWS), показуємо просто завантажені МБ
                            emit(Progress.Downloading(0, downloaded / 1_000_000, 0))
                        }
                    }
                }
            }
            if (file.exists()) file.delete()
            tempFile.renameTo(file)
            emit(Progress.Complete(file))
        } catch (t: Throwable) {
            emit(Progress.Error(t.message ?: "Помилка"))
        }
    }

    fun download(): Flow<Progress> = flow {
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(MODEL_URL).build()

        try {
            val response = client.newCall(request).execute()
            Log.d("ModelDownloader", "Response code: ${response.code}, URL: $url")
            if (!response.isSuccessful) {
                emit(Progress.Error("Сервер ${response.code}"))
                return@flow
            }
            val body = response.body ?: run {
                emit(Progress.Error("Порожня відповідь"))
                return@flow
            }
            val total = body.contentLength()
            val tempFile = File(context.cacheDir, "$MODEL_FILE.part")

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastEmit = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 250) {
                            lastEmit = now
                            val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                            emit(
                                Progress.Downloading(
                                    percent,
                                    downloaded / 1_048_576L,
                                    total / 1_048_576L
                                )
                            )
                        }
                    }
                }
            }

            if (modelFile.exists()) modelFile.delete()
            tempFile.renameTo(modelFile)
            emit(Progress.Complete(modelFile))
        } catch (e: IOException) {
            emit(Progress.Error("Помилка: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
}
