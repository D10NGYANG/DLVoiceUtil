package com.d10ng.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

actual fun createPCMVoiceRecorder(sampleRate: Int): PCMVoiceRecorder = PCMVoiceRecorderAndroid(sampleRate)

class PCMVoiceRecorderAndroid(private val sampleRate: Int) : PCMVoiceRecorder(sampleRate) {
    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    override fun startRecord(): Flow<ShortArray> = flow {
        val minimum = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "Unsupported recording format: $minimum" }
        val bufferBytes = maxOf(minimum, sampleRate * 2 / 25)
        val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes)
        var suppressor: NoiseSuppressor? = null
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord initialization failed" }
            if (NoiseSuppressor.isAvailable()) {
                suppressor = NoiseSuppressor.create(recorder.audioSessionId)?.apply { enabled = true }
            }
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Recording did not start" }
            val buffer = ShortArray(bufferBytes / 2)
            while (true) {
                currentCoroutineContext().ensureActive()
                // Non-blocking reads make cancellation independent of the microphone driver.
                val count = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                check(count >= 0) { "AudioRecord.read failed: $count" }
                if (count > 0) emit(buffer.copyOf(count)) else kotlinx.coroutines.delay(5)
            }
        } finally {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            } finally {
                try { suppressor?.release() } finally { recorder.release() }
            }
        }
    }
}
