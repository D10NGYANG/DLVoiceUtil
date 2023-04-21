package com.d10ng.voice.app

import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import com.d10ng.voice.PCMPlayer
import com.d10ng.voice.PCMRecorder

class MainViewModel: ViewModel() {

    private val recorder = PCMRecorder()
    private val player = PCMPlayer()

    val recordStatusFlow = recorder.getRecordStatusFlow()
    val recordTimeTextFlow = recorder.getRecordTimeTextFlow()
    val recordVolumeFlow = recorder.getRecordVolumeFlow()

    val playStatusFlow = player.getPlayStatusFlow()
    val playTimeTextFlow = player.getPlayTimeTextFlow()

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
}