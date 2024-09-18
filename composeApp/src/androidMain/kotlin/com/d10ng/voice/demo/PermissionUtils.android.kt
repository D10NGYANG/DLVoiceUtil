package com.d10ng.voice.demo

import android.Manifest
import com.d10ng.app.managers.PermissionManager

/**
 * 权限工具
 * @Author d10ng
 * @Date 2024/9/18 16:14
 */
actual suspend fun requestRecordAudioPermission(): Boolean {
    return PermissionManager.request(Manifest.permission.RECORD_AUDIO)
}