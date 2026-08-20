package com.wxjxpp.musicplayer.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.wxjxpp.musicplayer.ui.theme.AppTheme

/** 设置没有顶部标题；只包含可修改项目。 */
@Composable
fun SettingsScreen(
    floatingPlayerBar: Boolean,
    onFloatingPlayerBarChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AppTheme.dimens
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = dimens.spaceLg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dimens.spaceLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("悬浮播放栏", style = MaterialTheme.typography.titleMedium)
                Text(
                    "将底部播放栏显示为悬浮卡片",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = floatingPlayerBar,
                onCheckedChange = onFloatingPlayerBarChange,
            )
        }
    }
}