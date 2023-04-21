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
    private val transferMode: Int = AudioTrack.MODE_STATIC
) {

    private var audioTrack: AudioTrack? = null

    // 播放状态
    private val isPlayingFlow = MutableStateFlow(false)
    // 播放时间
    private val playTimeFlow = MutableStateFlow(0)
    private val playTimeTextFlow = playTimeFlow.map { secondTime2Text(it) }
    // 播放音量
    private val playVolumeFlow = MutableSharedFlow<Int>()

    fun getPlayStatusFlow() = isPlayingFlow.asStateFlow()
    fun getPlayTimeFlow() = playTimeFlow.asStateFlow()
    fun getPlayTimeTextFlow() = playTimeTextFlow
    fun getPlayVolumeUpdateEvent() = playVolumeFlow.asSharedFlow()

    private var playTimer: Timer? = null

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
        // 监听设备是否播放完毕
        Thread {
            val scope = CoroutineScope(Dispatchers.IO)
            // 通过PCM音频字节数据计算音频长度，单位为毫秒
            val duration = data.size / 16
            val startTime = System.currentTimeMillis()
            var lastIndex = 0
            var offsetTime = 0L
            while (audioTrack != null && offsetTime < duration) {
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
                    if(per == 0.0) 0 else min(per.roundToInt() / 20, 100)
                }
                scope.launch { playVolumeFlow.emit(volume) }
                Thread.sleep(128)
                offsetTime = System.currentTimeMillis() - startTime
            }
            stop()
        }.start()

        playTimer = Timer().apply {
            schedule(0L, 1000L) {
                // 播放时长 +1
                playTimeFlow.value += 1
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
        playTimer?.cancel()
        playTimer = null
        audioTrack = null
        isPlayingFlow.value = false
    }
}