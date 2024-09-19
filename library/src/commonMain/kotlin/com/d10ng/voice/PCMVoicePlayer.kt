package com.d10ng.voice

/**
 * PCM音频播放器
 * @Author d10ng
 * @Date 2024/9/19 10:06
 */
abstract class PCMVoicePlayer {

    /**
     * 开始播放
     * @param data ByteArray 音频数据
     * @param sampleRate Int 采样率
     */
    abstract fun start(data: ByteArray, sampleRate: Int)

    /**
     * 停止播放
     */
    abstract fun stop()
}

/**
 * 创建PCM播放器
 * @return PCMVoicePlayer
 */
expect fun createPCMVoicePlayer(): PCMVoicePlayer