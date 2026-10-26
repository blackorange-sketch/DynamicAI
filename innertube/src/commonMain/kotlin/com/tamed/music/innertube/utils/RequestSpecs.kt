/*
 * Tamed Project (2026)
 * Original project contributors
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.tamed.music.innertube.utils

import com.tamed.music.innertube.PlaybackAuthState
import com.tamed.music.innertube.models.YouTubeClient
import com.tamed.music.innertube.models.YouTubeLocale

internal enum class InnerTubeRequestMethod {
    GET,
    POST,
}

internal data class InnerTubeRequestSpec<T>(
    val method: InnerTubeRequestMethod,
    val url: String,
    val client: YouTubeClient,
    val authState: PlaybackAuthState,
    val setLogin: Boolean,
    val body: T? = null,
    val queryParameters: List<Pair<String, String>> = emptyList(),
    val useJsonContentType: Boolean = true,
)

internal fun buildSearchRequest(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    includeLoginContext: Boolean,
    query: String?,
    params: String?,
    continuation: String?,
): InnerTubeRequestSpec<Any> {
    val continuationParameters = continuation?.let {
        listOf("continuation" to it, "ctoken" to it)
    }.orEmpty()
    return InnerTubeRequestSpec(
        method = InnerTubeRequestMethod.POST,
        url = "search",
        client = client,
        authState = authState,
        setLogin = includeLoginContext,
        body = buildSearchBody(client, locale, authState, includeLoginContext, query, params),
        queryParameters = continuationParameters,
    )
}

internal fun buildPlayerRequest(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    videoId: String,
    playlistId: String?,
    signatureTimestamp: Int?,
    poToken: String?,
    setLogin: Boolean,
): InnerTubeRequestSpec<Any> {
    return InnerTubeRequestSpec(
        method = InnerTubeRequestMethod.POST,
        url = "player",
        client = client,
        authState = authState,
        setLogin = setLogin,
        body = buildPlayerBody(client, locale, authState, videoId, playlistId, signatureTimestamp, poToken, setLogin),
    )
}

internal fun buildRegisterPlaybackRequest(
    url: String,
    client: YouTubeClient,
    authState: PlaybackAuthState,
    cpn: String,
    playlistId: String?,
    poToken: String?,
): InnerTubeRequestSpec<Nothing> {
    val parameters = buildList {
        add("ver" to "2")
        add("c" to client.clientName)
        add("cpn" to cpn)
        if (!poToken.isNullOrBlank()) {
            add("pot" to poToken)
        }
        if (playlistId != null) {
            add("list" to playlistId)
            add("referrer" to "https://music.youtube.com/playlist?list=$playlistId")
        }
    }
    return InnerTubeRequestSpec(
        method = InnerTubeRequestMethod.GET,
        url = url,
        client = client,
        authState = authState,
        setLogin = true,
        queryParameters = parameters,
    )
}

internal fun buildBrowseRequest(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    includeLoginContext: Boolean,
    browseId: String?,
    params: String?,
    continuation: String?,
): InnerTubeRequestSpec<Any> {
    return InnerTubeRequestSpec(
        method = InnerTubeRequestMethod.POST,
        url = "browse",
        client = client,
        authState = authState,
        setLogin = includeLoginContext,
        body = buildBrowseBody(client, locale, authState, includeLoginContext, browseId, params, continuation),
    )
}

internal fun buildNextRequest(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    videoId: String?,
    playlistId: String?,
    playlistSetVideoId: String?,
    index: Int?,
    params: String?,
    continuation: String?,
): InnerTubeRequestSpec<Any> {
    return InnerTubeRequestSpec(
        method = InnerTubeRequestMethod.POST,
        url = "next",
        client = client,
        authState = authState,
        setLogin = true,
        body = buildNextBody(client, locale, authState, videoId, playlistId, playlistSetVideoId, index, params, continuation),
    )
}

internal fun buildSearchSuggestionsRequest(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    input: String,
): InnerTubeRequestSpec<Any> {
    return InnerTubeRequestSpec(
        method = InnerTubeRequestMethod.POST,
        url = "music/get_search_suggestions",
        client = client,
        authState = authState,
        setLogin = false,
        body = buildSearchSuggestionsBody(client, locale, authState, input),
    )
}

internal fun buildGetQueueRequest(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    videoIds: List<String>?,
    playlistId: String?,
): InnerTubeRequestSpec<Any> {
    return InnerTubeRequestSpec(
        method = InnerTubeRequestMethod.POST,
        url = "music/get_queue",
        client = client,
        authState = authState,
        setLogin = false,
        body = buildGetQueueBody(client, locale, authState, videoIds, playlistId),
    )
}

internal fun buildGetTranscriptRequest(
    client: YouTubeClient,
    locale: YouTubeLocale,
    videoId: String,
): InnerTubeRequestSpec<Any> {
    return InnerTubeRequestSpec(
        method = InnerTubeRequestMethod.POST,
        url = "https://music.youtube.com/youtubei/v1/get_transcript",
        client = client,
        authState = PlaybackAuthState.EMPTY,
        setLogin = false,
        body = buildGetTranscriptBody(client, locale, videoId),
        queryParameters = listOf("key" to "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3"),
    )
}

internal fun buildSwJsDataRequest(): InnerTubeRequestSpec<Nothing> {
    return InnerTubeRequestSpec(
        method = InnerTubeRequestMethod.GET,
        url = "https://music.youtube.com/sw.js_data",
        client = YouTubeClient.WEB_REMIX,
        authState = PlaybackAuthState.EMPTY,
        setLogin = false,
        useJsonContentType = false,
    )
}

internal fun buildAccountMenuRequest(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
): InnerTubeRequestSpec<Any> {
    return InnerTubeRequestSpec(
        method = InnerTubeRequestMethod.POST,
        url = "account/account_menu",
        client = client,
        authState = authState,
        setLogin = true,
        body = buildAccountMenuBody(client, locale, authState),
    )
}

internal fun buildLikeVideoRequest(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    videoId: String,
    unlike: Boolean = false,
): InnerTubeRequestSpec<Any> {
    return InnerTubeRequestSpec(
        method = InnerTubeRequestMethod.POST,
        url = if (unlike) "like/removelike" else "like/like",
        client = client,
        authState = authState,
        setLogin = true,
        body = buildLikeVideoBody(client, locale, authState, videoId),
    )
}

internal fun buildSubscribeRequest(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    channelId: String,
    unsubscribe: Boolean = false,
): InnerTubeRequestSpec<Any> {
    return InnerTubeRequestSpec(
        method = InnerTubeRequestMethod.POST,
        url = if (unsubscribe) "subscription/unsubscribe" else "subscription/subscribe",
        client = client,
        authState = authState,
        setLogin = true,
        body = buildSubscribeBody(client, locale, authState, channelId),
    )
}

internal fun buildLikePlaylistRequest(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    playlistId: String,
    unlike: Boolean = false,
): InnerTubeRequestSpec<Any> {
    return InnerTubeRequestSpec(
        method = InnerTubeRequestMethod.POST,
        url = if (unlike) "like/removelike" else "like/like",
        client = client,
        authState = authState,
        setLogin = true,
        body = buildLikePlaylistBody(client, locale, authState, playlistId),
    )
}

internal fun buildAddToPlaylistRequest(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    playlistId: String,
    videoId: String,
): InnerTubeRequestSpec<Any> {
    return InnerTubeRequestSpec(
        method = InnerTubeRequestMethod.POST,
        url = "browse/edit_playlist",
        client = client,
        authState = authState,
        setLogin = true,
        body = buildAddToPlaylistBody(client, locale, authState, playlistId, videoId),
    )
}

internal fun buildAddPlaylistToPlaylistRequest(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    playlistId: String,
    addPlaylistId: String,
): InnerTubeRequestSpec<Any> {
    return InnerTubeRequestSpec(
        method = InnerTubeRequestMethod.POST,
        url = "browse/edit_playlist",
        client = client,
        authState = authState,
        setLogin = true,
        body = buildAddPlaylistToPlaylistBody(client, locale, authState, playlistId, addPlaylistId),
    )
}

internal fun buildRemoveFromPlaylistRequest(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    playlistId: String,
    videoId: String,
    setVideoId: String,
): InnerTubeRequestSpec<Any> {
    return InnerTubeRequestSpec(
        method = InnerTubeRequestMethod.POST,
        url = "browse/edit_playlist",
        client = client,
        authState = authState,
        setLogin = true,
        body = buildRemoveFromPlaylistBody(client, locale, authState, playlistId, videoId, setVideoId),
    )
}

internal fun buildMoveSongPlaylistRequest(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    playlistId: String,
    setVideoId: String,
    successorSetVideoId: String?,
): InnerTubeRequestSpec<Any> {
    return InnerTubeRequestSpec(
        method = InnerTubeRequestMethod.POST,
        url = "browse/edit_playlist",
        client = client,
        authState = authState,
        setLogin = true,
        body = buildMoveSongPlaylistBody(client, locale, authState, playlistId, setVideoId, successorSetVideoId),
    )
}

internal fun buildCreatePlaylistRequest(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    title: String,
): InnerTubeRequestSpec<Any> {
    return InnerTubeRequestSpec(
        method = InnerTubeRequestMethod.POST,
        url = "playlist/create",
        client = client,
        authState = authState,
        setLogin = true,
        body = buildCreatePlaylistBody(client, locale, authState, title),
    )
}

internal fun buildRenamePlaylistRequest(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    playlistId: String,
    name: String,
): InnerTubeRequestSpec<Any> {
    return InnerTubeRequestSpec(
        method = InnerTubeRequestMethod.POST,
        url = "browse/edit_playlist",
        client = client,
        authState = authState,
        setLogin = true,
        body = buildRenamePlaylistBody(client, locale, authState, playlistId, name),
    )
}

internal fun buildDeletePlaylistRequest(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    playlistId: String,
): InnerTubeRequestSpec<Any> {
    return InnerTubeRequestSpec(
        method = InnerTubeRequestMethod.POST,
        url = "playlist/delete",
        client = client,
        authState = authState,
        setLogin = true,
        body = buildDeletePlaylistBody(client, locale, authState, playlistId),
    )
}
