package com.d10ng.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * PCM音频播放器
 * @Author d10ng
 * @Date 2024/9/19 10:06
 */
abstract class PCMVoicePlayer {

    // 播放状态
    private val playingFlow = MutableStateFlow(false)

    // 播放时间，单位为毫秒
    private val playTimeFlow = MutableStateFlow(0L)
    private val playTimeTextFlow =
        playTimeFlow.map { it / 1000 }.distinctUntilChanged().map { secondTime2Text(it) }

    // 播放音量
    private val playVolumeFlow = MutableSharedFlow<Float>()

    fun getPlayStatusFlow() = playingFlow.asStateFlow()
    fun getPlayTimeFlow() = playTimeFlow.asStateFlow()
    fun getPlayTimeTextFlow() = playTimeTextFlow
    fun getPlayVolumeUpdateEvent() = playVolumeFlow.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var updateVolumeJob: Job? = null

    fun start(data: ByteArray, sampleRate: Int) {
        startPlay(data, sampleRate)
        playingFlow.value = true
        playTimeFlow.value = 0
        // 每毫秒字节数
        val perByte = sampleRate * 2 / 1000
        // 通过PCM音频字节数据计算音频长度，单位为毫秒
        val duration = data.size / perByte
        // 更新音量任务
        updateVolumeJob = scope.launch {
            val startTime = getCurrentTimestamp()
            var lastIndex = 0
            var offsetTime = 0L
            while (true) {
                if (offsetTime >= duration) {
                    stop()
                    return@launch
                }
                val volume = if (offsetTime == 0L) 0f
                else {
                    // 拿到当前时间的字节数据
                    var index = minOf(offsetTime.toInt() * perByte, data.size)
                    index = if (index % 2 == 1) index - 1 else index
                    val bytes = data.copyOfRange(lastIndex, index)
                    lastIndex = index
                    var total = 0.0
                    for (i in bytes.indices step 2) {
                        val arr = byteArrayOf(bytes[i], bytes[i + 1])
                        total += abs(arr.toShort().toDouble())
                    }
                    (abs(total / (bytes.size / 2)) / Short.MAX_VALUE * 5).toFloat()
                        .coerceAtMost(100f)
                }
                playVolumeFlow.emit(volume)
                offsetTime = getCurrentTimestamp() - startTime
                playTimeFlow.value = offsetTime
                delay(16)
            }
        }
    }

    fun stop() {
        stopPlay()
        updateVolumeJob?.cancel()
        updateVolumeJob = null
        playingFlow.value = false
    }

    /**
     * 开始播放
     * @param data ByteArray 音频数据
     * @param sampleRate Int 采样率
     */
    internal abstract fun startPlay(data: ByteArray, sampleRate: Int)

    /**
     * 停止播放
     */
    internal abstract fun stopPlay()
}

/**
 * 创建PCM播放器
 * @return PCMVoicePlayer
 */
expect fun createPCMVoicePlayer(): PCMVoicePlayer