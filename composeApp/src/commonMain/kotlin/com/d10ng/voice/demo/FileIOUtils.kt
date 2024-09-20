package com.d10ng.voice.demo

/**
 * 文件工具
 * @Author d10ng
 * @Date 2024/9/18 17:05
 */
/**
 * 获取缓存文件存放路径
 * @param fileName String
 * @return String
 */
expect fun getCacheFilePath(fileName: String): String

/**
 * 删除文件
 * @param path String
 */
expect fun deleteFile(path: String)

/**
 * 写入文件
 * @param path String
 * @param data ByteArray
 */
expect fun writeFile(path: String, data: ByteArray)

/**
 * 给文件添加内容
 * @param path String
 * @param data ByteArray
 */
expect fun writeFileAppend(path: String, data: ByteArray)

/**
 * 读取文件字节数据
 * @param path String
 * @return ByteArray
 */
expect fun readFile(path: String): ByteArray