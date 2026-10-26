package com.dynamicai.launcher.music

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

class DownloaderImpl : Downloader() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val data = request.dataToSend()

        val body = if (data != null) RequestBody.create(null, data) else null
        val httpRequest = Request.Builder()
            .method(httpMethod, body)
            .url(url)
            .build()

        val response = client.newCall(httpRequest).execute()
        val responseBody = response.body?.string()
        val responseHeaders = response.headers.toMultimap()

        return Response(
            response.code,
            response.message,
            responseHeaders,
            responseBody,
            url
        )
    }
}
