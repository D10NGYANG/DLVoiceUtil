package com.d10ng.voice

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/** Plays mono, little-endian, 16-bit PCM. */
abstract class PCMVoicePlayer {
    private val playingFlow = MutableStateFlow(false)
    private val playTimeFlow = MutableStateFlow(0L)
    private val playTimeTextFlow = playTimeFlow.map { it / 1000 }.distinctUntilChanged().map { secondTime2Text(it) }
    private val playVolumeFlow = MutableSharedFlow<Float>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val errorFlow = MutableStateFlow<Throwable?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var updateVolumeJob: Job? = null

    fun getPlayStatusFlow() = playingFlow.asStateFlow()
    fun getPlayTimeFlow() = playTimeFlow.asStateFlow()
    fun getPlayTimeTextFlow() = playTimeTextFlow
    fun getPlayVolumeUpdateEvent() = playVolumeFlow.asSharedFlow()
    fun getErrorFlow() = errorFlow.asStateFlow()

    fun start(data: ByteArray, sampleRate: Int) = runBlocking {
        require(sampleRate > 0) { "Unsupported sample rate: $sampleRate" }
        require(data.isNotEmpty() && data.size % 2 == 0) { "Expected non-empty 16-bit PCM" }
        mutex.withLock {
            check(!playingFlow.value) { "Already playing" }
            errorFlow.value = null
            try {
                startPlay(data, sampleRate)
            } catch (e: Exception) {
                runCatching { stopPlay() }.exceptionOrNull()?.let(e::addSuppressed)
                errorFlow.value = e
                throw e
            }
            playingFlow.value = true
            playTimeFlow.value = 0
            val started = TimeSource.Monotonic.markNow()
            val duration = pcmDurationMillis(data.size, sampleRate)
            updateVolumeJob = scope.launch {
                var lastIndex = 0
                try {
                    while (isActive) {
                        val finished = mutex.withLock {
                            currentCoroutineContext().ensureActive()
                            val elapsed = started.elapsedNow().inWholeMilliseconds
                            val index = minOf(elapsed * sampleRate / 1000 * 2, data.size.toLong()).toInt()
                            playVolumeFlow.tryEmit(pcmVolume(data, lastIndex, index))
                            lastIndex = index
                            playTimeFlow.value = minOf(elapsed, duration)
                            if (isPlaybackComplete() ?: (elapsed >= duration)) {
                                stopPlay()
                                playingFlow.value = false
                                true
                            } else false
                        }
                        if (finished) break
                        delay(16)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    mutex.withLock {
                        currentCoroutineContext().ensureActive()
                        errorFlow.value = e
                        runCatching { stopPlay() }.exceptionOrNull()?.let(e::addSuppressed)
                        playingFlow.value = false
                    }
                }
            }
        }
    }

    fun stop() = runBlocking {
        mutex.withLock {
            updateVolumeJob?.cancel()
            updateVolumeJob = null
            try { stopPlay() } finally { playingFlow.value = false }
        }
    }

    internal abstract fun startPlay(data: ByteArray, sampleRate: Int)
    internal abstract fun stopPlay()
    internal open fun isPlaybackComplete(): Boolean? = null
}

expect fun createPCMVoicePlayer(): PCMVoicePlayer
