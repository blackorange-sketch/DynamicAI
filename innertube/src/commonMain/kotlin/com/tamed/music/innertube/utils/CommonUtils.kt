/*
 * Tamed Project (2026)
 * Original project contributors
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.tamed.music.innertube.utils

fun String.parseTime(): Int? {
    return try {
        val parts = split(":").map { it.toInt() }
        when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}
