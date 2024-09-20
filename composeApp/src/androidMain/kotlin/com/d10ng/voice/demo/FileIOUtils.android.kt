package com.d10ng.voice.demo

import com.d10ng.app.resource.getCachePath
import java.io.File

/**
 * 获取缓存文件存放路径
 * @param fileName String
 * @return String
 */
actual fun getCacheFilePath(fileName: String): String {
    return "${getCachePath()}/${fileName}"
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

/**
 * 读取文件字节数据
 * @param path String
 * @return ByteArray
 */
actual fun readFile(path: String): ByteArray {
    return File(path).readBytes()
}

/**
 * 写入文件
 * @param path String
 * @param data ByteArray
 */
actual fun writeFile(path: String, data: ByteArray) {
    File(path).writeBytes(data)
}