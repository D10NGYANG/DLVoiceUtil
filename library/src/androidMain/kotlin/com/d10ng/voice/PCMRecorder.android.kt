package com.d10ng.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 创建PCM录音器
 * @param sampleRate Int
 * @return IPCMRecorder
 */
actual fun createPCMRecorder(sampleRate: Int): PCMRecorder {
    return PCMRecorderAndroid(sampleRate)
}

/**
 * PCM录音器
 * @property sampleRate Int
 * @constructor
 */
class PCMRecorderAndroid(
    private val sampleRate: Int
) : PCMRecorder(sampleRate) {

    private var audioRecorder: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    override fun start(): Flow<ShortArray> {
        if (audioRecorder != null) throw Exception("Already recording")
        // MediaRecorder.AudioSource.MIC：指定音频源为设备的麦克风
        // AudioFormat.CHANNEL_IN_MONO：指定单声道
        // AudioFormat.ENCODING_PCM_16BIT：指定采样位数为16位
        // 每秒数据量为 声道数 * 采样频率 * 采样位数 / 8，那么在60帧的情况下，每帧数据量为每秒数据量的1/60
        val bufferSizeInBytes = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate * 2 * 1 / 60)
        audioRecorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSizeInBytes
        )
        // 噪音抑制
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(audioRecorder!!.audioSessionId)
            noiseSuppressor?.enabled = true
        }
        return flow {
            audioRecorder?.startRecording()
            val buffer = ShortArray(bufferSizeInBytes / 2)
            runCatching {
                while (audioRecorder != null && audioRecorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val readSize = audioRecorder?.read(buffer, 0, bufferSizeInBytes / 2) ?: 0
                    if (readSize > 0) emit(buffer)
                }
            }
        }
    }

    override fun stop() {
        audioRecorder?.run {
            stop()
            release()
        }
        audioRecorder = null
        noiseSuppressor?.run {
            release()
        }
        noiseSuppressor = null
    }
}