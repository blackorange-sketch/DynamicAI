/*
 * Tamed Project (2026)
 * Original project contributors
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.tamed.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class ResponseContext(
    val visitorData: String?,
    val serviceTrackingParams: List<ServiceTrackingParam>?,
) {
    @Serializable
    data class ServiceTrackingParam(
        val params: List<Param>,
        val service: String,
    ) {
        @Serializable
        data class Param(
            val key: String,
            val value: String,
        )
    }
}
