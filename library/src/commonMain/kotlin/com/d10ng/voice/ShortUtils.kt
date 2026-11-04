package com.d10ng.voice

import kotlin.math.abs

internal fun ByteArray.toShort(): Short =
    ((this[0].toInt() and 0xff) or ((this[1].toInt() and 0xff) shl 8)).toShort()

internal fun pcmDurationMillis(byteCount: Int, sampleRate: Int): Long =
    byteCount.toLong() / 2 * 1000 / sampleRate

internal fun pcmVolume(data: ByteArray, start: Int, end: Int): Float {
    if (end <= start) return 0f
    var total = 0.0
    for (i in start until end step 2) {
        val value = ((data[i].toInt() and 0xff) or ((data[i + 1].toInt() and 0xff) shl 8)).toShort()
        total += abs(value.toInt())
    }
    return (total / ((end - start) / 2) / Short.MAX_VALUE * 5).toFloat().coerceIn(0f, 1f)
}
