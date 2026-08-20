package com.wxjxpp.musicplayer.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.wxjxpp.musicplayer.core.model.Song
import com.wxjxpp.musicplayer.ui.components.SongCover
import com.wxjxpp.musicplayer.ui.theme.AppTheme

/** 歌曲主页：只展示歌曲；下拉即触发本地扫描。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    songs: List<Song>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSongClick: (Song) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val refreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        state = refreshState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = refreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            items(songs, key = { it.id }) { song ->
                SongRow(song = song, onClick = { onSongClick(song) })
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, onClick: () -> Unit) {
    val dimens = AppTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimens.listItemHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spaceLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
    ) {
        SongCover(song = song, size = dimens.listCoverSize, radius = dimens.playerBarCoverRadius)
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = "${song.artistName} · ${song.albumTitle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 歌曲页顶栏：扫描 + 搜索，两个入口都只在这里出现。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsTopBar(
    onOpenDrawer: () -> Unit,
    onSearch: () -> Unit,
    onScan: () -> Unit,
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MusicNote, contentDescription = null)
                Text(
                    text = "歌曲",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = AppTheme.dimens.spaceSm),
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Filled.Menu, contentDescription = "打开导航")
            }
        },
        actions = {
            IconButton(onClick = onScan) {
                Icon(Icons.Filled.Refresh, contentDescription = "扫描本地音乐")
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = "搜索歌曲")
            }
        },
    )
}