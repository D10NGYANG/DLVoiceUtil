package com.d10ng.voice

/**
 * 时间工具类
 * @Author d10ng
 * @Date 2024/9/19 15:52
 */

/**
 * 获取当前时间戳毫秒
 * @return Long
 */
expect fun getCurrentTimestamp(): Long

/**
 * 将秒转换成倒计时字符串，如00:00:01
 * @receiver Int
 * @return String
 */
internal fun secondTime2Text(time: Long): String {
    if (time <= 0) {
        return "00:00:00"
    }
    val hour = time / (60 * 60)
    val minute = (time - hour * 60 * 60) / 60
    val second = time - hour * 60 * 60 - minute * 60
    val builder = StringBuilder()
        .append(hour.toString().padStartForce(2, '0')).append(":")
        .append(minute.toString().padStartForce(2, '0')).append(":")
        .append(second.toString().padStartForce(2, '0'))
    return builder.toString()
}

internal fun String.padStartForce(length: Int, padChar: Char = '0'): String {
    return this.padStart(length, padChar).substring(0, length)
}