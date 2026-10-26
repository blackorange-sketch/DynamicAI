/*
 * Tamed Project (2026)
 * Original project contributors
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.tamed.music.innertube

import com.tamed.music.innertube.models.Context
import com.tamed.music.innertube.models.MediaInfo
import com.tamed.music.innertube.models.ReturnYouTubeDislikeResponse
import com.tamed.music.innertube.models.YouTubeClient
import com.tamed.music.innertube.models.YouTubeLocale
import com.tamed.music.innertube.models.response.NextResponse
import com.tamed.music.innertube.utils.InnerTubeRequestMethod
import com.tamed.music.innertube.utils.InnerTubeRequestSpec
import com.tamed.music.innertube.utils.buildAccountMenuRequest
import com.tamed.music.innertube.utils.buildAddPlaylistToPlaylistRequest
import com.tamed.music.innertube.utils.buildAddToPlaylistRequest
import com.tamed.music.innertube.utils.buildBrowseRequest
import com.tamed.music.innertube.utils.buildCreatePlaylistRequest
import com.tamed.music.innertube.utils.buildDeletePlaylistRequest
import com.tamed.music.innertube.utils.buildGetQueueRequest
import com.tamed.music.innertube.utils.buildGetTranscriptRequest
import com.tamed.music.innertube.utils.buildInnerTubeRequestMetadata
import com.tamed.music.innertube.utils.buildLikePlaylistRequest
import com.tamed.music.innertube.utils.buildLikeVideoRequest
import com.tamed.music.innertube.utils.buildMoveSongPlaylistRequest
import com.tamed.music.innertube.utils.buildNextRequest
import com.tamed.music.innertube.utils.buildPlayerRequest
import com.tamed.music.innertube.utils.buildRegisterPlaybackRequest
import com.tamed.music.innertube.utils.buildRemoveFromPlaylistRequest
import com.tamed.music.innertube.utils.buildRenamePlaylistRequest
import com.tamed.music.innertube.utils.buildSearchRequest
import com.tamed.music.innertube.utils.buildSearchSuggestionsRequest
import com.tamed.music.innertube.utils.buildSubscribeRequest
import com.tamed.music.innertube.utils.buildSwJsDataRequest
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlinx.coroutines.delay
import java.util.*
import kotlin.io.encoding.ExperimentalEncodingApi
import java.net.Proxy

/**
 * Provide access to InnerTube endpoints.
 * For making HTTP requests, not parsing response.
 */
@OptIn(ExperimentalEncodingApi::class)
class InnerTube {
    private var httpClient = createClient()

    var locale = YouTubeLocale(
        gl = Locale.getDefault().country,
        hl = Locale.getDefault().toLanguageTag()
    )
    @Volatile
    private var authState: PlaybackAuthState = PlaybackAuthState.EMPTY

    var visitorData: String?
        get() = authState.visitorData
        set(value) {
            authState = authState.copy(visitorData = value).normalized()
        }
    var dataSyncId: String?
        get() = authState.dataSyncId
        set(value) {
            authState = authState.copy(dataSyncId = value).normalized()
        }
    var poToken: String?
        get() = authState.poToken
        set(value) {
            authState = authState.copy(poToken = value).normalized()
        }
    var cookie: String?
        get() = authState.cookie
        set(value) {
            authState = authState.copy(cookie = value).normalized()
        }

    var proxy: Proxy? = null
        set(value) {
            field = value
            httpClient.close()
            httpClient = createClient()
        }

    var useLoginForBrowse: Boolean = false

    fun currentAuthState(): PlaybackAuthState = authState

