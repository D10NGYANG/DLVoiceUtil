package com.d10ng.voice.demo

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * 获取缓存目录路径
 * @return String
 */
@OptIn(ExperimentalForeignApi::class)
actual fun getCacheDir(): String {
    val fileManager = NSFileManager.defaultManager()
    val cachesDirectory =
        fileManager.URLForDirectory(NSCachesDirectory, NSUserDomainMask, null, true, null)
    return cachesDirectory?.path ?: ""
}

/**
 * 给文件添加内容
 * @param path String
 * @param data ByteArray
 */
actual fun writeFileAppend(path: String, data: ByteArray) {
    // 判断文件是否存在
    val fileManager = NSFileManager.defaultManager()
}

/**
 * 删除文件
 * @param path String
 */
@OptIn(ExperimentalForeignApi::class)
actual fun deleteFile(path: String) {
    NSFileManager.defaultManager().removeItemAtPath(path, null)
}