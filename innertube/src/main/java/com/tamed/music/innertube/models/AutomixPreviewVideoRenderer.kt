/*
 * Tamed Project (2026)
 * Original project contributors
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.tamed.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class AutomixPreviewVideoRenderer(
    val content: Content,
) {
    @Serializable
    data class Content(
        val automixPlaylistVideoRenderer: AutomixPlaylistVideoRenderer,
    ) {
        @Serializable
        data class AutomixPlaylistVideoRenderer(
            val navigationEndpoint: NavigationEndpoint,
        )
    }
}
