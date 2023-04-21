package com.d10ng.voice.app

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.d10ng.compose.BaseActivity
import com.d10ng.compose.ui.AppColor
import com.d10ng.compose.ui.AppTheme
import com.d10ng.compose.view.VerticalLinearProgressIndicator
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme(app = app) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    MainView()
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainView(
    model: MainViewModel = viewModel()
) {
    val audioPermissionState = rememberPermissionState(
        android.Manifest.permission.RECORD_AUDIO
    )

    val recordStatus by model.recordStatusFlow.collectAsState()
    val recordTimeText by model.recordTimeTextFlow.collectAsState("")
    val recordVolumeList by model.recordVolumeListFlow.collectAsState()
    val playStatus by model.playStatusFlow.collectAsState()
    val playTimeText by model.playTimeTextFlow.collectAsState("")
    val playVolumeList by model.playVolumeListFlow.collectAsState()

    if (audioPermissionState.status.isGranted) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Button(onClick = {
                if (playStatus) return@Button
                if (recordStatus) model.stopRecord() else model.startRecord()
            }) {
                val text = if (recordStatus) "停止录音" else "开始录音"
                Text(text)
            }
            Text("当前录音时长: $recordTimeText")
            VolumeListBar(list = recordVolumeList)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = {
                if (recordStatus) return@Button
                if (playStatus) model.stopPlay() else model.startPlay()
            }) {
                Text(if (playStatus) "停止播放" else "播放录音文件")
            }
            Text("当前播放时长: $playTimeText")
            VolumeListBar(list = playVolumeList)
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Button(onClick = { audioPermissionState.launchPermissionRequest() }) {
                Text("请求录音权限")
            }
        }
    }
}

@Composable
fun VolumeListBar(
    list: List<Int>
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
                    .padding(start = 8.dp)
                    .fillMaxHeight()
                    .width(8.dp)
                    .background(Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier
                    .fillMaxHeight(it / 100f)
                    .width(8.dp)
                    .background(Color.Green))
            }
        }
    }
}