package com.d10ng.voice

/**
 * Short工具
 * @Author d10ng
 * @Date 2024/9/19 17:02
 */

/**
 * 将ByteArray转换为Short
 * @receiver ByteArray
 * @return Short
 */
internal fun ByteArray.toShort(): Short {
    return (this[0].toUInt() or (this[1].toUInt() shl 8)).toShort()
}