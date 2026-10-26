/*
 * Tamed Project (2026)
 * Original project contributors
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.tamed.music.innertube

import com.tamed.music.innertube.models.YTItem
import com.tamed.music.innertube.models.SongItem
import com.tamed.music.innertube.models.AlbumItem
import com.tamed.music.innertube.models.Artist
import com.tamed.music.innertube.models.ArtistItem
import com.tamed.music.innertube.models.MusicResponsiveListItemRenderer
import com.tamed.music.innertube.models.PlaylistItem
import com.tamed.music.innertube.models.YouTubeClient
import com.tamed.music.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.tamed.music.innertube.models.YouTubeLocale
import com.tamed.music.innertube.models.SearchSuggestions
import com.tamed.music.innertube.models.getContinuation
import com.tamed.music.innertube.models.getItems
import com.tamed.music.innertube.models.response.BrowseResponse
import com.tamed.music.innertube.models.response.GetSearchSuggestionsResponse
import com.tamed.music.innertube.models.response.SearchResponse
import com.tamed.music.innertube.pages.ArtistPage
import com.tamed.music.innertube.pages.HomePage
import com.tamed.music.innertube.pages.AlbumPage
import com.tamed.music.innertube.pages.NewReleaseAlbumPage
import com.tamed.music.innertube.pages.PlaylistPage
import com.tamed.music.innertube.pages.SearchPage
import com.tamed.music.innertube.pages.SearchResult
import com.tamed.music.innertube.pages.SearchSuggestionPage
import com.tamed.music.innertube.pages.SearchSummary
import com.tamed.music.innertube.pages.SearchSummaryPage
import com.tamed.music.innertube.utils.InnerTubeRequestMetadata
import com.tamed.music.innertube.utils.InnerTubeRequestMethod
import com.tamed.music.innertube.utils.InnerTubeRequestSpec
import com.tamed.music.innertube.utils.buildBrowseRequest
import com.tamed.music.innertube.utils.buildInnerTubeRequestMetadata
import com.tamed.music.innertube.utils.buildSearchRequest
import com.tamed.music.innertube.utils.buildSearchSuggestionsRequest
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

class PortableInnertube(
    private val httpClient: HttpClient = createPortableInnertubeHttpClient(),
    var locale: YouTubeLocale = DEFAULT_LOCALE,
) {
    private var authState: PlaybackAuthState = PlaybackAuthState.EMPTY

    var useLoginForBrowse: Boolean = false

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

    fun currentAuthState(): PlaybackAuthState = authState

    fun applyAuthState(value: PlaybackAuthState) {
        authState = value.normalized()
    }

    suspend fun search(
        query: String,
        continuation: String? = null,
        params: String? = null,
        client: YouTubeClient = WEB_REMIX,
    ): Result<SearchResult> = runCatching {
        val response = executeRequest(
            buildSearchRequest(
                client = client,
                locale = locale,
                authState = currentAuthState(),
                includeLoginContext = useLoginForBrowse,
                query = query.takeIf { continuation == null },
                params = params,
                continuation = continuation,
            ),
        ).body<SearchResponse>()

        if (continuation == null) {
            val contents = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents
            val musicShelf = contents?.mapNotNull { it.musicShelfRenderer }?.firstOrNull()
                ?: contents?.mapNotNull { it.itemSectionRenderer?.contents?.mapNotNull { it.musicShelfRenderer }?.firstOrNull() }?.firstOrNull()
            SearchResult(
                items = musicShelf?.contents?.getItems()?.mapNotNull(SearchPage::toYTItem).orEmpty(),
                continuation = musicShelf?.continuations?.getContinuation(),
            )
        } else {
            val items = response.continuationContents?.musicShelfContinuation?.contents
                ?.mapNotNull { SearchPage.toYTItem(it.musicResponsiveListItemRenderer) }
                .orEmpty()
            SearchResult(
                items = items,
                continuation = if (items.isEmpty()) {
                    null
                } else {
                    response.continuationContents?.musicShelfContinuation?.continuations?.getContinuation()
                },
            )
        }
    }

    suspend fun searchSuggestions(
        query: String,
        client: YouTubeClient = WEB_REMIX,
    ): Result<SearchSuggestions> = runCatching {
        val response = executeRequest(
            buildSearchSuggestionsRequest(
                client = client,
                locale = locale,
                authState = currentAuthState(),
                input = query,
            ),
        ).body<GetSearchSuggestionsResponse>()

        SearchSuggestions(
            queries = response.contents?.getOrNull(0)?.searchSuggestionsSectionRenderer?.contents?.mapNotNull { content ->
                content.searchSuggestionRenderer?.suggestion?.runs?.joinToString(separator = "") { it.text }
            }.orEmpty(),
            recommendedItems = response.contents?.getOrNull(1)?.searchSuggestionsSectionRenderer?.contents?.mapNotNull { content ->
                content.musicResponsiveListItemRenderer?.let(SearchSuggestionPage::fromMusicResponsiveListItemRenderer)
            }.orEmpty(),
        )
    }

    suspend fun searchSummary(
        query: String,
        client: YouTubeClient = WEB_REMIX,
    ): Result<SearchSummaryPage> = runCatching {
        val response = executeRequest(
            buildSearchRequest(
                client = client,
                locale = locale,
                authState = currentAuthState(),
                includeLoginContext = useLoginForBrowse,
                query = query,
                params = null,
                continuation = null,
            ),
        ).body<SearchResponse>()

        val contents = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents.orEmpty()

        val summaries = mutableListOf<SearchSummary>()
        val flatItems = mutableListOf<YTItem>()
        contents.forEach { content ->
            if (content.musicCardShelfRenderer != null) {
                val cardShelf = content.musicCardShelfRenderer
                val title = cardShelf.header?.musicCardShelfHeaderBasicRenderer?.title?.runs?.firstOrNull()?.text ?: "Top result"
                val cardItem = SearchSummaryPage.fromMusicCardShelfRenderer(cardShelf)
                val listItems = cardShelf.contents
                    ?.mapNotNull { it.musicResponsiveListItemRenderer }
                    ?.mapNotNull { SearchSummaryPage.fromMusicResponsiveListItemRenderer(it) }
                    .orEmpty()
                val items = listOfNotNull(cardItem).plus(listItems).distinctBy { it.id }
                if (items.isNotEmpty()) {
                    summaries.add(SearchSummary(title = title, items = items))
                }
            } else if (content.musicShelfRenderer != null) {
                val shelf = content.musicShelfRenderer
                val title = shelf.title?.runs?.firstOrNull()?.text ?: "Other"
                val items = shelf.contents?.getItems()
                    ?.mapNotNull { SearchSummaryPage.fromMusicResponsiveListItemRenderer(it) }
                    ?.distinctBy { it.id }
                    .orEmpty()
                if (items.isNotEmpty()) {
                    summaries.add(SearchSummary(title = title, items = items))
                }
            } else if (content.itemSectionRenderer != null) {
                content.itemSectionRenderer.contents?.forEach { itemSectionContent ->
                    if (itemSectionContent.musicShelfRenderer != null) {
                        val shelf = itemSectionContent.musicShelfRenderer
                        val title = shelf.title?.runs?.firstOrNull()?.text ?: "Other"
                        val items = shelf.contents?.getItems()
                            ?.mapNotNull { SearchSummaryPage.fromMusicResponsiveListItemRenderer(it) }
                            ?.distinctBy { it.id }
                            .orEmpty()
                        if (items.isNotEmpty()) {
                            summaries.add(SearchSummary(title = title, items = items))
                        }
                    } else if (itemSectionContent.gridRenderer != null) {
                        val grid = itemSectionContent.gridRenderer
                        val title = grid.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text ?: "Other"
                        val items = grid.items
                            .mapNotNull { it.musicTwoRowItemRenderer }
                            .mapNotNull { HomePage.Section.fromMusicTwoRowItemRenderer(it) }
                            .distinctBy { it.id }
                        if (items.isNotEmpty()) {
                            summaries.add(SearchSummary(title = title, items = items))
                        }
                    } else if (itemSectionContent.musicResponsiveListItemRenderer != null) {
                        SearchSummaryPage.fromMusicResponsiveListItemRenderer(itemSectionContent.musicResponsiveListItemRenderer)
                            ?.let { flatItems.add(it) }
                    }
                }
            } else if (content.musicCarouselShelfRenderer != null) {
                val carouselShelf = content.musicCarouselShelfRenderer
                val title = carouselShelf.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.firstOrNull()?.text ?: "Other"
                val twoRowItems = carouselShelf.contents
                    .mapNotNull { it.musicTwoRowItemRenderer }
                    .mapNotNull { HomePage.Section.fromMusicTwoRowItemRenderer(it) }
                val listItems = carouselShelf.contents
                    .mapNotNull { it.musicResponsiveListItemRenderer }
                    .mapNotNull { SearchSummaryPage.fromMusicResponsiveListItemRenderer(it) }
                val items = twoRowItems.plus(listItems).distinctBy { it.id }
                if (items.isNotEmpty()) {
                    summaries.add(SearchSummary(title = title, items = items))
                }
            } else if (content.gridRenderer != null) {
                val grid = content.gridRenderer
                val title = grid.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text ?: "Other"
                val items = grid.items
                    .mapNotNull { it.musicTwoRowItemRenderer }
                    .mapNotNull { HomePage.Section.fromMusicTwoRowItemRenderer(it) }
                    .distinctBy { it.id }
                if (items.isNotEmpty()) {
                    summaries.add(SearchSummary(title = title, items = items))
                }
            }
        }

        if (flatItems.isNotEmpty()) {
            val songs = flatItems.filterIsInstance<SongItem>().distinctBy { it.id }
            val albums = flatItems.filterIsInstance<AlbumItem>().distinctBy { it.id }
            val artists = flatItems.filterIsInstance<ArtistItem>().distinctBy { it.id }
            val playlists = flatItems.filterIsInstance<PlaylistItem>().distinctBy { it.id }

            if (songs.isNotEmpty()) {
                summaries.add(SearchSummary(title = "Songs", items = songs))
            }
            if (albums.isNotEmpty()) {
                summaries.add(SearchSummary(title = "Albums", items = albums))
            }
            if (artists.isNotEmpty()) {
                summaries.add(SearchSummary(title = "Artists", items = artists))
            }
            if (playlists.isNotEmpty()) {
                summaries.add(SearchSummary(title = "Playlists", items = playlists))
            }
        }

        SearchSummaryPage(summaries = summaries)
    }

    suspend fun home(
        continuation: String? = null,
        params: String? = null,
        client: YouTubeClient = WEB_REMIX,
    ): Result<HomePage> = runCatching {
        if (continuation != null) {
            val response = executeRequest(
                buildBrowseRequest(
                    client = client,
                    locale = locale,
                    authState = currentAuthState(),
                    includeLoginContext = true,
                    browseId = null,
                    params = null,
                    continuation = continuation,
                ),
            ).body<BrowseResponse>()

            val sections = response.continuationContents?.sectionListContinuation?.contents
                ?.mapNotNull { it.musicCarouselShelfRenderer }
                ?.mapNotNull(HomePage.Section::fromMusicCarouselShelfRenderer)
                .orEmpty()

            HomePage(
                chips = null,
                sections = sections,
                continuation = if (sections.isEmpty()) {
                    null
                } else {
                    response.continuationContents?.sectionListContinuation?.continuations?.getContinuation()
                },
            )
        } else {
            val response = executeRequest(
                buildBrowseRequest(
                    client = client,
                    locale = locale,
                    authState = currentAuthState(),
                    includeLoginContext = true,
                    browseId = "FEmusic_home",
                    params = params,
                    continuation = null,
                ),
            ).body<BrowseResponse>()

            val sectionList = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer
                ?: error("Missing home section list")

            HomePage(
                chips = sectionList.header?.chipCloudRenderer?.chips?.mapNotNull(HomePage.Chip::fromChipCloudChipRenderer),
                sections = sectionList.contents.orEmpty()
                    .mapNotNull { it.musicCarouselShelfRenderer }
                    .mapNotNull(HomePage.Section::fromMusicCarouselShelfRenderer),
                continuation = sectionList.continuations?.getContinuation(),
            )
        }
    }

    suspend fun newReleaseAlbums(
        client: YouTubeClient = WEB_REMIX,
    ): Result<List<AlbumItem>> = runCatching {
        val response = executeRequest(
            buildBrowseRequest(
                client = client,
                locale = locale,
                authState = currentAuthState(),
                includeLoginContext = false,
                browseId = "FEmusic_new_releases_albums",
                params = null,
                continuation = null,
            ),
        ).body<BrowseResponse>()

        val contents = response.contents
            ?.singleColumnBrowseResultsRenderer
            ?.tabs
            ?.firstOrNull()
            ?.tabRenderer
            ?.content
            ?.sectionListRenderer
            ?.contents
            .orEmpty()

        contents
            .asSequence()
            .flatMap { content ->
                when {
                    content.gridRenderer?.items != null -> {
                        content.gridRenderer.items
                            .asSequence()
                            .mapNotNull { it.musicTwoRowItemRenderer }
                            .mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
                    }
                    content.musicCarouselShelfRenderer?.contents != null -> {
                        content.musicCarouselShelfRenderer.contents
                            .asSequence()
                            .mapNotNull { it.musicTwoRowItemRenderer }
                            .mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
                    }
                    else -> emptySequence()
                }
            }
            .toList()
    }

    suspend fun album(
        browseId: String,
        withSongs: Boolean = true,
        client: YouTubeClient = WEB_REMIX,
    ): Result<AlbumPage> = runCatching {
        val response = executeRequest(
            buildBrowseRequest(
                client = client,
                locale = locale,
                authState = currentAuthState(),
                includeLoginContext = false,
                browseId = browseId,
                params = null,
                continuation = null,
            ),
        ).body<BrowseResponse>()

        val playlistId = AlbumPage.getPlaylistId(response)
            ?: throw IllegalStateException("Missing album playlist id for $browseId")
        val albumTitle = AlbumPage.getTitle(response)
            ?: throw IllegalStateException("Missing album title for $browseId")
        val albumArtists = AlbumPage.getArtists(response).takeIf { it.isNotEmpty() }
        val albumYear = AlbumPage.getYear(response)
        val albumThumbnail = AlbumPage.getThumbnail(response)
            ?: throw IllegalStateException("Missing album thumbnail url for $browseId")
        val albumItem = AlbumItem(
            browseId = browseId,
            playlistId = playlistId,
            title = albumTitle,
            artists = albumArtists,
            year = albumYear,
            thumbnail = albumThumbnail,
            explicit = false,
        )
        val albumDescription = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents
            ?.firstOrNull { it.musicDescriptionShelfRenderer != null }
            ?.musicDescriptionShelfRenderer?.description?.runs?.joinToString(separator = "") { it.text }
            ?: response.contents?.sectionListRenderer?.contents
                ?.firstOrNull { it.musicDescriptionShelfRenderer != null }
                ?.musicDescriptionShelfRenderer?.description?.runs?.joinToString(separator = "") { it.text }
            ?: response.header?.musicImmersiveHeaderRenderer?.description?.runs?.joinToString(separator = "") { it.text }

        val inlineSongs = if (withSongs) AlbumPage.getSongs(response, albumItem) else emptyList()
        val songs = if (withSongs) {
            val fetchedSongs = runCatching {
                albumSongs(playlistId, albumItem, client).getOrThrow()
            }.getOrElse { error ->
                if (inlineSongs.isNotEmpty()) inlineSongs else throw error
            }

            if (fetchedSongs.isEmpty() && inlineSongs.isNotEmpty()) inlineSongs else fetchedSongs
        } else {
            emptyList()
        }

        AlbumPage(
            album = albumItem,
            songs = songs,
            description = albumDescription,
            otherVersions = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer?.contents
                ?.mapNotNull { it.musicCarouselShelfRenderer }
                ?.flatMap { it.contents }
                ?.mapNotNull { it.musicTwoRowItemRenderer }
                ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
                ?.distinctBy { it.id }
                .orEmpty(),
        )
    }

    suspend fun albumSongs(
        playlistId: String,
        album: AlbumItem? = null,
        client: YouTubeClient = WEB_REMIX,
    ): Result<List<com.tamed.music.innertube.models.SongItem>> = runCatching {
        var response = executeRequest(
            buildBrowseRequest(
                client = client,
                locale = locale,
                authState = currentAuthState(),
                includeLoginContext = false,
                browseId = "VL$playlistId",
                params = null,
                continuation = null,
            ),
        ).body<BrowseResponse>()
        val songs = linkedMapOf<String, com.tamed.music.innertube.models.SongItem>()

        fun appendSongs(
            candidates: List<MusicResponsiveListItemRenderer>,
            parsedSongs: List<com.tamed.music.innertube.models.SongItem>,
            source: String,
        ): Boolean {
            if (candidates.isNotEmpty() && parsedSongs.isEmpty()) {
                throw IllegalStateException("Unable to parse album songs from $source for playlist $playlistId")
            }

            val previousSize = songs.size
            parsedSongs.forEach { song ->
                if (song.id !in songs) {
                    songs[song.id] = song
                }
            }
            return songs.size > previousSize
        }

        appendSongs(
            candidates = AlbumPage.getSongRenderers(response),
            parsedSongs = AlbumPage.getSongs(response, album),
            source = "initial response",
        )

        var continuation = AlbumPage.getSongContinuation(response)
        val seenContinuations = mutableSetOf<String>()
        var requestCount = 0
        var consecutiveEmptyResponses = 0

        while (continuation != null && requestCount < 50) {
            if (continuation in seenContinuations) break
            seenContinuations.add(continuation)
            requestCount++

            response = executeRequest(
                buildBrowseRequest(
                    client = client,
                    locale = locale,
                    authState = currentAuthState(),
                    includeLoginContext = false,
                    browseId = null,
                    params = null,
                    continuation = continuation,
                ),
            ).body<BrowseResponse>()

            val newSongCandidates = AlbumPage.getContinuationSongRenderers(response)
            val newSongs = AlbumPage.getContinuationSongs(response, album)
            val hasNewSongs = if (newSongCandidates.isNotEmpty() || newSongs.isNotEmpty()) {
                appendSongs(
                    candidates = newSongCandidates,
                    parsedSongs = newSongs,
                    source = "continuation response",
                )
            } else {
                false
            }

            if (!hasNewSongs) {
                consecutiveEmptyResponses++
                if (consecutiveEmptyResponses >= 2) break
            } else {
                consecutiveEmptyResponses = 0
            }

            continuation = AlbumPage.getNextSongContinuation(response)
        }

        songs.values.toList()
    }

    suspend fun artist(
        browseId: String,
        client: YouTubeClient = WEB_REMIX,
    ): Result<ArtistPage> = runCatching {
        val response = executeRequest(
            buildBrowseRequest(
                client = client,
                locale = locale,
                authState = currentAuthState(),
                includeLoginContext = false,
                browseId = browseId,
                params = null,
                continuation = null,
            ),
        ).body<BrowseResponse>()

        ArtistPage(
            artist = ArtistItem(
                id = browseId,
                title = response.header?.musicImmersiveHeaderRenderer?.title?.runs?.firstOrNull()?.text
                    ?: response.header?.musicVisualHeaderRenderer?.title?.runs?.firstOrNull()?.text
                    ?: response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text
                    ?: throw IllegalStateException("Missing artist title for $browseId"),
                thumbnail = response.header?.musicImmersiveHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
                    ?: response.header?.musicVisualHeaderRenderer?.foregroundThumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
                    ?: response.header?.musicDetailHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl(),
                channelId = response.header?.musicImmersiveHeaderRenderer?.subscriptionButton?.subscribeButtonRenderer?.channelId,
                playEndpoint = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                    ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicShelfRenderer
                    ?.contents?.firstOrNull()?.musicResponsiveListItemRenderer?.overlay?.musicItemThumbnailOverlayRenderer
                    ?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint,
                shuffleEndpoint = response.header?.musicImmersiveHeaderRenderer?.playButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint
                    ?: response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer
                        ?.contents?.firstOrNull()?.musicShelfRenderer?.contents?.firstOrNull()?.musicResponsiveListItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
                radioEndpoint = response.header?.musicImmersiveHeaderRenderer?.startRadioButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint,
            ),
            sections = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents
                ?.mapNotNull(ArtistPage::fromSectionListRendererContent)
                .orEmpty(),
            description = response.header?.musicImmersiveHeaderRenderer?.description?.runs?.firstOrNull()?.text,
            subscriberCountText = response.header?.musicImmersiveHeaderRenderer
                ?.subscriptionButton2?.subscribeButtonRenderer
                ?.subscriberCountWithSubscribeText?.runs?.firstOrNull()?.text,
            monthlyListenerCount = response.header?.musicImmersiveHeaderRenderer
                ?.monthlyListenerCount?.runs?.firstOrNull()?.text,
        )
    }

    suspend fun playlist(
        playlistId: String,
        client: YouTubeClient = WEB_REMIX,
    ): Result<PlaylistPage> = runCatching {
        val response = executeRequest(
            buildBrowseRequest(
                client = client,
                locale = locale,
                authState = currentAuthState(),
                includeLoginContext = true,
                browseId = "VL$playlistId",
                params = null,
                continuation = null,
            ),
        ).body<BrowseResponse>()

        val base = response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
        val header = base?.musicResponsiveHeaderRenderer ?: base?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicResponsiveHeaderRenderer
            ?: throw IllegalStateException("PLAYLIST_PRIVATE")

        val title = header.title.runs?.firstOrNull()?.text ?: throw IllegalStateException("PLAYLIST_PRIVATE")
        val thumbnail = header.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url
            ?: throw IllegalStateException("PLAYLIST_PRIVATE")
        val headerMenuItems = header.buttons.firstOrNull { it.menuRenderer != null }?.menuRenderer?.items.orEmpty()

        PlaylistPage(
            playlist = PlaylistItem(
                id = playlistId,
                title = title,
                author = header.straplineTextOne?.runs?.firstOrNull()?.let {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId,
                    )
                },
                songCountText = header.secondSubtitle?.runs?.firstOrNull()?.text,
                thumbnail = thumbnail,
                playEndpoint = header.buttons.firstOrNull()?.musicPlayButtonRenderer?.playNavigationEndpoint?.anyWatchEndpoint,
                shuffleEndpoint = headerMenuItems.firstOrNull()?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
                radioEndpoint = headerMenuItems.find {
                    it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
                isEditable = base?.musicEditablePlaylistDetailHeaderRenderer != null,
            ),
            songs = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
                ?.contents?.firstOrNull()?.musicPlaylistShelfRenderer?.contents?.getItems()?.mapNotNull {
                    PlaylistPage.fromMusicResponsiveListItemRenderer(it)
                }.orEmpty(),
            songsContinuation = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
                ?.contents?.firstOrNull()?.musicPlaylistShelfRenderer?.contents?.getContinuation(),
            continuation = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
                ?.continuations?.getContinuation(),
        )
    }

    private suspend fun <T> executeRequest(spec: InnerTubeRequestSpec<T>) = when (spec.method) {
        InnerTubeRequestMethod.GET -> httpClient.get(spec.url) {
            applyRequestSpec(spec)
        }
        InnerTubeRequestMethod.POST -> httpClient.post(spec.url) {
            applyRequestSpec(spec)
        }
    }

    private fun <T> HttpRequestBuilder.applyRequestSpec(spec: InnerTubeRequestSpec<T>) {
        if (spec.useJsonContentType) {
            applyRequestMetadata(
                buildInnerTubeRequestMetadata(
                    client = spec.client,
                    authState = spec.authState,
                    setLogin = spec.setLogin,
                ),
            )
            contentType(ContentType.Application.Json)
        }
        for ((name, value) in spec.queryParameters) {
            parameter(name, value)
        }
        spec.body?.let(::setBody)
    }

    private fun HttpRequestBuilder.applyRequestMetadata(metadata: InnerTubeRequestMetadata) {
        headers {
            for ((name, value) in metadata.headers) {
                append(name, value)
            }
        }
        userAgent(metadata.userAgent)
        parameter("prettyPrint", false)
    }

    companion object {
        val DEFAULT_LOCALE = YouTubeLocale(
            gl = "US",
            hl = "en-US",
        )
    }
}

@OptIn(ExperimentalSerializationApi::class)
internal fun HttpClientConfig<*>.configurePortableInnertubeClient() {
    expectSuccess = true

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            },
        )
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

    defaultRequest {
        url(YouTubeClient.API_URL_YOUTUBE_MUSIC)
    }
}

expect fun createPortableInnertubeHttpClient(): HttpClient
