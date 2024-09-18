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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.d10ng.voice.PCMRecorder
import com.d10ng.voice.createPCMRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.abs

private var recorder: PCMRecorder? = null
private val filePath = getCacheDir() + "/audio.pcm"
private val recordVolumeListFlow = MutableStateFlow(List(40) { 0f })

@Composable
@Preview
fun App() {
    MaterialTheme {
        var recording by remember { mutableStateOf(false) }
        val recordVolumeList by recordVolumeListFlow.collectAsState()
        var recordTime by remember { mutableStateOf(0L) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Button(onClick = {
                if (recording) {
                    recording = false
                    recorder?.stop()
                } else {
                    CoroutineScope(Dispatchers.IO).launch {
                        if (requestRecordAudioPermission()) {
                            recording = true
                            recordTime = 0L
                            println("开始录音")
                            recorder = createPCMRecorder(48000).apply {
                                launch {
                                    deleteFile(filePath)
                                    start()
                                        .onCompletion { println("录音完成") }
                                        .collect {
                                            //writeFileAppend(filePath, it)
                                            var total = 0.0
                                            for (element in it) {
                                                total += abs(element.toDouble())
                                            }
                                            // 计算录音音量百分比
                                            val volume =
                                                (abs(total / it.size) / Short.MAX_VALUE * 5).toFloat()
                                                    .coerceAtMost(100f)
                                            println("录音音量: $volume")
                                            val ls = recordVolumeListFlow.value.toMutableList()
                                            ls.add(volume)
                                            ls.removeAt(0)
                                            recordVolumeListFlow.value = ls
                                            // 计算录音时间
                                            recordTime += it.size / 48
                                        }
                                }
                            }
                        }
                    }
                }
            }) {
                Text(text = if (recording) "停止录音" else "开始录音")
            }
            Text(text = "录音时长: ${recordTime / 1000.0} 秒")
            VolumeListBar(list = recordVolumeList)
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