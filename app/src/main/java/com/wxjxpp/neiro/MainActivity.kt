package com.wxjxpp.neiro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wxjxpp.neiro.app.AppViewModel
import com.wxjxpp.neiro.app.MusicPlayerApp
import com.wxjxpp.neiro.app.MusicPlayerApplication
import com.wxjxpp.neiro.ui.theme.MusicPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MusicPlayerApplication).container
        setContent {
            // 全局字体设置：从 ViewModel 状态实时读取，切换立即生效
            val viewModel: AppViewModel = viewModel(factory = AppViewModel.factory(container))
            val uiState by viewModel.uiState.collectAsState()
            MusicPlayerTheme(
                fontScale = uiState.appFontScale,
                fontFamilyId = uiState.appFontFamily,
            ) {
                MusicPlayerApp(container = container)
            }
        }
    }
}