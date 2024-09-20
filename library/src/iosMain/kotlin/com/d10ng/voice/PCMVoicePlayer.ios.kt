package com.d10ng.voice

import com.d10ng.common.transform.toNSData
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.setActive
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * 创建PCM播放器
 * @return PCMVoicePlayer
 */
actual fun createPCMVoicePlayer(): PCMVoicePlayer {
    return PCMVoicePlayerIOS()
}

class PCMVoicePlayerIOS : PCMVoicePlayer() {

    private var player: AVAudioPlayer? = null

    private val playerDelegate = object : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            println("播放结束")
            stopPlay()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun startPlay(data: ByteArray, sampleRate: Int) {
        if (player != null) throw Exception("Already playing")
        val wavData = pcmToWav(data).toNSData()
        AVAudioSession.sharedInstance().apply {
            setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeDefault, 0u, null)
            setActive(true, null)
        }
        player = AVAudioPlayer(wavData, null).apply {
            delegate = playerDelegate
            prepareToPlay()
            play()
        }
    }

    override fun stopPlay() {
        player?.stop()
        player = null
    }

    private fun pcmToWav(pcmData: ByteArray): ByteArray {
        val wavHeader = createWavHeader(pcmData.size)
        return wavHeader + pcmData
    }

    private fun createWavHeader(pcmDataSize: Int): ByteArray {
        val wavDataSize = pcmDataSize + 36
        return byteArrayOf(
            0x52, 0x49, 0x46, 0x46, // "RIFF"
            (wavDataSize and 0xff).toByte(),
            ((wavDataSize shr 8) and 0xff).toByte(),
            ((wavDataSize shr 16) and 0xff).toByte(),
            ((wavDataSize shr 24) and 0xff).toByte(), // WAV Chunk Size
            0x57, 0x41, 0x56, 0x45, // "WAVE"
            0x66, 0x6d, 0x74, 0x20, // "fmt "
            16, 0, 0, 0, // Subchunk1Size (16 for PCM)
            1, 0, // AudioFormat (1 for PCM)
            1, 0, // NumChannels (1 for mono)
            0x80.toByte(), 0xbb.toByte(), 0, 0, // SampleRate (48000)
            0x00, 0x77, 0x01, 0, // ByteRate (SampleRate * NumChannels * BitsPerSample/8)
            2, 0, // BlockAlign (NumChannels * BitsPerSample/8)
            16, 0, // BitsPerSample (16 bits)
            0x64, 0x61, 0x74, 0x61, // "data"
            (pcmDataSize and 0xff).toByte(),
            ((pcmDataSize shr 8) and 0xff).toByte(),
            ((pcmDataSize shr 16) and 0xff).toByte(),
            ((pcmDataSize shr 24) and 0xff).toByte() // Subchunk2Size
        )
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