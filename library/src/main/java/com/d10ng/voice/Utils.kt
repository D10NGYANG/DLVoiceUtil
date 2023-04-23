package com.d10ng.voice

/**
 * 将ShortArray转换为ByteArray
 * @receiver ShortArray
 * @return ByteArray
 */
internal fun ShortArray.toByteArray(): ByteArray {
    val shortSize = this.size
    val bytes = ByteArray(shortSize * 2)
    for (i in 0 until shortSize) {
        val item = this[i].toByteArray()
        bytes[i * 2] = item[0]
        bytes[i * 2 + 1] = item[1]
    }
    return bytes
}

/**
 * 将Short转换为ByteArray
 * @receiver Short
 * @return ByteArray
 */
internal fun Short.toByteArray(): ByteArray {
    val bytes = ByteArray(2)
    val item = this.toUShort()
    bytes[0] = (item and 255u).toByte()
    bytes[1] = ((item and 0xFF00.toUShort()).toUInt() shr 8).toByte()
    return bytes
}

/**
 * 将ByteArray转换为Short
 * @receiver ByteArray
 * @return Short
 */
internal fun ByteArray.toShort(): Short {
    return (this[0].toUInt() or (this[1].toUInt() shl 8)).toShort()
}

/**
 * 将秒数转换为时分秒格式，如：00:00:00
 * @param time Long
 * @return String
 */
internal fun secondTime2Text(time: Long): String {
    val hour = time / 3600
    val minute = time / 60 % 60
    val second = time % 60
    return String.format("%02d:%02d:%02d", hour, minute, second)
}