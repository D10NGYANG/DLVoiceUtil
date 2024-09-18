package com.d10ng.voice.demo

import com.d10ng.app.resource.getCachePath
import java.io.File

/**
 * 获取缓存目录路径
 * @return String
 */
actual fun getCacheDir(): String {
    return getCachePath()
}

/**
 * 给文件添加内容
 * @param path String
 * @param data ByteArray
 */
actual fun writeFileAppend(path: String, data: ByteArray) {
    // 判断文件是否存在
    val file = File(path)
    if (file.exists()) {
        file.appendBytes(data)
    } else {
        file.writeBytes(data)
    }
}

/**
 * 删除文件
 * @param path String
 */
actual fun deleteFile(path: String) {
    File(path).deleteOnExit()
}