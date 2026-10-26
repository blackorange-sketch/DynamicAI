/*
 * Tamed Project (2026)
 * Original project contributors
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.tamed.music.innertube

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun createPortableInnertubeHttpClient(): HttpClient = HttpClient(OkHttp) {
    configurePortableInnertubeClient()
}
