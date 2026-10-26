/*
 * Tamed Project (2026)
 * Original project contributors
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.tamed.music.innertube.utils

import com.tamed.music.innertube.PlaybackAuthState
import com.tamed.music.innertube.models.YouTubeClient

internal data class InnerTubeRequestMetadata(
    val headers: Map<String, String>,
    val userAgent: String,
)

internal fun buildInnerTubeRequestMetadata(
    client: YouTubeClient,
    authState: PlaybackAuthState,
    setLogin: Boolean,
): InnerTubeRequestMetadata {
    val requestOrigin = client.requestOrigin()
    val requestReferer = client.requestReferer()
    val headers = linkedMapOf(
        "X-Goog-Api-Format-Version" to "1",
        "X-YouTube-Client-Name" to client.clientId,
        "X-YouTube-Client-Version" to client.clientVersion,
        "X-Origin" to requestOrigin,
        "Referer" to requestReferer,
    )

    authState.visitorData?.let { headers["X-Goog-Visitor-Id"] = it }

    if (setLogin && client.loginSupported) {
        authState.cookie?.let { cookie ->
            headers["cookie"] = cookie
            buildSapisidAuthorization(cookie, requestOrigin)?.let { headers["Authorization"] = it }
        }
    }

    return InnerTubeRequestMetadata(
        headers = headers,
        userAgent = client.userAgent,
    )
}

internal fun buildSapisidAuthorization(
    cookie: String,
    requestOrigin: String,
): String? {
    val sapisid = parseCookieString(cookie)["SAPISID"] ?: return null
    val currentTimeSeconds = currentTimeMillis() / 1000
    val sapisidHash = sha1("$currentTimeSeconds $sapisid $requestOrigin")
    return "SAPISIDHASH ${currentTimeSeconds}_$sapisidHash"
}
