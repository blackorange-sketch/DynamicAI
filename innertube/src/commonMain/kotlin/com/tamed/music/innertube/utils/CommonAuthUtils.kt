/*
 * Tamed Project (2026)
 * Original project contributors
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.tamed.music.innertube.utils

fun parseCookieString(cookie: String): Map<String, String> =
    cookie.split(";")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { part ->
            val splitIndex = part.indexOf('=')
            if (splitIndex == -1) {
                null
            } else {
                val key = part.substring(0, splitIndex).trim()
                if (key.isEmpty()) null else key to part.substring(splitIndex + 1).trim()
            }
        }
        .toMap()

internal fun sha1(input: String): String {
    val source = input.encodeToByteArray()
    val bitLength = source.size.toLong() * 8
    val paddedSize = ((source.size + 9 + 63) / 64) * 64
    val padded = ByteArray(paddedSize)

    source.copyInto(padded)
    padded[source.size] = 0x80.toByte()
    for (index in 0 until 8) {
        padded[padded.lastIndex - index] = (bitLength ushr (index * 8)).toByte()
    }

    var h0 = 0x67452301
    var h1 = 0xEFCDAB89.toInt()
    var h2 = 0x98BADCFE.toInt()
    var h3 = 0x10325476
    var h4 = 0xC3D2E1F0.toInt()
    val words = IntArray(80)

    for (offset in padded.indices step 64) {
        for (index in 0 until 16) {
            val base = offset + index * 4
            words[index] = ((padded[base].toInt() and 0xFF) shl 24) or
                ((padded[base + 1].toInt() and 0xFF) shl 16) or
                ((padded[base + 2].toInt() and 0xFF) shl 8) or
                (padded[base + 3].toInt() and 0xFF)
        }
        for (index in 16 until 80) {
            words[index] = (words[index - 3] xor words[index - 8] xor words[index - 14] xor words[index - 16]).rotateLeft(1)
        }

        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4

        for (index in 0 until 80) {
            val (f, k) = when (index) {
                in 0..19 -> ((b and c) or (b.inv() and d)) to 0x5A827999
                in 20..39 -> (b xor c xor d) to 0x6ED9EBA1
                in 40..59 -> ((b and c) or (b and d) or (c and d)) to 0x8F1BBCDC.toInt()
                else -> (b xor c xor d) to 0xCA62C1D6.toInt()
            }

            val temp = a.rotateLeft(5) + f + e + k + words[index]
            e = d
            d = c
            c = b.rotateLeft(30)
            b = a
            a = temp
        }

        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
    }

    return listOf(h0, h1, h2, h3, h4).joinToString(separator = "") { word ->
        word.toUInt().toString(16).padStart(8, '0')
    }
}

internal expect fun currentTimeMillis(): Long
