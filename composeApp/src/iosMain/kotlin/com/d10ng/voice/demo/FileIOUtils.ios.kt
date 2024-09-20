package com.d10ng.voice.demo

import com.d10ng.common.transform.toByteArray
import com.d10ng.common.transform.toNSData
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.closeFile
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.fileHandleForUpdatingAtPath
import platform.Foundation.seekToEndOfFile
import platform.Foundation.writeData
import platform.Foundation.writeToFile

/**
 * 获取缓存文件存放路径
 * @param fileName String
 * @return String
 */
@OptIn(ExperimentalForeignApi::class)
actual fun getCacheFilePath(fileName: String): String {
    val fileManager = NSFileManager.defaultManager()
    val cachesDirectory =
        fileManager.URLForDirectory(NSCachesDirectory, NSUserDomainMask, null, true, null)
    val fileURL = cachesDirectory?.URLByAppendingPathComponent(fileName)
    return fileURL?.path ?: ""
}

/**
 * 给文件添加内容
 * @param path String
 * @param data ByteArray
 */
actual fun writeFileAppend(path: String, data: ByteArray) {
    // 判断文件是否存在
    val fileManager = NSFileManager.defaultManager()
    val nsData = data.toNSData()

    if (fileManager.fileExistsAtPath(path)) {
        // 文件存在，追加内容
        NSFileHandle.fileHandleForUpdatingAtPath(path)?.let {
            it.seekToEndOfFile()
            it.writeData(nsData)
            it.closeFile()
        }
    } else {
        // 文件不存在，创建新文件并写入内容
        nsData.writeToFile(path, true)
    }
}

/**
 * 删除文件
 * @param path String
 */
@OptIn(ExperimentalForeignApi::class)
actual fun deleteFile(path: String) {
    NSFileManager.defaultManager().removeItemAtPath(path, null)
}

/**
 * 读取文件字节数据
 * @param path String
 * @return ByteArray
 */
actual fun readFile(path: String): ByteArray {
    val data = NSData.dataWithContentsOfFile(path) ?: return byteArrayOf()
    return data.toByteArray()
}

/**
 * 写入文件
 * @param path String
 * @param data ByteArray
 */
actual fun writeFile(path: String, data: ByteArray) {
    val nsData = data.toNSData()
    nsData.writeToFile(path, true)
}