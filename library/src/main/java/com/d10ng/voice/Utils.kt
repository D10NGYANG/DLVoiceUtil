package com.d10ng.voice

/**
 * 将ShortArray转换为ByteArray
 * @param sData ShortArray
 * @return ByteArray
 */
internal fun short2byte(sData: ShortArray): ByteArray {
    val shortSize = sData.size
    val bytes = ByteArray(shortSize * 2)
    for (i in 0 until shortSize) {
        val item = sData[i].toUShort()
        bytes[i * 2] = (item and 255u).toByte()
        bytes[i * 2 + 1] = ((item and 0xFF00.toUShort()).toUInt() shr 8).toByte()
    }
    return bytes
}

/**
 * 将秒数转换为时分秒格式，如：00:00:00
 * @param time Int
 * @return String
 */
internal fun secondTime2Text(time: Int): String {
    val hour = time / 3600
    val minute = time / 60 % 60
    val second = time % 60
    return String.format("%02d:%02d:%02d", hour, minute, second)
}