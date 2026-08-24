package com.wxjxpp.neiro.feature.home

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wxjxpp.neiro.core.model.MediaLocation
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.ui.components.SongCover
import com.wxjxpp.neiro.ui.theme.AppTheme
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 歌曲主页（紧凑布局）。
 *
 * 点击播放，长按进入多选。多选态下点击即切换选中，不会误触播放。
 * 行尾「⋮」打开歌曲详情面板。顶栏由外壳传入（含导航按钮，安全区内）。
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
    /** 当前正在播放的歌曲 id（用于高亮与"定位当前播放"）。 */
    currentPlayingId: String? = null,
    topBar: @Composable () -> Unit = {},
    onRefresh: () -> Unit,
    onSongClick: (Song) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onRemoveFromLibrary: (Song) -> Unit,
    onRequestDeleteFile: (Song, (android.content.IntentSender) -> Unit) -> Unit,
    onFinalizeDeleteFile: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val refreshState = rememberPullToRefreshState()
    val inSelectionMode = selectedIds.isNotEmpty()
    // 一键回顶 / 定位当前播放
    val listState = rememberLazyListState()
    // 歌曲详情面板 & 删除确认
    var detailSong by remember { mutableStateOf<Song?>(null) }
    var confirmDelete by remember { mutableStateOf<Song?>(null) }
    // 等待系统删除确认的歌曲 id（launch 后 detailSong 已清空，靠这个收尾）
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    // 系统删除确认（Android 11+ Scoped Storage）：用户确认后收尾
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val target = pendingDeleteId
        pendingDeleteId = null
        if (result.resultCode == Activity.RESULT_OK && target != null) {
            onFinalizeDeleteFile(target)
        }
    }
        Column(modifier = modifier.fillMaxSize()) {
        topBar()
        Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
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
                state = listState,
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            ) {
                items(songs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        selected = song.id in selectedIds,
                        inSelectionMode = inSelectionMode,
                        isPlaying = song.id == currentPlayingId,
                        onClick = { onSongClick(song) },
                        onLongClick = { onSongLongPress(song) },
                        onOpenDetail = { detailSong = song },
                    )
                }
            }
        }
        // 悬浮操作组：回顶 + 定位当前播放（右侧，抬高避开播放栏）
        val scope = rememberCoroutineScope()
        com.wxjxpp.neiro.ui.components.ScrollActions(
            listState = listState,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = AppTheme.dimens.spaceLg,
                    // 抬高到播放栏上方，避免被遮挡
                    bottom = AppTheme.dimens.playerBarHeight +
                        AppTheme.dimens.floatingBarBottomMargin + AppTheme.dimens.spaceMd,
                ),
            onLocate = {
                val index = songs.indexOfFirst { it.id == currentPlayingId }
                if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
            },
        )
        }
    }

    // 歌曲详情 ActionSheet
    detailSong?.let { song ->
        ModalBottomSheet(onDismissRequest = { detailSong = null }) {
            SongDetailSheetContent(
                song = song,
                onRemoveFromLibrary = {
                    onRemoveFromLibrary(song)
                    detailSong = null
                },
                onDeleteFile = { confirmDelete = song },
                onDismiss = { detailSong = null },
            )
        }
    }

    // 删除文件二次确认
    confirmDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除歌曲文件") },
            text = { Text("确定要永久删除「${song.title}」的音频文件吗？\n\n该操作不可恢复，文件将从存储中移除。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    detailSong = null
                    onRequestDeleteFile(song) { sender ->
                        pendingDeleteId = song.id
                        deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("取消") }
            },
        )
    }
}

/** 歌曲详情面板内容：技术参数 + 文件信息 + 危险操作。 */
@Composable
private fun SongDetailSheetContent(
    song: Song,
    onRemoveFromLibrary: () -> Unit,
    onDeleteFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val filePath = (song.location as? MediaLocation.Local)?.filePath
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.dimens.spaceLg),
    ) {
        Text(song.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            song.artistName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(AppTheme.dimens.spaceMd))
        DetailRow("歌曲时长", formatMs(song.durationMs))
        DetailRow("歌曲码率", song.format.bitrateKbps?.let { "$it kbps" } ?: "未知")
        DetailRow("歌曲比特率", song.format.bitrateKbps?.let { "${it * 1000} bit/s" } ?: "未知")
        DetailRow("歌曲文件大小", filePath?.let { formatBytes(File(it).length()) } ?: "未知")
        DetailRow("文件修改日期", filePath?.let { formatDate(File(it).lastModified()) } ?: "未知")
        DetailRow("歌曲文件目录", filePath?.substringBeforeLast('/', "")?.ifEmpty { "未知" } ?: "未知")
        Spacer(Modifier.height(AppTheme.dimens.spaceLg))
        // 危险操作区
        SheetAction(label = "移除歌曲（不删除文件）", destructive = false, onClick = onRemoveFromLibrary)
        SheetAction(label = "删除歌曲文件", destructive = true, onClick = onDeleteFile)
        Spacer(Modifier.height(AppTheme.dimens.spaceXl))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 24.dp),
        )
    }
}

@Composable
private fun SheetAction(label: String, destructive: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (destructive) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "未知"
    bytes >= 1 shl 20 -> "%.1f MB".format(bytes / 1048576f)
    bytes >= 1 shl 10 -> "%.0f KB".format(bytes / 1024f)
    else -> "$bytes B"
}

private val dateFormat by lazy { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
private fun formatDate(ms: Long): String =
    if (ms <= 0L) "未知" else dateFormat.format(Date(ms))

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    song: Song,
    selected: Boolean,
    inSelectionMode: Boolean,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpenDetail: () -> Unit,
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
                    Icons.Rounded.Check,
                    contentDescription = "已选中",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title + if (isPlaying) " ♪" else "",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${song.artistName} · ${song.albumTitle}",
                style = MaterialTheme.typography.bodySmall,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 行尾详情入口：多选态隐藏避免误触
        if (!inSelectionMode) {
            IconButton(onClick = onOpenDetail) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "歌曲详情与操作",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
        windowInsets = WindowInsets(0),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null)
                Text(
                    text = "歌曲",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = AppTheme.dimens.spaceSm),
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Rounded.Menu, contentDescription = "打开导航")
            }
        },
        actions = {
            // 排序：字段菜单 + 方向切换
            Box {
                IconButton(onClick = { showSortMenu = !showSortMenu }) {
                    Icon(Icons.Rounded.Sort, contentDescription = "排序方式")
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
                                if (sortDescending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                                contentDescription = null,
                            )
                        },
                        onClick = onSortDirectionToggle,
                    )
                }
            }
            IconButton(onClick = onScan) {
                Icon(Icons.Rounded.Refresh, contentDescription = "扫描本地音乐")
            }
            IconButton(onClick = onPlayRandom) {
                Icon(Icons.Rounded.Shuffle, contentDescription = "随机一发")
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Rounded.Search, contentDescription = "搜索歌曲")
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
        windowInsets = WindowInsets(0),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        title = { Text("已选 $selectedCount 首") },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "退出多选")
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Rounded.SelectAll, contentDescription = "全选")
            }
            IconButton(onClick = onAddToPlaylist) {
                Icon(Icons.Rounded.PlaylistAdd, contentDescription = "加入歌单")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "从曲库移除")
            }
        },
    )
}