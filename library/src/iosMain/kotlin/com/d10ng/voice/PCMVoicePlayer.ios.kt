package com.d10ng.voice

import com.d10ng.common.base.toShortArray
import com.d10ng.common.transform.toNSData
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ShortVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.setActive
import platform.AudioToolbox.AURenderCallbackStruct
import platform.AudioToolbox.AudioComponentDescription
import platform.AudioToolbox.AudioComponentFindNext
import platform.AudioToolbox.AudioComponentInstance
import platform.AudioToolbox.AudioComponentInstanceDispose
import platform.AudioToolbox.AudioComponentInstanceNew
import platform.AudioToolbox.AudioComponentInstanceVar
import platform.AudioToolbox.AudioOutputUnitStart
import platform.AudioToolbox.AudioOutputUnitStop
import platform.AudioToolbox.AudioUnitInitialize
import platform.AudioToolbox.AudioUnitRenderActionFlagsVar
import platform.AudioToolbox.AudioUnitSetProperty
import platform.AudioToolbox.AudioUnitUninitialize
import platform.AudioToolbox.kAudioOutputUnitProperty_EnableIO
import platform.AudioToolbox.kAudioUnitManufacturer_Apple
import platform.AudioToolbox.kAudioUnitProperty_SetRenderCallback
import platform.AudioToolbox.kAudioUnitProperty_StreamFormat
import platform.AudioToolbox.kAudioUnitScope_Input
import platform.AudioToolbox.kAudioUnitScope_Output
import platform.AudioToolbox.kAudioUnitSubType_RemoteIO
import platform.AudioToolbox.kAudioUnitType_Output
import platform.CoreAudioTypes.AudioBufferList
import platform.CoreAudioTypes.AudioStreamBasicDescription
import platform.CoreAudioTypes.AudioTimeStamp
import platform.CoreAudioTypes.kAudioFormatFlagIsPacked
import platform.CoreAudioTypes.kAudioFormatFlagIsSignedInteger
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.CoreAudioTypes.kAudio_NoError
import platform.darwin.NSObject
import platform.darwin.OSStatus
import platform.darwin.UInt32
import platform.darwin.UInt32Var
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy
import kotlin.experimental.ExperimentalNativeApi

/**
 * 创建PCM播放器
 * @return PCMVoicePlayer
 */
