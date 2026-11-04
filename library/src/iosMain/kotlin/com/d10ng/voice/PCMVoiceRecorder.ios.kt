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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import platform.AVFAudio.AVAudioSession
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
class PCMVoiceRecorderIOS(sampleRate: Int) : PCMVoiceRecorderIOS1(sampleRate)

open class PCMVoiceRecorderIOS1(
    private val sampleRate: Int
) : PCMVoiceRecorder(sampleRate) {


    @OptIn(ExperimentalForeignApi::class)
    var audioUnit: AudioComponentInstance? = null

    @OptIn(ExperimentalForeignApi::class)
    private var stableRef: StableRef<PCMVoiceRecorderIOS1>? = null

    var audioDataChannel = Channel<ShortArray>(64)
        private set

    @OptIn(ExperimentalForeignApi::class)
    override fun startRecord(): Flow<ShortArray> = flow {
        if (audioUnit != null) throw Exception("Already recording")
        audioDataChannel = Channel(64)
        try {
            AVAudioSession.sharedInstance().apply {
                check(setCategory(AVAudioSessionCategoryRecord, AVAudioSessionModeDefault, 0u, null)) { "Cannot configure audio session" }
                check(setActive(true, null)) { "Cannot activate audio session" }
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
                audioUnit = audioUnitVar.value
                check(status == 0) { "AudioComponentInstanceNew failed: $status" }
                val one = alloc<UInt32Var>().apply { value = 1u }
                status = AudioUnitSetProperty(
                    audioUnit!!,
                    kAudioOutputUnitProperty_EnableIO,
                    kAudioUnitScope_Input,
                    1u,
                    one.ptr,
                    sizeOf<UInt32Var>().convert()
                )
                check(status == 0) { "AudioUnit setup step 2 failed: $status" }
                val zero = alloc<UInt32Var>().apply { value = 0u }
                status = AudioUnitSetProperty(audioUnit, kAudioOutputUnitProperty_EnableIO,
                    kAudioUnitScope_Output, 0u, zero.ptr, sizeOf<UInt32Var>().convert())
                check(status == 0) { "Cannot disable recording output: $status" }

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
                check(status == 0) { "AudioUnit setup step 3 failed: $status" }
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
                check(status == 0) { "AudioUnit setup step 4 failed: $status" }
                status = AudioUnitInitialize(audioUnit)
                check(status == 0) { "AudioUnit setup step 5 failed: $status" }
                status = AudioOutputUnitStart(audioUnit)
                check(status == 0) { "AudioUnit setup step 6 failed: $status" }
            }
            for (audioData in audioDataChannel) emit(audioData)
        } finally {
            try { cleanUpAudioUnit() } finally { audioDataChannel.cancel() }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun cleanUpAudioUnit() {
        if (audioUnit != null) {
            AudioOutputUnitStop(audioUnit)
            AudioUnitUninitialize(audioUnit)
            check(AudioComponentInstanceDispose(audioUnit) == 0) { "Cannot dispose recording AudioUnit" }
            audioUnit = null
        }
        stableRef?.dispose()
        stableRef = null
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
            val samples = bufferList.mBuffers.pointed.mData?.reinterpret<ShortVar>()
            if (samples == null) {
                recorder.audioDataChannel.close(IllegalStateException("AudioUnitRender returned no samples"))
                return -1
            }
            val frameCount = (bufferList.mBuffers.pointed.mDataByteSize / 2u).toInt()
            val shortArray = ShortArray(frameCount)
            for (i in 0 until frameCount) {
                shortArray[i] = samples[i]
            }
            if (recorder.audioDataChannel.trySend(shortArray).isFailure) {
                recorder.audioDataChannel.close(IllegalStateException("Recording consumer overflow"))
            }
        } else {
            recorder.audioDataChannel.close(IllegalStateException("AudioUnitRender failed: $status"))
        }

        return status
    }
}