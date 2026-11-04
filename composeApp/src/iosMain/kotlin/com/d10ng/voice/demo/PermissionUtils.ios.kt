package com.d10ng.voice.demo

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioSession
import kotlin.coroutines.resume

actual suspend fun requestRecordAudioPermission(): Boolean = suspendCancellableCoroutine { continuation ->
    AVAudioSession.sharedInstance().requestRecordPermission { granted ->
        if (continuation.isActive) continuation.resume(granted)
    }
}
