package com.d10ng.voice

import kotlinx.cinterop.*
import platform.CoreAudioTypes.AudioBufferList
import kotlin.test.*

@OptIn(ExperimentalForeignApi::class)
class PCMNativeBufferTest {
    @Test fun wavHeaderUsesRequestedSampleRate() {
        for (rate in listOf(8000, 44100, 48000)) {
            val header = PCMVoicePlayerIOS().createWavHeader(rate * 2, rate)
            fun intAt(offset: Int): Int = (0..3).fold(0) { result, i ->
                result or ((header[offset + i].toInt() and 255) shl (i * 8))
            }
            assertEquals(44, header.size)
            assertEquals(rate, intAt(24))
            assertEquals(rate * 2, intAt(28))
            assertEquals(rate * 2, intAt(40))
            assertEquals(rate * 2 + 36, intAt(4))
        }
    }

    @Test fun playbackTailIsSilentAndCompletionDoesNotReadPastEnd() = memScoped {
        val player = PCMVoicePlayerIOS1()
        player.audioBuffer = shortArrayOf(128, -32768)
        val ref = StableRef.create(player)
        try {
            val output = allocArray<ShortVar>(4)
            for (i in 0..3) output[i] = 99
            val list = alloc<AudioBufferList>()
            list.mNumberBuffers = 1u
            list.mBuffers[0].mNumberChannels = 1u
            list.mBuffers[0].mData = output
            list.mBuffers[0].mDataByteSize = 8u
            assertEquals(0, playbackCallback(ref.asCPointer(), null, null, 0u, 4u, list.ptr))
            assertContentEquals(shortArrayOf(128, -32768, 0, 0), ShortArray(4) { output[it] })
            assertEquals(2, player.currentIndex)
            assertFalse(player.playbackEnded)
            assertEquals(0, playbackCallback(ref.asCPointer(), null, null, 0u, 4u, list.ptr))
            assertContentEquals(ShortArray(4), ShortArray(4) { output[it] })
            assertTrue(player.playbackEnded)
        } finally { ref.dispose() }
    }
}
