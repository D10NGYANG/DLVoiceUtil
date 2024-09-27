package com.d10ng.voice

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ShortVar
import kotlinx.cinterop.StableRef
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
import kotlinx.cinterop.value
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioInputNode
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionCategoryRecord
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
import platform.AudioToolbox.AudioUnitRender
import platform.AudioToolbox.AudioUnitRenderActionFlagsVar
import platform.AudioToolbox.AudioUnitSetProperty
import platform.AudioToolbox.AudioUnitUninitialize
import platform.AudioToolbox.kAudioOutputUnitProperty_EnableIO
import platform.AudioToolbox.kAudioOutputUnitProperty_SetInputCallback
import platform.AudioToolbox.kAudioUnitManufacturer_Apple
import platform.AudioToolbox.kAudioUnitProperty_StreamFormat
import platform.AudioToolbox.kAudioUnitScope_Global
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
import platform.darwin.OSStatus
import platform.darwin.UInt32
import platform.darwin.UInt32Var
import kotlin.experimental.ExperimentalNativeApi

/**
 * 创建PCM录音器
 * @param sampleRate Int
 * @return PCMVoiceRecorder
 */
actual fun createPCMVoiceRecorder(sampleRate: Int): PCMVoiceRecorder {
    return PCMVoiceRecorderIOS1(sampleRate)
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
        val bufferSizeInShorts = sampleRate * 2 * 1 / 25
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
            // 设置bufferSizeInShorts无效，暂时不管
            inputNode?.installTapOnBus(0u, bufferSizeInShorts.toUInt(), format) { buffer, _ ->
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

class PCMVoiceRecorderIOS1(
    private val sampleRate: Int
) : PCMVoiceRecorder(sampleRate) {

    private var isRecording = false

    @OptIn(ExperimentalForeignApi::class)
    var audioUnit: AudioComponentInstance? = null

    @OptIn(ExperimentalForeignApi::class)
    private var stableRef: StableRef<PCMVoiceRecorderIOS1>? = null

    val audioDataChannel = Channel<ShortArray>(Channel.UNLIMITED)

    @OptIn(ExperimentalForeignApi::class)
    override fun startRecord(): Flow<ShortArray> {
        if (audioUnit != null) throw Exception("Already recording")
        AVAudioSession.sharedInstance().apply {
            setCategory(AVAudioSessionCategoryRecord, AVAudioSessionModeDefault, 0u, null)
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
                kAudioUnitScope_Input,
                1u,
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
                kAudioUnitScope_Output,
                1u,
                asbd.ptr,
                sizeOf<AudioStreamBasicDescription>().convert()
            )
            println("status3 = $status")
            stableRef = StableRef.create(this@PCMVoiceRecorderIOS1)
            val selfPtr = stableRef!!.asCPointer()
            val callbackStruct = alloc<AURenderCallbackStruct>().apply {
                inputProc = staticCFunction(::recordingCallback)
                inputProcRefCon = selfPtr
            }
            status = AudioUnitSetProperty(
                audioUnit,
                kAudioOutputUnitProperty_SetInputCallback,
                kAudioUnitScope_Global,
                0u,
                callbackStruct.ptr,
                sizeOf<AURenderCallbackStruct>().convert()
            )
            println("status4 = $status")
            status = AudioUnitInitialize(audioUnit)
            println("status5 = $status")
            status = AudioOutputUnitStart(audioUnit)
            println("status6 = $status")
            isRecording = true
            return flow {
                try {
                    while (isRecording) {
                        val audioData = audioDataChannel.receive()
                        emit(audioData)
                    }
                } finally {
                    isRecording = false
                    cleanUpAudioUnit()
                }
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun cleanUpAudioUnit() {
        if (audioUnit != null) {
            AudioOutputUnitStop(audioUnit)
            AudioUnitUninitialize(audioUnit)
            AudioComponentInstanceDispose(audioUnit)
            audioUnit = null
        }
    }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@CName("recordingCallback")
private fun recordingCallback(
    inRefCon: COpaquePointer?,
    ioActionFlags: CPointer<AudioUnitRenderActionFlagsVar>?,
    inTimeStamp: CPointer<AudioTimeStamp>?,
    inBusNumber: UInt32,
    inNumberFrames: UInt32,
    ioData: CPointer<AudioBufferList>?
): OSStatus {
    val recorder = inRefCon!!.asStableRef<PCMVoiceRecorderIOS1>().get()

    memScoped {
        val bufferList = alloc<AudioBufferList>().apply {
            mNumberBuffers = 1u
            mBuffers.pointed.apply {
                mNumberChannels = 1u
                mDataByteSize = inNumberFrames * 2u
                mData = null
            }
        }

        val status = AudioUnitRender(
            inUnit = recorder.audioUnit,
            ioActionFlags = ioActionFlags,
            inTimeStamp = inTimeStamp,
            inOutputBusNumber = inBusNumber,
            inNumberFrames = inNumberFrames,
            ioData = bufferList.ptr
        )

        if (status == 0) {
            val samples = bufferList.mBuffers.pointed.mData!!.reinterpret<ShortVar>()
            val frameCount = (bufferList.mBuffers.pointed.mDataByteSize / 2u).toInt()
            val shortArray = ShortArray(frameCount)
            for (i in 0 until frameCount) {
                shortArray[i] = samples[i]
            }
            recorder.audioDataChannel.trySend(shortArray)
        } else {
            println("AudioUnitRender error: $status")
        }

        return status
    }
}