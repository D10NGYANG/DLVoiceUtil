package com.d10ng.voice

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ShortVar
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioInputNode
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.setActive

/**
 * 创建PCM录音器
 * @param sampleRate Int
 * @return PCMVoiceRecorder
 */
actual fun createPCMVoiceRecorder(sampleRate: Int): PCMVoiceRecorder {
    return PCMVoiceRecorderIOS(sampleRate)
}

/**
 * PCM录音器
 * @property sampleRate Int
 * @constructor
 */
class PCMVoiceRecorderIOS(
    private val sampleRate: Int
) : PCMVoiceRecorder(sampleRate) {

    private var audioEngine: AVAudioEngine? = null
    private var inputNode: AVAudioInputNode? = null

    @OptIn(ExperimentalForeignApi::class)
    override fun startRecord(): Flow<ShortArray> {
        if (audioEngine != null) throw Exception("Already recording")
        val bufferSizeInBytes = sampleRate * 2 * 1 / 60
        val audioSession = AVAudioSession.sharedInstance()
        audioSession.setCategory(
            AVAudioSessionCategoryPlayAndRecord,
            AVAudioSessionModeDefault,
            0u,
            null
        )
        audioSession.setActive(true, null)
        audioEngine = AVAudioEngine()
        inputNode = audioEngine?.inputNode
        val format = AVAudioFormat(AVAudioPCMFormatInt16, sampleRate.toDouble(), 1u, false)
        val flow = callbackFlow {
            inputNode?.installTapOnBus(0u, bufferSizeInBytes.toUInt(), format) { buffer, _ ->
                buffer ?: return@installTapOnBus
                val channelData = buffer.int16ChannelData
                val frameCount = buffer.frameLength.toInt()
                val pcmBuffer = ShortArray(frameCount)
                channelData?.let { data ->
                    val samples = data[0]?.reinterpret<ShortVar>()
                    samples?.let {
                        for (i in 0 until frameCount) {
                            pcmBuffer[i] = it[i]
                        }
                    }
                }
                trySend(pcmBuffer)
            }
            awaitClose {
                inputNode?.removeTapOnBus(0u)
                audioEngine?.stop()
                audioEngine = null
                inputNode = null
            }
        }
        audioEngine?.prepare()
        audioEngine?.startAndReturnError(null)
        return flow
    }
}