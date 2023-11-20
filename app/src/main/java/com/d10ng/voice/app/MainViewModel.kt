package com.d10ng.voice.app

import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.d10ng.voice.PCMPlayer
import com.d10ng.voice.PCMRecorder
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

@OptIn(FlowPreview::class)
class MainViewModel: ViewModel() {

    private val recorder = PCMRecorder()
    private val player = PCMPlayer()

    val recordStatusFlow = recorder.getRecordStatusFlow()
    val recordTimeTextFlow = recorder.getRecordTimeTextFlow()
    val recordVolumeListFlow = MutableStateFlow(List(10) { 0 })

    val playStatusFlow = player.getPlayStatusFlow()
    val playTimeTextFlow = player.getPlayTimeTextFlow()
    val playVolumeListFlow = MutableStateFlow(List(10) { 0 })

    init {
        viewModelScope.launch {
            recorder.getRecordVolumeUpdateEvent().collect {
                val ls = recordVolumeListFlow.value.toMutableList()
                ls.add(it)
                ls.removeAt(0)
                recordVolumeListFlow.value = ls
            }
        }
        viewModelScope.launch {
            player.getPlayVolumeUpdateEvent().collect {
                val ls = playVolumeListFlow.value.toMutableList()
                ls.add(it)
                ls.removeAt(0)
                playVolumeListFlow.value = ls
            }
        }
    }

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun startRecord() {
        recorder.start()
    }

    fun stopRecord() {
        // 获取录音数据
        val pcmData = recorder.stop()
    }

    fun startPlay() {
        recorder.data?.apply {
            player.start(this)
        }
    }

    fun stopPlay() {
        player.stop()
    }

    fun save() {
        recorder.data?.apply {
            val file =
                File(MainActivity.instant!!.cacheDir, "output-${System.currentTimeMillis()}.pcm")
            file.writeBytes(this)
        }
    }
}