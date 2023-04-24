package com.d10ng.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Timer
import kotlin.concurrent.schedule
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * PCM播放器
 */
class PCMPlayer(
    private val encoding: Int = AudioFormat.ENCODING_PCM_16BIT,
    private val sampleRateInHz: Int = 8000,
    private val channelConfig: Int = AudioFormat.CHANNEL_OUT_MONO,
    private val transferMode: Int = AudioTrack.MODE_STATIC,
    // 计时频率，单位为毫秒
    private val timingFrequency: Long = 100L
) {

    private var audioTrack: AudioTrack? = null

    // 播放状态
    private val isPlayingFlow = MutableStateFlow(false)
    // 播放时间，单位为毫秒
    private val playTimeFlow = MutableStateFlow(0L)
    private val playTimeTextFlow = playTimeFlow.map { secondTime2Text(it / 1000) }

    // 播放音量
    private val playVolumeFlow = MutableSharedFlow<Int>()

    fun getPlayStatusFlow() = isPlayingFlow.asStateFlow()
    fun getPlayTimeFlow() = playTimeFlow.asStateFlow()
    fun getPlayTimeTextFlow() = playTimeTextFlow
    fun getPlayVolumeUpdateEvent() = playVolumeFlow.asSharedFlow()

    private var playTimer: Timer? = null

    // 计算播放音量定时器
    private var volumeTimer: Timer? = null

    /**
     * 开始播放
     * @param data ByteArray
     */
    @Synchronized
    fun start(data: ByteArray) {
        if (audioTrack != null) return
        isPlayingFlow.value = true
        playTimeFlow.value = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(sampleRateInHz)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setBufferSizeInBytes(data.size)
                .setTransferMode(transferMode)
                .build()
        } else {
            audioTrack = AudioTrack(
                android.media.AudioManager.STREAM_MUSIC,
                sampleRateInHz,
                channelConfig,
                encoding,
                data.size,
                transferMode
            )
        }
        audioTrack?.write(data, 0, data.size)
        audioTrack?.play()
        val duration = data.size / 16
        // 监听设备是否播放完毕
        volumeTimer = Timer().apply {
            // 通过PCM音频字节数据计算音频长度，单位为毫秒
            val startTime = System.currentTimeMillis()
            var lastIndex = 0
            var offsetTime = 0L
            schedule(0, 128) {
                if (audioTrack == null || offsetTime >= duration) {
                    CoroutineScope(Dispatchers.IO).launch {
                        if (isPlayingFlow.value) isPlayingFlow.emit(false)
                    }
                    cancel()
                    return@schedule
                }
                val volume = if (offsetTime == 0L) 0
                else {
                    // 拿到当前时间的字节数据
                    val index = minOf(offsetTime * 16, data.size.toLong())
                    val bytes = data.copyOfRange(lastIndex, index.toInt())
                    lastIndex = index.toInt()
                    var total = 0.0
                    for (i in bytes.indices step 2) {
                        val arr = byteArrayOf(bytes[i], bytes[i + 1])
                        total += abs(arr.toShort().toDouble())
                    }
                    val per = total / bytes.size * 2
                    if (per == 0.0) 0 else min(per.roundToInt() / 20, 100)
                }
                CoroutineScope(Dispatchers.IO).launch { playVolumeFlow.emit(volume) }
                offsetTime = System.currentTimeMillis() - startTime
            }
        }

        playTimer = Timer().apply {
            schedule(timingFrequency, timingFrequency) {
                // 播放时长增加
                playTimeFlow.value += timingFrequency
                if (playTimeFlow.value >= duration) cancel()
            }
        }
    }

    /**
     * 停止播放
     */
    @Synchronized
    fun stop() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        playTimer?.cancel()
        playTimer = null
        volumeTimer?.cancel()
        volumeTimer = null
        isPlayingFlow.value = false
    }
}