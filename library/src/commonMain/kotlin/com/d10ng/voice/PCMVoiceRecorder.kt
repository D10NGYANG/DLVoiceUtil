package com.d10ng.voice

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Records mono, little-endian, 16-bit PCM. */
abstract class PCMVoiceRecorder(private val sampleRate: Int) {
    init { require(sampleRate > 0) { "Unsupported sample rate: $sampleRate" } }
    private val recordingFlow = MutableStateFlow(false)
    private val recordTimeFlow = MutableStateFlow(0L)
    private val recordTimeTextFlow = recordTimeFlow.map { it / 1000 }.distinctUntilChanged().map { secondTime2Text(it) }
    private val recordVolumeFlow = MutableSharedFlow<Float>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val errorFlow = MutableStateFlow<Throwable?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var recordJob: Job? = null
    private val chunks = mutableListOf<ByteArray>()
    private var byteCount = 0

    fun getRecordStatusFlow() = recordingFlow.asStateFlow()
    fun getRecordTimeFlow() = recordTimeFlow.asStateFlow()
    fun getRecordTimeTextFlow() = recordTimeTextFlow
    fun getRecordVolumeUpdateEvent() = recordVolumeFlow.asSharedFlow()
    fun getErrorFlow() = errorFlow.asStateFlow()

    fun start() = runBlocking {
        mutex.withLock {
            check(!recordingFlow.value) { "Already recording" }
            recordJob?.join()
            chunks.clear()
            byteCount = 0
            recordTimeFlow.value = 0
            errorFlow.value = null
            recordingFlow.value = true
            recordJob = scope.launch {
                try {
                    startRecord().collect { samples ->
                        if (samples.isNotEmpty()) {
                            check(samples.size.toLong() * 2 + byteCount <= Int.MAX_VALUE) { "Recording is too large" }
                            val bytes = ByteArray(samples.size * 2)
                            for (i in samples.indices) {
                                bytes[i * 2] = samples[i].toByte()
                                bytes[i * 2 + 1] = (samples[i].toInt() shr 8).toByte()
                            }
                            chunks.add(bytes)
                            byteCount += bytes.size
                            recordTimeFlow.value = pcmDurationMillis(byteCount, sampleRate)
                            recordVolumeFlow.tryEmit(pcmVolume(bytes, 0, bytes.size))
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errorFlow.value = e
                } finally {
                    recordingFlow.value = false
                }
            }
        }
    }

    /** Compatibility API; prefer stopAndGetData off the UI thread for large recordings. */
    fun stop(): ByteArray = runBlocking { stopAndGetData() }

    suspend fun stopAndGetData(): ByteArray = withContext(Dispatchers.IO) {
        mutex.withLock {
            recordJob?.cancelAndJoin()
            recordJob = null
            recordingFlow.value = false
            val result = ByteArray(byteCount)
            var offset = 0
            for (chunk in chunks) {
                chunk.copyInto(result, offset)
                offset += chunk.size
            }
            result
        }
    }

    internal abstract fun startRecord(): Flow<ShortArray>
}

expect fun createPCMVoiceRecorder(sampleRate: Int): PCMVoiceRecorder
