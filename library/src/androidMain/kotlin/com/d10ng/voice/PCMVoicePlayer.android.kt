package com.d10ng.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build

/**
 * 创建PCM播放器
 * @return PCMVoicePlayer
 */
actual fun createPCMVoicePlayer(): PCMVoicePlayer {
    return PCMVoicePlayerAndroid()
}

class PCMVoicePlayerAndroid : PCMVoicePlayer() {

    private var audioTrack: AudioTrack? = null
    private var frameCount = 0

    override fun isPlaybackComplete(): Boolean = (audioTrack?.playbackHeadPosition ?: 0) >= frameCount

    override fun startPlay(data: ByteArray, sampleRate: Int) {
        check(audioTrack == null) { "Already playing" }
        frameCount = data.size / 2
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
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(data.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } else {
            @Suppress("DEPRECATION")
            audioTrack = AudioTrack(
                android.media.AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                data.size,
                AudioTrack.MODE_STATIC
            )
        }
        check(audioTrack?.state != AudioTrack.STATE_UNINITIALIZED) { "AudioTrack initialization failed" }
        check(audioTrack?.write(data, 0, data.size) == data.size) { "Incomplete AudioTrack write" }
        audioTrack?.play()
    }

    override fun stopPlay() {
        val track = audioTrack ?: return
        audioTrack = null
        try { track.stop() } finally { track.release() }
    }
}