    fun applyAuthState(value: PlaybackAuthState) {
        authState = value.normalized()
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun createClient() = HttpClient(OkHttp) {
        expectSuccess = true

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            })
        }

        install(ContentEncoding) {
            gzip(0.9F)
            deflate(0.8F)
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 15000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 15000
        }

        if (proxy != null) {
            engine {
                proxy = this@InnerTube.proxy
            }
        }

        defaultRequest {
            url(YouTubeClient.API_URL_YOUTUBE_MUSIC)
        }
    }

    private fun HttpRequestBuilder.ytClient(
        client: YouTubeClient,
        setLogin: Boolean = false,
        authState: PlaybackAuthState = currentAuthState(),
    ) {
        val requestMetadata = buildInnerTubeRequestMetadata(
            client = client,
            authState = authState,
            setLogin = setLogin,
        )
        contentType(ContentType.Application.Json)
        headers {
            for ((name, value) in requestMetadata.headers) {
                append(name, value)
            }
        }
        userAgent(requestMetadata.userAgent)
        parameter("prettyPrint", false)
    }

    private suspend fun <T> executeRequest(spec: InnerTubeRequestSpec<T>) = withRetry {
        when (spec.method) {
            InnerTubeRequestMethod.GET -> httpClient.get(spec.url) {
                applyRequestSpec(spec)
            }
            InnerTubeRequestMethod.POST -> httpClient.post(spec.url) {
                applyRequestSpec(spec)
            }
        }
    }

    private fun <T> HttpRequestBuilder.applyRequestSpec(spec: InnerTubeRequestSpec<T>) {
        if (spec.useJsonContentType) {
            ytClient(spec.client, setLogin = spec.setLogin, authState = spec.authState)
        }
        if (spec.useJsonContentType) {
            contentType(ContentType.Application.Json)
        }
        for ((name, value) in spec.queryParameters) {
            parameter(name, value)
        }
        spec.body?.let { setBody(it) }
    }

    /**
     * Simple retry wrapper for transient IO errors (socket aborts, timeouts).
     * Retries the given block up to [maxAttempts] times with exponential backoff.
     * Cancellation is respected since [delay] will throw if the coroutine is cancelled.
     */
    private suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelay: Long = 500L,
        factor: Double = 2.0,
        block: suspend () -> T,
    ): T {
        var currentDelay = initialDelay
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: IOException) {
                attempt++
                if (attempt >= maxAttempts) throw e
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
    }

    suspend fun search(
        client: YouTubeClient,
        query: String? = null,
        params: String? = null,
        continuation: String? = null,
    ) = executeRequest(buildSearchRequest(client, locale, currentAuthState(), useLoginForBrowse, query, params, continuation))

    suspend fun player(
        client: YouTubeClient,
        videoId: String,
        playlistId: String?,
        signatureTimestamp: Int?,
        poToken: String? = null,
        setLogin: Boolean = true,
        authState: PlaybackAuthState = currentAuthState(),
    ) = executeRequest(buildPlayerRequest(client, locale, authState, videoId, playlistId, signatureTimestamp, poToken, setLogin))

    suspend fun registerPlayback(
        url: String,
        cpn: String,
        playlistId: String?,
        poToken: String? = null,
        client: YouTubeClient = YouTubeClient.WEB_REMIX,
        authState: PlaybackAuthState = currentAuthState(),
    ) = executeRequest(buildRegisterPlaybackRequest(url, client, authState, cpn, playlistId, poToken))

    suspend fun browse(
        client: YouTubeClient,
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null,
        setLogin: Boolean = false,
    ) = executeRequest(buildBrowseRequest(client, locale, currentAuthState(), setLogin || useLoginForBrowse, browseId, params, continuation))

    suspend fun next(
        client: YouTubeClient,
        videoId: String?,
        playlistId: String?,
        playlistSetVideoId: String?,
        index: Int?,
        params: String?,
        continuation: String? = null,
    ) = executeRequest(buildNextRequest(client, locale, currentAuthState(), videoId, playlistId, playlistSetVideoId, index, params, continuation))

    suspend fun getSearchSuggestions(
        client: YouTubeClient,
        input: String,
    ) = executeRequest(buildSearchSuggestionsRequest(client, locale, currentAuthState(), input))

    suspend fun getQueue(
        client: YouTubeClient,
        videoIds: List<String>?,
        playlistId: String?,
    ) = executeRequest(buildGetQueueRequest(client, locale, currentAuthState(), videoIds, playlistId))

    suspend fun getTranscript(
        client: YouTubeClient,
        videoId: String,
    ) = executeRequest(buildGetTranscriptRequest(client, locale, videoId))

    suspend fun getSwJsData() = executeRequest(buildSwJsDataRequest())


    suspend fun accountMenu(client: YouTubeClient) = executeRequest(buildAccountMenuRequest(client, locale, currentAuthState()))

    suspend fun likeVideo(
        client: YouTubeClient,
        videoId: String,
    ) = executeRequest(buildLikeVideoRequest(client, locale, currentAuthState(), videoId))

    suspend fun unlikeVideo(
        client: YouTubeClient,
        videoId: String,
    ) = executeRequest(buildLikeVideoRequest(client, locale, currentAuthState(), videoId, unlike = true))

    suspend fun subscribeChannel(
        client: YouTubeClient,
        channelId: String,
    ) = executeRequest(buildSubscribeRequest(client, locale, currentAuthState(), channelId))

    suspend fun unsubscribeChannel(
        client: YouTubeClient,
        channelId: String,
    ) = executeRequest(buildSubscribeRequest(client, locale, currentAuthState(), channelId, unsubscribe = true))

    suspend fun likePlaylist(
        client: YouTubeClient,
        playlistId: String,
    ) = executeRequest(buildLikePlaylistRequest(client, locale, currentAuthState(), playlistId))

    suspend fun unlikePlaylist(
        client: YouTubeClient,
        playlistId: String,
    ) = executeRequest(buildLikePlaylistRequest(client, locale, currentAuthState(), playlistId, unlike = true))

    suspend fun addToPlaylist(
        client: YouTubeClient,
        playlistId: String,
        videoId: String,
    ) = executeRequest(buildAddToPlaylistRequest(client, locale, currentAuthState(), playlistId, videoId))

    suspend fun addPlaylistToPlaylist(
        client: YouTubeClient,
        playlistId: String,
        addPlaylistId: String,
    ) = executeRequest(buildAddPlaylistToPlaylistRequest(client, locale, currentAuthState(), playlistId, addPlaylistId))

    suspend fun removeFromPlaylist(
        client: YouTubeClient,
        playlistId: String,
        videoId: String,
        setVideoId: String,
    ) = executeRequest(buildRemoveFromPlaylistRequest(client, locale, currentAuthState(), playlistId, videoId, setVideoId))

    suspend fun moveSongPlaylist(
        client: YouTubeClient,
        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String?,
    ) = executeRequest(buildMoveSongPlaylistRequest(client, locale, currentAuthState(), playlistId, setVideoId, successorSetVideoId))

    suspend fun createPlaylist(
        client: YouTubeClient,
        title: String,
    ) = executeRequest(buildCreatePlaylistRequest(client, locale, currentAuthState(), title))

    suspend fun renamePlaylist(
        client: YouTubeClient,
        playlistId: String,
        name: String,
    ) = executeRequest(buildRenamePlaylistRequest(client, locale, currentAuthState(), playlistId, name))

    suspend fun deletePlaylist(
        client: YouTubeClient,
        playlistId: String,
    ) = executeRequest(buildDeletePlaylistRequest(client, locale, currentAuthState(), playlistId))

    private suspend fun returnYouTubeDislike(videoId: String) = withRetry {
        httpClient.get("https://returnyoutubedislikeapi.com/Votes?videoId=$videoId") {
            contentType(ContentType.Application.Json)
        }
    }


    suspend fun getMediaInfo(videoId: String): Result<MediaInfo> =
        runCatching {
            val response = next(client = YouTubeClient.WEB, videoId, null, null, null, null, null).body<NextResponse>()

            val baseForInfo =
                response.contents.twoColumnWatchNextResults
                    ?.results
                    ?.results
                    ?.content
                    ?.find {
                        it?.videoSecondaryInfoRenderer != null
                    }?.videoSecondaryInfoRenderer

            val baseForTitle =
                response.contents.twoColumnWatchNextResults
                    ?.results
                    ?.results
                    ?.content
                    ?.find {
                        it?.videoPrimaryInfoRenderer != null
                    }?.videoPrimaryInfoRenderer

            val returnYouTubeDislikeResponse =
                returnYouTubeDislike(videoId).body<ReturnYouTubeDislikeResponse>()

            return@runCatching MediaInfo(
                videoId = videoId,
                title = baseForTitle
                    ?.title
                    ?.runs
                    ?.firstOrNull()
                    ?.text,
                author = baseForInfo
                    ?.owner
                    ?.videoOwnerRenderer
                    ?.title
                    ?.runs
                    ?.firstOrNull()
                    ?.text,
                authorId =
                    baseForInfo
                        ?.owner
                        ?.videoOwnerRenderer
                        ?.navigationEndpoint
                        ?.browseEndpoint
                        ?.browseId,
                authorThumbnail =
                    baseForInfo
                        ?.owner
                        ?.videoOwnerRenderer
                        ?.thumbnail
                        ?.thumbnails
                        ?.find {
                            it.height == 48
                        }?.url
                        ?.replace("s48", "s960"),
                description = baseForInfo?.attributedDescription?.content,
                subscribers =
                    baseForInfo
                        ?.owner
                        ?.videoOwnerRenderer
                        ?.subscriberCountText
                        ?.simpleText?.split(" ")?.firstOrNull(),
                uploadDate = baseForTitle?.dateText?.simpleText,
                viewCount = returnYouTubeDislikeResponse.viewCount,
                like = returnYouTubeDislikeResponse.likes,
                dislike = returnYouTubeDislikeResponse.dislikes,
            )

        }


}
