package com.wxjxpp.neiro.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.ui.components.SongCover
import com.wxjxpp.neiro.ui.theme.AppTheme

/**
 * 歌曲主页。
 *
 * 点击播放，长按进入多选。多选态下点击即切换选中，不会误触播放。
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun HomeScreen(
    songs: List<Song>,
    isRefreshing: Boolean,
    selectedIds: Set<String>,
    onRefresh: () -> Unit,
    onSongClick: (Song) -> Unit,
    onSongLongPress: (Song) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val refreshState = rememberPullToRefreshState()
    val inSelectionMode = selectedIds.isNotEmpty()

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
                SongRow(
                    song = song,
                    selected = song.id in selectedIds,
                    inSelectionMode = inSelectionMode,
                    onClick = { onSongClick(song) },
                    onLongClick = { onSongLongPress(song) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    song: Song,
    selected: Boolean,
    inSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val dimens = AppTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimens.listItemHeight)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = dimens.spaceLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
    ) {
        Box(contentAlignment = Alignment.Center) {
            SongCover(
                song = song,
                size = dimens.listCoverSize,
                radius = dimens.playerBarCoverRadius,
                modifier = if (selected) Modifier.alpha(0.35f) else Modifier,
            )
            if (inSelectionMode && selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "已选中",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
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

/** 歌曲页顶栏：扫描 + 搜索 + 排序。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsTopBar(
    onOpenDrawer: () -> Unit,
    onSearch: () -> Unit,
    onScan: () -> Unit,
    onPlayRandom: () -> Unit = {},
    sortField: com.wxjxpp.neiro.core.model.SongSortField = com.wxjxpp.neiro.core.model.SongSortField.Title,
    sortDescending: Boolean = false,
    onSortFieldChange: (com.wxjxpp.neiro.core.model.SongSortField) -> Unit = {},
    onSortDirectionToggle: () -> Unit = {},
) {
    var showSortMenu by remember { mutableStateOf(false) }
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
            // 排序：字段菜单 + 方向切换
            Box {
                IconButton(onClick = { showSortMenu = !showSortMenu }) {
                    Icon(Icons.Filled.Sort, contentDescription = "排序方式")
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    com.wxjxpp.neiro.core.model.SongSortField.entries.forEach { field ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (sortField == field) "✓ ${field.displayName}" else field.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            onClick = {
                                onSortFieldChange(field)
                                showSortMenu = false
                            },
                        )
                    }
                    androidx.compose.material3.HorizontalDivider()
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Text(
                                text = if (sortDescending) "当前：倒序" else "当前：正序",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (sortDescending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                contentDescription = null,
                            )
                        },
                        onClick = onSortDirectionToggle,
                    )
                }
            }
            IconButton(onClick = onScan) {
                Icon(Icons.Filled.Refresh, contentDescription = "扫描本地音乐")
            }
            IconButton(onClick = onPlayRandom) {
                Icon(Icons.Filled.Shuffle, contentDescription = "随机一发")
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = "搜索歌曲")
            }
        },
    )
}

/** 多选态顶栏：显示已选数量与批量操作。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        title = { Text("已选 $selectedCount 首") },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "退出多选")
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Filled.SelectAll, contentDescription = "全选")
            }
            IconButton(onClick = onAddToPlaylist) {
                Icon(Icons.Filled.PlaylistAdd, contentDescription = "加入歌单")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "从曲库移除")
            }
        },
    )
}