package com.d10ng.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * PCM录音器
 */
class PCMRecorder(
    private val audioSource: Int = MediaRecorder.AudioSource.MIC,
    private val sampleRateInHz: Int = 48000,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    private val bufferSizeInBytes: Int = 1024,
    // 计时频率，单位为毫秒
    private val timingFrequency: Long = 100L
) {
    private var audioRecorder: AudioRecord? = null

    // 录音状态
    private val isRecordingFlow = MutableStateFlow(false)
    // 录音时长，单位为毫秒
    private val recordTimeFlow = MutableStateFlow(0L)
    private val recordTimeTextFlow = recordTimeFlow.map { secondTime2Text(it / 1000) }
    // 录音音量
    private val recordVolumeFlow = MutableSharedFlow<Int>()

    fun getRecordStatusFlow() = isRecordingFlow.asStateFlow()
    fun getRecordTimeFlow() = recordTimeFlow.asStateFlow()
    fun getRecordTimeTextFlow() = recordTimeTextFlow
    fun getRecordVolumeUpdateEvent() = recordVolumeFlow.asSharedFlow()

    private var recordThread: Thread? = null
    private var recordTimer: Timer? = null
    private var bos: ByteArrayOutputStream? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    var data: ByteArray? = null
        private set

    /**
     * 开始录音
     */
    @Synchronized
    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (audioRecorder != null) return
        isRecordingFlow.value = true
        recordTimeFlow.value = 0
        audioRecorder =
            AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes)
        // 噪音抑制
        if (NoiseSuppressor.isAvailable()) {
            println("噪音抑制可用")
            noiseSuppressor = NoiseSuppressor.create(audioRecorder!!.audioSessionId)
            noiseSuppressor?.enabled = true
        }

        recordThread = Thread {
            val scope = CoroutineScope(Dispatchers.IO)
            audioRecorder!!.startRecording()
            bos = ByteArrayOutputStream()
            val buffer = ShortArray(bufferSizeInBytes)
            while (audioRecorder != null) {
                val readSize = audioRecorder!!.read(buffer, 0, bufferSizeInBytes)
                if (readSize > 0) {
                    var total = 0.0
                    for (i in 0 until readSize) {
                        total += abs(buffer[i].toDouble())
                    }
                    val per = total / readSize
                    val volume = if(per == 0.0) 0 else min(per.roundToInt() / 20, 100)
                    scope.launch { recordVolumeFlow.emit(volume) }
                    bos?.write(buffer.toByteArray())
                }
            }
        }
        recordThread?.start()

        recordTimer = Timer().apply {
            schedule(timingFrequency, timingFrequency) {
                // 录音时长增加
                recordTimeFlow.value += timingFrequency
            }
        }
    }

    /**
     * 停止录音
     * @return ByteArray 录音数据
     */
    @Synchronized
    fun stop(): ByteArray {
        audioRecorder?.stop()
        audioRecorder?.release()
        audioRecorder = null
        noiseSuppressor?.release()
        recordThread?.interrupt()
        recordThread = null
        recordTimer?.cancel()
        recordTimer = null
        val data = bos?.toByteArray()
        bos?.close()
        bos = null
        isRecordingFlow.value = false
        this.data = data
        return data?: byteArrayOf()
    }
}