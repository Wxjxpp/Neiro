package com.wxjxpp.neiro

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wxjxpp.neiro.app.AppViewModel
import com.wxjxpp.neiro.app.MusicPlayerApp
import com.wxjxpp.neiro.app.MusicPlayerApplication
import com.wxjxpp.neiro.ui.theme.MusicPlayerTheme
class MainActivity : ComponentActivity() {
    /** 通知权限：Android 13+ 动态请求（媒体通知与下载通知都需要）。 */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
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

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}