package com.d10ng.voice

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/**
 * 获取当前时间戳毫秒
 * @return Long
 */
actual fun getCurrentTimestamp(): Long {
    return (NSDate().timeIntervalSince1970 * 1000).toLong()
}