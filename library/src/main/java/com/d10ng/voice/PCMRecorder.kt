package com.d10ng.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.concurrent.schedule

/**
 * PCM录音器
 */
class PCMRecorder(
    private val audioSource: Int = MediaRecorder.AudioSource.MIC,
    private val sampleRateInHz: Int = 8000,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    private val bufferSizeInBytes: Int = 1024
) {
    private var audioRecorder: AudioRecord? = null

    // 录音状态
    private val isRecordingFlow = MutableStateFlow(false)
    // 录音时长
    private val recordTimeFlow = MutableStateFlow(0)
    private val recordTimeTextFlow = recordTimeFlow.map { secondTime2Text(it) }
    // 录音音量
    private val recordVolumeFlow = MutableStateFlow(0)

    fun getRecordStatusFlow() = isRecordingFlow.asStateFlow()
    fun getRecordTimeFlow() = recordTimeFlow.asStateFlow()
    fun getRecordTimeTextFlow() = recordTimeTextFlow
    fun getRecordVolumeFlow() = recordVolumeFlow.asStateFlow()

    private var recordThread: Thread? = null
    private var recordTimer: Timer? = null
    private var bos: ByteArrayOutputStream? = null
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
        audioRecorder = AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes)

        recordThread = Thread {
            audioRecorder!!.startRecording()
            bos = ByteArrayOutputStream()
            val buffer = ShortArray(bufferSizeInBytes)
            while (audioRecorder != null) {
                val readSize = audioRecorder!!.read(buffer, 0, bufferSizeInBytes)
                if (readSize > 0) {
                    var volume = 0
                    for (i in 0 until readSize) {
                        val a = buffer[i].toInt()
                        if (a > volume || -a > volume)
                            volume = a
                    }
                    recordVolumeFlow.value = volume
                    bos?.write(short2byte(buffer))
                }
            }
        }
        recordThread?.start()

        recordTimer = Timer().apply {
            schedule(0L, 1000L) {
                // 录音时长 +1
                recordTimeFlow.value += 1
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
        audioRecorder = null
        recordThread?.interrupt()
        recordThread = null
        recordTimer?.cancel()
        recordTimer = null
        val data = bos!!.toByteArray()
        bos?.close()
        bos = null
        isRecordingFlow.value = false
        this.data = data
        return data
    }
}