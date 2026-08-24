package com.wxjxpp.neiro.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.core.search.OnlineSearchRepository
import com.wxjxpp.neiro.ui.components.SongCover
import com.wxjxpp.neiro.ui.theme.AppTheme

/**
 * 搜索页。
 *
 * - 搜索栏：Material3 [SearchBar]（带水平安全区，不再顶到屏幕边缘）
 * - 平台筛选：FilterChip + FlowRow
 * - 在线结果每行带下载按钮；未导入音源时给出明确空态引导
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    query: String,
    localResults: List<Song>,
    onlineResults: List<Song> = emptyList(),
    onlineFailed: List<String> = emptyList(),
    onlinePlatforms: List<OnlineSearchRepository.PlatformOption>,
    currentOnlinePlatform: String,
    isLoadingOnline: Boolean = false,
    /** 未导入任何音源时展示引导空态。 */
    noSourceAvailable: Boolean = false,
    onQueryChange: (String) -> Unit,
    onSongClick: (Song) -> Unit,
    onOnlinePlatformChange: (String) -> Unit,
    onDownloadSong: ((Song) -> Unit)? = null,
    onDownloadLyrics: ((Song) -> Unit)? = null,
    /** 在线结果长按多选后的批量操作。 */
    onFavorites: ((List<Song>) -> Unit)? = null,
    onDownloadMany: ((List<Song>) -> Unit)? = null,
    onBatchToPlaylist: ((List<Song>) -> Unit)? = null,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val dimens = AppTheme.dimens
    // 在线/本地 Tab 状态：仅聚合模式下有意义；默认先看在线结果。
    var showLocalTab by remember(query, onlineResults) { mutableStateOf(false) }
    // 在线结果多选（长按触发）
    val selectedOnline = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    fun toggleSelect(song: Song) {
        if (!selectedOnline.remove(song.id)) selectedOnline.add(song.id)
    }
    val selectedSongsList = onlineResults.filter { it.id in selectedOnline }
    val allLocalEmpty = localResults.isEmpty() && !isLoadingOnline && onlineResults.isEmpty()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        // 全屏搜索栏：激活时由 Material3 接管整屏展示（对齐官方 FullScreenSearchBar 行为），
        // 收起后作为顶部圆角搜索条。material3 1.5.0-alpha18 的 SearchBarDefaults.InputField
        // 已改为 TextFieldState 新签名，这里在 inputField 槽位用普通 TextField 保持字符串受控。
        var active by remember { mutableStateOf(false) }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
        SearchBar(
            inputField = {
                androidx.compose.material3.TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("歌名 / 歌手 / 专辑 / 标签") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "清空")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { active = false },
                    ),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { state ->
                            if (state.isFocused && !active) active = true
                        },
                )
            },
            expanded = active,
            onExpandedChange = { active = it },
            // 收起态：水平边距让搜索条不抵屏幕边缘；展开态全屏接管（去掉 padding，
            // 否则 M3 全屏 SearchBar 内容被挤到中间、顶部/两侧露白）
            modifier = Modifier
                .fillMaxWidth()
                .then(if (active) Modifier else Modifier.padding(horizontal = dimens.spaceMd)),
        ) {
            // 展开态正文：轻提示（不做历史记录）
            Text(
                text = "输入关键词后按搜索键收起并开始检索在线曲库",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = dimens.spaceLg, vertical = dimens.spaceMd),
            )
        }
        }
        // 平台筛选区也铺满主题底色，避免左右露白
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {

        // 聚合搜索时展示平台筛选器：FilterChip + FlowRow 自动换行
        if (query.isNotBlank() && onlinePlatforms.size > 1) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceXs),
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
            ) {
                onlinePlatforms.forEach { platform ->
                    FilterChip(
                        selected = currentOnlinePlatform == platform.id,
                        onClick = { onOnlinePlatformChange(platform.id) },
                        label = { Text(platform.displayName, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
        }
        }
        when {
            query.isBlank() -> {
                if (noSourceAvailable) {
                    // 未导入音源：明确空态 + 引导（用户指定文案）
                    NoSourceHint()
                } else {
                    HintText("输入关键词开始搜索")
                }
            }
            noSourceAvailable && onlineResults.isEmpty() && localResults.isEmpty() -> NoSourceHint()
            allLocalEmpty -> {
                if (onlineFailed.isEmpty()) {
                    HintText("没有匹配的歌曲")
                } else {
                    HintText("在线搜索失败：${onlineFailed.joinToString("、")}")
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            ) {
                // 聚合模式下同时展示在线 + 本地，带切换开关
                if (currentOnlinePlatform == OnlineSearchRepository.ALL) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.spaceLg, vertical = dimens.spaceXs),
                            horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 在线/本地切换：i=0 在线，i=1 本地
                            val labels = listOf("在线", "本地")
                            var tabActive by remember(query, onlineResults) { mutableStateOf(if (showLocalTab) 1 else 0) }
                            FlowRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
                            ) {
                                labels.forEachIndexed { i, label ->
                                    FilterChip(
                                        selected = tabActive == i,
                                        onClick = { tabActive = i; showLocalTab = (i == 1) },
                                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                    )
                                }
                            }
                            if (onlineFailed.isNotEmpty()) {
                                Text(
                                    "(${onlineFailed.size} 失败)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    item {
                        Divider(modifier = Modifier.padding(horizontal = dimens.spaceLg))
                    }
                }

                // 聚合模式按 Tab 过滤；单平台模式始终展示在线结果
                val showOnline = if (currentOnlinePlatform == OnlineSearchRepository.ALL) !showLocalTab else true
                val showLocalList = if (currentOnlinePlatform == OnlineSearchRepository.ALL) showLocalTab else false
                if (showOnline && onlineResults.isNotEmpty()) {
                    item(key = "online_header") {
                        SectionHeader("在线结果（${onlineResults.size}）")
                    }
                    if (selectedOnline.isNotEmpty()) {
                        item(key = "online_batchbar") {
                            BatchActionBar(
                                count = selectedOnline.size,
                                onFavorite = {
                                    onFavorites?.invoke(selectedSongsList)
                                    selectedOnline.clear()
                                },
                                onDownload = {
                                    onDownloadMany?.invoke(selectedSongsList)
                                    selectedOnline.clear()
                                },
                                onAddToPlaylist = {
                                    onBatchToPlaylist?.invoke(selectedSongsList)
                                    selectedOnline.clear()
                                },
                                onCancel = { selectedOnline.clear() },
                            )
                        }
                    }
                    items(onlineResults, key = { "online_${it.id}" }) { song ->
                        SearchResultRow(
                            song = song,
                            onClick = { onSongClick(song) },
                            onDownloadSong = onDownloadSong.takeIf { selectedOnline.isEmpty() },
                            onDownloadLyrics = onDownloadLyrics.takeIf { selectedOnline.isEmpty() },
                            selected = song.id in selectedOnline,
                            onLongClick = { toggleSelect(song) },
                        )
                    }
                }
                // 本地结果
                if (showLocalList && localResults.isNotEmpty()) {
                    item {
                        SectionHeader("本地曲库（${localResults.size}）")
                    }
                    items(localResults, key = { "local_${it.id}" }) { song ->
                        SearchResultRow(song = song, onClick = { onSongClick(song) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val dimens = AppTheme.dimens
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm),
    )
}

@Composable
private fun HintText(text: String) {
    val dimens = AppTheme.dimens
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = dimens.spaceLg, vertical = dimens.spaceMd),
    )
}

/** 未导入音源的空态：明确告知原因 + 添加按钮引导。 */
@Composable
private fun NoSourceHint() {
    val dimens = AppTheme.dimens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spaceXl, vertical = dimens.spaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("还没有可用音源", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(dimens.spaceSm))
        Text(
            text = "在线搜索与播放需要先导入音源脚本",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun SearchResultRow(
    song: Song,
    onClick: () -> Unit,
    onDownloadSong: ((Song) -> Unit)? = null,
    onDownloadLyrics: ((Song) -> Unit)? = null,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val dimens = AppTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimens.listItemHeight)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent,
            )
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
        // 在线结果提供下载入口：歌曲文件 / 歌词
        if (song.location is com.wxjxpp.neiro.core.model.MediaLocation.Remote) {
            if (onDownloadSong != null || onDownloadLyrics != null) {
                var showMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.Download, contentDescription = "下载", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    onDownloadSong?.let { download ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("下载歌曲") },
                            onClick = { showMenu = false; download(song) },
                        )
                    }
                    onDownloadLyrics?.let { download ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("下载歌词") },
                            onClick = { showMenu = false; download(song) },
                        )
                    }
                }
            }
        }
    }
}@Composable
private fun BatchActionBar(
    count: Int,
    onFavorite: () -> Unit,
    onDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.dimens.spaceLg, vertical = AppTheme.dimens.spaceXs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = AppTheme.dimens.spaceMd),
        ) {
            Text("已选 $count 首", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onFavorite) { Text("收藏") }
            TextButton(onClick = onDownload) { Text("下载") }
            TextButton(onClick = onAddToPlaylist) { Text("加歌单") }
            IconButton(onClick = onCancel) {
                Icon(Icons.Rounded.Close, contentDescription = "取消多选")
            }
        }
    }
}
