package com.d10ng.voice.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.d10ng.voice.createPCMVoicePlayer
import com.d10ng.voice.createPCMVoiceRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview

private const val sampleRate = 8000
@Composable
@Preview
fun App() {
    val recorder = remember { createPCMVoiceRecorder(sampleRate) }
    val player = remember { createPCMVoicePlayer() }
    val scope = rememberCoroutineScope()
    var recordData by remember { mutableStateOf<ByteArray?>(null) }
    val recordVolumeListFlow = remember { MutableStateFlow(List(40) { 0f }) }
    val playVolumeListFlow = remember { MutableStateFlow(List(40) { 0f }) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var action by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val recordError by recorder.getErrorFlow().collectAsState()
    val playError by player.getErrorFlow().collectAsState()
    fun finishSession() {
        action?.cancel()
        busy = false
        runCatching {
            try {
                if (recorder.getRecordStatusFlow().value) recordData = recorder.stop()
                else recorder.stop()
            } finally { player.stop() }
        }.onFailure { message = it.message ?: "停止失败" }
    }
    DisposableEffect(Unit) { onDispose { finishSession() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { finishSession() }

    MaterialTheme {
        val recording by recorder.getRecordStatusFlow().collectAsState()
        val recordTimeText by recorder.getRecordTimeTextFlow().collectAsState("")
        val recordVolumeList by recordVolumeListFlow.collectAsState()

        val playing by player.getPlayStatusFlow().collectAsState()
        val playTimeText by player.getPlayTimeTextFlow().collectAsState("")
        val playVolumeList by playVolumeListFlow.collectAsState()

        LaunchedEffect(Unit) {
            launch {
                recorder.getRecordVolumeUpdateEvent().collect {
                    val ls = recordVolumeListFlow.value.toMutableList()
                    ls.add(it)
                    ls.removeAt(0)
                    recordVolumeListFlow.value = ls
                }
            }
            launch {
                player.getPlayVolumeUpdateEvent().collect {
                    val ls = playVolumeListFlow.value.toMutableList()
                    ls.add(it)
                    ls.removeAt(0)
                    playVolumeListFlow.value = ls
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Button(enabled = !busy && !playing, onClick = {
                busy = true
                message = ""
                action = scope.launch {
                    try {
                        if (recording) recordData = recorder.stopAndGetData()
                        else if (requestRecordAudioPermission()) {
                            coroutineContext.ensureActive()
                            recorder.start()
                        } else message = "未获得麦克风权限"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        message = e.message ?: "录音失败"
                    } finally { busy = false }
                }
            }) {
                Text(text = if (recording) "停止录音" else "开始录音")
            }
            Text(text = "录音时长: $recordTimeText")
            VolumeListBar(list = recordVolumeList)

            Button(enabled = !busy && !recording && (playing || recordData?.isNotEmpty() == true), onClick = {
                runCatching {
                    if (playing) player.stop() else recordData?.let { player.start(it, sampleRate) }
                }.onFailure { message = it.message ?: "播放失败" }
            }) {
                Text(text = if (playing) "停止播放" else "播放录音")
            }
            Text(text = "播放时长: $playTimeText")
            VolumeListBar(list = playVolumeList)

            Button(enabled = !busy && !recording && recordData?.isNotEmpty() == true, onClick = {
                val bytes = recordData ?: return@Button
                busy = true
                action = scope.launch {
                    try {
                        withContext(Dispatchers.IO) { writeFile(getCacheFilePath("audio.pcm"), bytes) }
                        message = "已保存 audio.pcm"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        message = e.message ?: "保存失败"
                    } finally { busy = false }
                }
            }) { Text("保存文件到缓存目录") }
            val error = recordError ?: playError
            if (error != null) Text(error.message ?: "音频操作失败")
            if (message.isNotEmpty() && message != error?.message) Text(message)

        }
    }
}


@Composable
fun VolumeListBar(
    list: List<Float>
) {
    Row(
        modifier = Modifier
            .height(100.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        list.forEach {
            Box(
                modifier = Modifier
                    .padding(start = 2.dp)
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight(it.coerceIn(0f, 1f))
                        .width(4.dp)
                        .background(Color.Green)
                )
            }
        }
    }
}