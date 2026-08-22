package com.wxjxpp.neiro.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.wxjxpp.neiro.ui.theme.AppTheme

/**
 * 空曲库引导页。
 *
 * 没有歌曲时给出明确动作：授予权限 / 扫描本地音乐，
 * 而不是只显示一句“暂无歌曲”。
 */
@Composable
fun EmptySongsScreen(
    hasPermission: Boolean,
    isScanning: Boolean,
    onRequestPermission: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = AppTheme.dimens
    Column(
        modifier = modifier.fillMaxSize().padding(d.spaceXl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.LibraryMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "暂无歌曲",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = d.spaceMd),
        )
        Text(
            text = if (hasPermission) "点击下方按钮扫描设备中的音乐文件" else "需要音频读取权限才能扫描本地音乐",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = d.spaceXs),
        )
        Button(
            onClick = if (hasPermission) onScan else onRequestPermission,
            enabled = !isScanning,
            modifier = Modifier.padding(top = d.spaceLg),
        ) {
            Text(
                when {
                    isScanning -> "扫描中…"
                    hasPermission -> "扫描本地音乐"
                    else -> "授予权限"
                }
            )
        }
    }
}