package com.d10ng.voice

import kotlinx.coroutines.flow.Flow

/**
 * PCM音频录音器
 * @Author d10ng
 * @Date 2024/9/19 09:23
 */
abstract class PCMVoiceRecorder(
    // 采样率
    private val sampleRate: Int
) {

    /**
     * 开始录音
     * @return Flow<ShortArray>
     */
    abstract fun start(): Flow<ShortArray>
}

/**
 * 创建PCM录音器
 * @param sampleRate Int
 * @return PCMVoiceRecorder
 */
expect fun createPCMVoiceRecorder(sampleRate: Int): PCMVoiceRecorder