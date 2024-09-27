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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.d10ng.voice.createPCMVoicePlayer
import com.d10ng.voice.createPCMVoiceRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

private const val sampleRate = 8000
private val recorder = createPCMVoiceRecorder(sampleRate)
private val player = createPCMVoicePlayer()
private val filePath = getCacheFilePath("audio.pcm")
private val recordVolumeListFlow = MutableStateFlow(List(40) { 0f })
private var recordData: ByteArray? = null
private val playVolumeListFlow = MutableStateFlow(List(40) { 0f })

@Composable
@Preview
fun App() {
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
            Button(onClick = {
                if (recording) {
                    recordData = recorder.stop()
                } else {
                    CoroutineScope(Dispatchers.IO).launch {
                        if (requestRecordAudioPermission()) {
                            recorder.start()
                        }
                    }
                }
            }) {
                Text(text = if (recording) "停止录音" else "开始录音")
            }
            Text(text = "录音时长: $recordTimeText")
            VolumeListBar(list = recordVolumeList)

            Button(onClick = {
                if (playing) {
                    player.stop()
                } else {
                    recordData?.let { player.start(it, sampleRate) }
                }
            }) {
                Text(text = if (playing) "停止播放" else "播放录音")
            }
            Text(text = "播放时长: $playTimeText")
            VolumeListBar(list = playVolumeList)

            Button(onClick = {
                recordData?.let { writeFile(filePath, it) }
            }) {
                Text(text = "保存文件到缓存目录")
            }
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
                        .fillMaxHeight(it)
                        .width(4.dp)
                        .background(Color.Green)
                )
            }
        }
    }
}