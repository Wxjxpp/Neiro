package com.wxjxpp.musicplayer.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.wxjxpp.musicplayer.ui.theme.AppTheme

@Composable
fun EmptySongsScreen(modifier: Modifier = Modifier) {
    val d = AppTheme.dimens
    Column(modifier = modifier.fillMaxSize().padding(d.spaceXl), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("暂无歌曲", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = d.spaceMd))
        Text("扫描本地歌曲后会显示在这里", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