actual fun createPCMVoicePlayer(): PCMVoicePlayer {
    return PCMVoicePlayerIOS1()
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

class PCMVoicePlayerIOS1 : PCMVoicePlayer() {
    @OptIn(ExperimentalForeignApi::class)
    private var audioUnit: AudioComponentInstance? = null
    var audioBuffer: ShortArray = shortArrayOf()
    var currentIndex: Int = 0

    @OptIn(ExperimentalForeignApi::class)
    private var stableRef: StableRef<PCMVoicePlayerIOS1>? = null

    @OptIn(ExperimentalForeignApi::class)
    override fun startPlay(data: ByteArray, sampleRate: Int) {
        if (audioUnit != null) throw Exception("Already playing")
        audioBuffer = data.toShortArray()
        currentIndex = 0
        AVAudioSession.sharedInstance().apply {
            setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeDefault, 0u, null)
            setActive(true, null)
        }
        memScoped {
            val audioComponentDescription = alloc<AudioComponentDescription>().apply {
                componentType = kAudioUnitType_Output
                componentSubType = kAudioUnitSubType_RemoteIO
                componentManufacturer = kAudioUnitManufacturer_Apple
                componentFlags = 0u
                componentFlagsMask = 0u
            }
            val audioComponent = AudioComponentFindNext(null, audioComponentDescription.ptr)
            val audioUnitVar = alloc<AudioComponentInstanceVar>()
            var status = AudioComponentInstanceNew(audioComponent, audioUnitVar.ptr)
            println("status1 = $status")
            audioUnit = audioUnitVar.value
            val one = alloc<UInt32Var>().apply { value = 1u }
            status = AudioUnitSetProperty(
                audioUnit!!,
                kAudioOutputUnitProperty_EnableIO,
                kAudioUnitScope_Output,
                0u,
                one.ptr,
                sizeOf<UInt32Var>().convert()
            )
            println("status2 = $status")
            val asbd = alloc<AudioStreamBasicDescription>().apply {
                mSampleRate = sampleRate.toDouble()
                mFormatID = kAudioFormatLinearPCM
                mFormatFlags = (kAudioFormatFlagIsSignedInteger or kAudioFormatFlagIsPacked)
                mBytesPerPacket = 2u
                mFramesPerPacket = 1u
                mBytesPerFrame = 2u
                mChannelsPerFrame = 1u
                mBitsPerChannel = 16u
                mReserved = 0u
            }
            status = AudioUnitSetProperty(
                audioUnit,
                kAudioUnitProperty_StreamFormat,
                kAudioUnitScope_Input,
                0u,
                asbd.ptr,
                sizeOf<AudioStreamBasicDescription>().convert()
            )
            println("status3 = $status")
            stableRef = StableRef.create(this@PCMVoicePlayerIOS1)
            val selfPtr = stableRef!!.asCPointer()
            val callbackStruct = alloc<AURenderCallbackStruct>().apply {
                inputProc = staticCFunction(::playbackCallback)
                inputProcRefCon = selfPtr
            }
            status = AudioUnitSetProperty(
                audioUnit,
                kAudioUnitProperty_SetRenderCallback,
                kAudioUnitScope_Input,
                0u,
                callbackStruct.ptr,
                sizeOf<AURenderCallbackStruct>().convert()
            )
            println("status4 = $status")
            status = AudioUnitInitialize(audioUnit)
            println("status5 = $status")
            status = AudioOutputUnitStart(audioUnit)
            println("status6 = $status")
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun stopPlay() {
        memScoped {
            if (audioUnit == null) return

            runCatching {
                AudioOutputUnitStop(audioUnit)
                AudioUnitUninitialize(audioUnit)
                AudioComponentInstanceDispose(audioUnit)
            }.onFailure { it.printStackTrace() }
            audioUnit = null

            stableRef?.dispose()
            stableRef = null
        }
    }

}

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@CName("playbackCallback")
fun playbackCallback(
    inRefCon: COpaquePointer?,
    ioActionFlags: CPointer<AudioUnitRenderActionFlagsVar>?,
    inTimeStamp: CPointer<AudioTimeStamp>?,
    inBusNumber: UInt32,
    inNumberFrames: UInt32,
    ioData: CPointer<AudioBufferList>?
): OSStatus {
    val audioPlayerRef = inRefCon?.asStableRef<PCMVoicePlayerIOS1>() ?: return kAudio_NoError
    val audioPlayer = audioPlayerRef.get()

    if (ioData == null) return kAudio_NoError

    val bufferList = ioData.pointed
    if (bufferList.mNumberBuffers > 0u) {
        val audioBuffer = bufferList.mBuffers[0]
        val frameCount =
            minOf(inNumberFrames.toInt(), audioPlayer.audioBuffer.size - audioPlayer.currentIndex)
        val bytesToCopy = frameCount * sizeOf<ShortVar>()

        audioPlayer.audioBuffer.usePinned { audioBufferPinned ->
            val src = audioBufferPinned.addressOf(audioPlayer.currentIndex).reinterpret<ByteVar>()
            val dst = audioBuffer.mData?.reinterpret<ByteVar>() ?: return kAudio_NoError
            memcpy(dst, src, bytesToCopy.convert())
        }

        audioBuffer.mDataByteSize = bytesToCopy.convert()
        audioPlayer.currentIndex += frameCount

        if (audioPlayer.currentIndex >= audioPlayer.audioBuffer.size) {
            dispatch_async(dispatch_get_main_queue()) {
                audioPlayer.stopPlay()
            }
        }
    }
    return kAudio_NoError
}