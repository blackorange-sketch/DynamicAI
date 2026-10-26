/*
 * Tamed Project (2026)
 * Original project contributors
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.tamed.music.innertube.utils

import com.tamed.music.innertube.PlaybackAuthState
import com.tamed.music.innertube.models.Context
import com.tamed.music.innertube.models.body.Action
import com.tamed.music.innertube.models.YouTubeClient
import com.tamed.music.innertube.models.YouTubeLocale
import com.tamed.music.innertube.models.body.CreatePlaylistBody
import com.tamed.music.innertube.models.body.AccountMenuBody
import com.tamed.music.innertube.models.body.BrowseBody
import com.tamed.music.innertube.models.body.EditPlaylistBody
import com.tamed.music.innertube.models.body.GetQueueBody
import com.tamed.music.innertube.models.body.GetSearchSuggestionsBody
import com.tamed.music.innertube.models.body.GetTranscriptBody
import com.tamed.music.innertube.models.body.LikeBody
import com.tamed.music.innertube.models.body.NextBody
import com.tamed.music.innertube.models.body.PlayerBody
import com.tamed.music.innertube.models.body.PlaylistDeleteBody
import com.tamed.music.innertube.models.body.SearchBody
import com.tamed.music.innertube.models.body.SubscribeBody
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal fun buildSearchBody(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    includeLoginContext: Boolean,
    query: String?,
    params: String?,
): SearchBody {
    return SearchBody(
        context = client.toPlaybackContext(locale, authState, includeLoginContext),
        query = query,
        params = params,
    )
}

internal fun buildPlayerBody(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    videoId: String,
    playlistId: String?,
    signatureTimestamp: Int?,
    poToken: String?,
    setLogin: Boolean,
): PlayerBody {
    return PlayerBody(
        context = client
            .toPlaybackContext(locale, authState, includeLoginContext = setLogin && client.loginSupported)
            .let { context ->
                if (client.isEmbedded) {
                    context.copy(
                        thirdParty = Context.ThirdParty(
                            embedUrl = "https://www.youtube.com/watch?v=$videoId",
                        ),
                    )
                } else {
                    context
                }
            },
        videoId = videoId,
        playlistId = playlistId,
        playbackContext = if (client.useSignatureTimestamp && signatureTimestamp != null) {
            PlayerBody.PlaybackContext(
                PlayerBody.PlaybackContext.ContentPlaybackContext(signatureTimestamp),
            )
        } else {
            null
        },
        serviceIntegrityDimensions = poToken?.let(PlayerBody::ServiceIntegrityDimensions),
    )
}

internal fun buildBrowseBody(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    includeLoginContext: Boolean,
    browseId: String?,
    params: String?,
    continuation: String?,
): BrowseBody {
    return BrowseBody(
        context = client.toPlaybackContext(locale, authState, includeLoginContext),
        browseId = browseId,
        params = params,
        continuation = continuation,
    )
}

internal fun buildNextBody(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    videoId: String?,
    playlistId: String?,
    playlistSetVideoId: String?,
    index: Int?,
    params: String?,
    continuation: String?,
): NextBody {
    return NextBody(
        context = client.toPlaybackContext(locale, authState, includeLoginContext = true),
        videoId = videoId,
        playlistId = playlistId,
        playlistSetVideoId = playlistSetVideoId,
        index = index,
        params = params,
        continuation = continuation,
    )
}

internal fun buildSearchSuggestionsBody(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    input: String,
): GetSearchSuggestionsBody {
    return GetSearchSuggestionsBody(
        context = client.toPlaybackContext(locale, authState, includeLoginContext = false),
        input = input,
    )
}

internal fun buildGetQueueBody(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    videoIds: List<String>?,
    playlistId: String?,
): GetQueueBody {
    return GetQueueBody(
        context = client.toPlaybackContext(locale, authState, includeLoginContext = false),
        videoIds = videoIds,
        playlistId = playlistId,
    )
}

@OptIn(ExperimentalEncodingApi::class)
internal fun buildGetTranscriptBody(
    client: YouTubeClient,
    locale: YouTubeLocale,
    videoId: String,
): GetTranscriptBody {
    return GetTranscriptBody(
        context = client.toContext(locale, visitorData = null, dataSyncId = null),
        params = Base64.Default.encode("\n${11.toChar()}$videoId".encodeToByteArray()),
    )
}

internal fun buildAccountMenuBody(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
): AccountMenuBody {
    return AccountMenuBody(
        context = client.toPlaybackContext(locale, authState, includeLoginContext = true),
    )
}

internal fun buildLikeVideoBody(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    videoId: String,
): LikeBody {
    return LikeBody(
        context = client.toPlaybackContext(locale, authState, includeLoginContext = true),
        target = LikeBody.Target.VideoTarget(videoId),
    )
}

internal fun buildLikePlaylistBody(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    playlistId: String,
): LikeBody {
    return LikeBody(
        context = client.toPlaybackContext(locale, authState, includeLoginContext = true),
        target = LikeBody.Target.PlaylistTarget(playlistId),
    )
}

internal fun buildSubscribeBody(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    channelId: String,
): SubscribeBody {
    return SubscribeBody(
        channelIds = listOf(channelId),
        context = client.toPlaybackContext(locale, authState, includeLoginContext = true),
    )
}

internal fun buildAddToPlaylistBody(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    playlistId: String,
    videoId: String,
): EditPlaylistBody {
    return buildEditPlaylistBody(
        client = client,
        locale = locale,
        authState = authState,
        playlistId = playlistId.removePrefix("VL"),
        actions = listOf(Action.AddVideoAction(addedVideoId = videoId)),
    )
}

internal fun buildAddPlaylistToPlaylistBody(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    playlistId: String,
    addPlaylistId: String,
): EditPlaylistBody {
    return buildEditPlaylistBody(
        client = client,
        locale = locale,
        authState = authState,
        playlistId = playlistId.removePrefix("VL"),
        actions = listOf(Action.AddPlaylistAction(addedFullListId = addPlaylistId)),
    )
}

internal fun buildRemoveFromPlaylistBody(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    playlistId: String,
    videoId: String,
    setVideoId: String,
): EditPlaylistBody {
    return buildEditPlaylistBody(
        client = client,
        locale = locale,
        authState = authState,
        playlistId = playlistId.removePrefix("VL"),
        actions = listOf(
            Action.RemoveVideoAction(
                removedVideoId = videoId,
                setVideoId = setVideoId,
            ),
        ),
    )
}

internal fun buildMoveSongPlaylistBody(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    playlistId: String,
    setVideoId: String,
    successorSetVideoId: String?,
): EditPlaylistBody {
    return buildEditPlaylistBody(
        client = client,
        locale = locale,
        authState = authState,
        playlistId = playlistId,
        actions = listOf(
            Action.MoveVideoAction(
                movedSetVideoIdSuccessor = successorSetVideoId,
                setVideoId = setVideoId,
            ),
        ),
    )
}

internal fun buildCreatePlaylistBody(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    title: String,
): CreatePlaylistBody {
    return CreatePlaylistBody(
        context = client.toPlaybackContext(locale, authState, includeLoginContext = true),
        title = title,
    )
}

internal fun buildRenamePlaylistBody(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    playlistId: String,
    name: String,
): EditPlaylistBody {
    return buildEditPlaylistBody(
        client = client,
        locale = locale,
        authState = authState,
        playlistId = playlistId,
        actions = listOf(Action.RenamePlaylistAction(playlistName = name)),
    )
}

internal fun buildDeletePlaylistBody(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    playlistId: String,
): PlaylistDeleteBody {
    return PlaylistDeleteBody(
        context = client.toPlaybackContext(locale, authState, includeLoginContext = true),
        playlistId = playlistId,
    )
}

private fun buildEditPlaylistBody(
    client: YouTubeClient,
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    playlistId: String,
    actions: List<Action>,
): EditPlaylistBody {
    return EditPlaylistBody(
        context = client.toPlaybackContext(locale, authState, includeLoginContext = true),
        playlistId = playlistId,
        actions = actions,
    )
}

private fun YouTubeClient.toPlaybackContext(
    locale: YouTubeLocale,
    authState: PlaybackAuthState,
    includeLoginContext: Boolean,
) = toContext(
    locale = locale,
    visitorData = authState.visitorData,
    dataSyncId = if (includeLoginContext) authState.dataSyncId else null,
)
