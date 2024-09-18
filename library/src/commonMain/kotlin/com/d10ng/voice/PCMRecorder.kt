package com.d10ng.voice

import kotlinx.coroutines.flow.Flow

/**
 * PCM录音器
 * @Author d10ng
 * @Date 2024/9/18 14:21
 */
abstract class PCMRecorder(
    private val sampleRate: Int
) {

    /**
     * 开始录音
     * @return Flow<ShortArray>
     */
    abstract fun start(): Flow<ShortArray>

    /**
     * 结束录音
     */
    abstract fun stop()
}

/**
 * 创建PCM录音器
 * @param sampleRate Int
 * @return IPCMRecorder
 */
expect fun createPCMRecorder(sampleRate: Int): PCMRecorder

