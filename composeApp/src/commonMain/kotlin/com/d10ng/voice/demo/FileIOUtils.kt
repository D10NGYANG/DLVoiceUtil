package com.d10ng.voice.demo

/**
 * 文件工具
 * @Author d10ng
 * @Date 2024/9/18 17:05
 */
/**
 * 获取缓存目录路径
 * @return String
 */
expect fun getCacheDir(): String

/**
 * 删除文件
 * @param path String
 */
expect fun deleteFile(path: String)

/**
 * 给文件添加内容
 * @param path String
 * @param data ByteArray
 */
expect fun writeFileAppend(path: String, data: ByteArray)