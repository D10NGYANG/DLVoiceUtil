package com.d10ng.voice

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*

class PCMVoiceTest {
    private class Recorder(val source: () -> Flow<ShortArray>) : PCMVoiceRecorder(44100) {
        override fun startRecord() = source()
    }
    private class Player : PCMVoicePlayer() {
        var starts = 0
        var stops = 0
        var fail = false
        val finished = MutableStateFlow(false)
        override fun startPlay(data: ByteArray, sampleRate: Int) { starts++; check(!fail) }
        override fun stopPlay() { stops++ }
        override fun isPlaybackComplete() = finished.value
    }

    @Test fun littleEndianSignedBoundaries() {
        for ((bytes, value) in listOf(
            byteArrayOf(0, 0) to 0, byteArrayOf(0x80.toByte(), 0) to 128,
            byteArrayOf(-1, 0x7f) to 32767, byteArrayOf(0, 0x80.toByte()) to -32768,
            byteArrayOf(-1, -1) to -1
        )) assertEquals(value.toShort(), bytes.toShort())
    }

    @Test fun durationRetainsFractionalSamples() {
        for (rate in listOf(8000, 11025, 22050, 44100, 48000)) {
            assertEquals(1000L, pcmDurationMillis(rate * 2, rate))
        }
        assertEquals(0f, pcmVolume(byteArrayOf(), 0, 0))
        assertEquals(1f, pcmVolume(byteArrayOf(-1, 0x7f), 0, 2))
    }

    @Test fun stopWaitsForCleanupAndReturnsExactData() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val closing = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val recorder = Recorder {
            flow {
                try { emit(shortArrayOf(128, -32768, -1)); entered.complete(Unit); awaitCancellation() }
                finally { withContext(NonCancellable) { closing.complete(Unit); release.await() } }
            }
        }
        recorder.start()
        withTimeout(5000) { entered.await() }
        val stopped = async { recorder.stopAndGetData() }
        withTimeout(5000) { closing.await() }
        assertFalse(stopped.isCompleted)
        release.complete(Unit)
        assertContentEquals(byteArrayOf(0x80.toByte(), 0, 0, 0x80.toByte(), -1, -1), withTimeout(5000) { stopped.await() })
        assertFalse(recorder.getRecordStatusFlow().value)
    }

    @Test fun failureResetsStateAndAllowsRestart() = runBlocking {
        var fail = true
        val recorder = Recorder { flow { if (fail) error("setup failed") else emit(ShortArray(44100)) } }
        recorder.start()
        withTimeout(5000) { recorder.getRecordStatusFlow().first { !it } }
        recorder.stopAndGetData()
        assertNotNull(recorder.getErrorFlow().value)
        fail = false
        recorder.start()
        withTimeout(5000) { recorder.getRecordStatusFlow().first { !it } }
        assertEquals(88200, recorder.stopAndGetData().size)
        assertEquals(1000L, recorder.getRecordTimeFlow().value)
        assertNull(recorder.getErrorFlow().value)
    }

    @Test fun repeatedSessionsDoNotReuseAudio() = runBlocking {
        var value: Short = 1
        val recorder = Recorder { flow { repeat(1000) { emit(shortArrayOf(value)) } } }
        repeat(3) {
            recorder.start()
            withTimeout(5000) { recorder.getRecordStatusFlow().first { !it } }
            val bytes = recorder.stopAndGetData()
            assertEquals(2000, bytes.size)
            for (i in bytes.indices step 2) assertEquals(value.toByte(), bytes[i])
            value++
        }
    }

    @Test fun rejectsDuplicateRecording() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val recorder = Recorder { flow { entered.complete(Unit); awaitCancellation() } }
        recorder.start()
        withTimeout(5000) { entered.await() }
        assertFailsWith<IllegalStateException> { recorder.start() }
        recorder.stopAndGetData()
        Unit
    }

    @Test fun duplicatePlaybackDoesNotChangeSession() {
        val player = Player()
        player.start(ByteArray(16000), 8000)
        assertFailsWith<IllegalStateException> { player.start(ByteArray(1600), 8000) }
        assertEquals(1, player.starts)
        assertTrue(player.getPlayStatusFlow().value)
        player.stop()
        player.start(ByteArray(1600), 8000)
        assertEquals(2, player.starts)
        player.stop()
    }

    @Test fun playbackFailureCanRecover() {
        val player = Player()
        player.fail = true
        assertFailsWith<IllegalStateException> { player.start(ByteArray(16), 8000) }
        assertFalse(player.getPlayStatusFlow().value)
        assertNotNull(player.getErrorFlow().value)
        player.fail = false
        player.start(ByteArray(16), 8000)
        assertTrue(player.getPlayStatusFlow().value)
        player.stop()
    }

    @Test fun naturalPlaybackCompletionReleasesPlatform() = runBlocking {
        val player = Player()
        player.start(ByteArray(1600), 8000)
        player.finished.value = true
        withTimeout(5000) { player.getPlayStatusFlow().first { !it } }
        assertEquals(1, player.stops)
        assertNull(player.getErrorFlow().value)
        player.stop()
    }

    @Test fun rejectsInvalidPcmBeforeNativeStartup() {
        val player = Player()
        assertFailsWith<IllegalArgumentException> { player.start(byteArrayOf(), 8000) }
        assertFailsWith<IllegalArgumentException> { player.start(byteArrayOf(1), 8000) }
        assertFailsWith<IllegalArgumentException> { player.start(ByteArray(2), 0) }
        assertEquals(0, player.starts)
        assertFailsWith<IllegalArgumentException> { createPCMVoiceRecorder(0) }
    }
}
