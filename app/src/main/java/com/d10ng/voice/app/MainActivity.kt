package com.d10ng.voice.app

import android.os.Bundle
import androidx.activity.compose.setContent
import com.d10ng.compose.BaseActivity
import com.d10ng.compose.ui.AppTheme

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme(app = app) {

            }
        }
    }
}