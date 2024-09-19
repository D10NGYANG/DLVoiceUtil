package com.d10ng.voice

import com.d10ng.common.base.toByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * PCM音频录音器
 * @Author d10ng
 * @Date 2024/9/19 09:23
 */
abstract class PCMVoiceRecorder(
    // 采样率
    private val sampleRate: Int
) {

    // 录音状态
    private val recordingFlow = MutableStateFlow(false)

    // 录音时长，单位为毫秒
    private val recordTimeFlow = MutableStateFlow(0L)
    private val recordTimeTextFlow = recordTimeFlow.map { it / 1000 }
        .distinctUntilChanged().map { secondTime2Text(it) }

    // 录音音量
    private val recordVolumeFlow = MutableSharedFlow<Float>()

    fun getRecordStatusFlow() = recordingFlow.asStateFlow()
    fun getRecordTimeFlow() = recordTimeFlow.asStateFlow()
    fun getRecordTimeTextFlow() = recordTimeTextFlow
    fun getRecordVolumeUpdateEvent() = recordVolumeFlow.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    // 录音任务
    private var recordJob: Job? = null

    // 录音数据
    private var data = byteArrayOf()

    /**
     * 开始录音
     */
    fun start() {
        if (recordJob != null) throw Exception("already recording")
        recordJob = scope.launch {
            recordingFlow.value = true
            recordTimeFlow.value = 0
            data = byteArrayOf()
            startRecord().collect {
                // 计算音量百分比
                var total = 0.0
                for (element in it) {
                    total += abs(element.toDouble())
                }
                val volume = (abs(total / it.size) / Short.MAX_VALUE * 5).toFloat()
                    .coerceAtMost(100f)
                recordVolumeFlow.emit(volume)
                // 计算录音时长
                recordTimeFlow.value += it.size / (sampleRate / 1000)
                println("录音时长变化：${recordTimeFlow.value}")
                // 添加录音数据
                data += it.toByteArray()
            }
        }
    }

    /**
     * 停止录音
     */
    fun stop(): ByteArray {
        recordJob?.cancel()
        recordJob = null
        recordingFlow.value = false
        return data
    }

    /**
     * 开始录音
     * @return Flow<ShortArray>
     */
    internal abstract fun startRecord(): Flow<ShortArray>
}

/**
 * 创建PCM录音器
 * @param sampleRate Int
 * @return PCMVoiceRecorder
 */
expect fun createPCMVoiceRecorder(sampleRate: Int): PCMVoiceRecorder