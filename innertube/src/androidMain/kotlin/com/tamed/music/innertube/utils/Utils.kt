/*
 * Tamed Project (2026)
 * Original project contributors
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.tamed.music.innertube.utils

import com.tamed.music.innertube.YouTube
import com.tamed.music.innertube.pages.LibraryPage
import com.tamed.music.innertube.pages.PlaylistContinuationPage
import com.tamed.music.innertube.pages.PlaylistPage

@JvmName("completedLibrary")
suspend fun Result<PlaylistPage>.completed(): Result<PlaylistPage> = runCatching {
    completePlaylistPage(getOrThrow()) { continuation ->
        YouTube.playlistContinuation(continuation).getOrNull()
    }
}

internal suspend fun completePlaylistPage(
    page: PlaylistPage,
    fetchContinuationPage: suspend (String) -> PlaylistContinuationPage?,
): PlaylistPage {
    val songs = page.songs.toMutableList()
    var continuation = page.songsContinuation.normalizedContinuation()
        ?: page.continuation.normalizedContinuation()
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0
    val maxRequests = 50
    var consecutiveEmptyResponses = 0

    while (continuation != null && requestCount < maxRequests) {
        if (continuation in seenContinuations) {
            break
        }
        seenContinuations.add(continuation)
        requestCount++

        val continuationPage = fetchContinuationPage(continuation) ?: break

        if (continuationPage.songs.isEmpty()) {
            consecutiveEmptyResponses++
            if (consecutiveEmptyResponses >= 2) break
        } else {
            consecutiveEmptyResponses = 0
            songs += continuationPage.songs
        }

        continuation = continuationPage.continuation.normalizedContinuation()
    }

    return page.copy(
        songs = songs,
        songsContinuation = null,
        continuation = null
    )
}

@JvmName("completedPlaylist")
suspend fun Result<LibraryPage>.completed(): Result<LibraryPage> = runCatching {
    val page = getOrThrow()
    val items = page.items.toMutableList()
    var continuation = page.continuation
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0
    val maxRequests = 50
    var consecutiveEmptyResponses = 0
    
    while (continuation != null && requestCount < maxRequests) {
        if (continuation in seenContinuations) {
            break
        }
        seenContinuations.add(continuation)
        requestCount++
        
        val continuationPage = YouTube.libraryContinuation(continuation).getOrNull() ?: break
        
        if (continuationPage.items.isEmpty()) {
            consecutiveEmptyResponses++
            if (consecutiveEmptyResponses >= 2) break
        } else {
            consecutiveEmptyResponses = 0
            items += continuationPage.items
        }
        
        continuation = continuationPage.continuation
    }
    LibraryPage(
        items = items,
        continuation = null
    )
}

fun isPrivateId(browseId: String): Boolean {
    return browseId.contains("privately")
}

private fun String?.normalizedContinuation(): String? = this?.takeUnless(String::isBlank)
