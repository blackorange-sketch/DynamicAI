package com.dynamicai.launcher.llm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
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

    fun download(): Flow<Progress> = flow {
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(MODEL_URL).build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(Progress.Error("Сервер повернув ${response.code}"))
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
            emit(Progress.Error("Помилка завантаження: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
}
