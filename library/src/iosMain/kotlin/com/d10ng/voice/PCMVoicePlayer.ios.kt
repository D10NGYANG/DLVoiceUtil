package com.d10ng.voice

import com.d10ng.common.transform.toNSData
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.AVFAudio.AVAudioPlayerNode
import platform.posix.memcpy

/**
 * 创建PCM播放器
 * @return PCMVoicePlayer
 */
actual fun createPCMVoicePlayer(): PCMVoicePlayer {
    return PCMVoicePlayerIOS()
}

class PCMVoicePlayerIOS : PCMVoicePlayer() {
    @OptIn(ExperimentalForeignApi::class)
    override fun start(data: ByteArray, sampleRate: Int) {
        val audioEngine = AVAudioEngine()
        val playerNode = AVAudioPlayerNode()
        audioEngine.attachNode(playerNode)
        audioEngine.connect(playerNode, to = audioEngine.mainMixerNode, format = null)
        audioEngine.startAndReturnError(null)

        val format = AVAudioFormat(AVAudioPCMFormatInt16, sampleRate.toDouble(), 1u, true)
        val frameCapacity = (data.size / 2).toUInt()
        val buffer = AVAudioPCMBuffer(format, frameCapacity = frameCapacity)
        buffer.frameLength = frameCapacity

        val nsData = data.toNSData()
        nsData.bytes?.let { dataPtr ->
            buffer.int16ChannelData?.get(0)?.let { bufferPtr ->
                memcpy(bufferPtr, dataPtr, data.size.toULong())
            }
        }

        playerNode.scheduleBuffer(buffer) {
            println("Finished playing PCM data")
        }
        playerNode.play()
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun ByteArray.toAVAudioPCMBuffer(format: AVAudioFormat): AVAudioPCMBuffer {
        val frameLength = (this.size / 2).toUInt() // 16-bit samples
        val pcmBuffer = AVAudioPCMBuffer(format, frameLength)
        pcmBuffer.let { buffer ->
            buffer.frameLength = frameLength

            val audioBufferList = buffer.audioBufferList
            val audioBufferData = audioBufferList!![0].mBuffers[0].mData!!.reinterpret<ByteVar>()

            this.usePinned { pinnedData ->
                memcpy(audioBufferData, pinnedData.addressOf(0), this.size.toULong())
            }
        }

        return pcmBuffer
    }
}