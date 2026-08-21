package com.wxjxpp.musicplayer.feature.search

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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.wxjxpp.musicplayer.core.model.Song
import com.wxjxpp.musicplayer.core.search.OnlineSearchRepository
import com.wxjxpp.musicplayer.ui.components.SongCover
import com.wxjxpp.musicplayer.ui.theme.AppTheme

/**
 * 搜索页。
 *
 * - 聚合搜索：并发搜全部平台，交错展示
 * - 单平台：按平台过滤
 */
@Composable
fun SearchScreen(
    query: String,
    localResults: List<Song>,
    onlineResults: List<Song> = emptyList(),
    onlineFailed: List<String> = emptyList(),
    onlinePlatforms: List<OnlineSearchRepository.PlatformOption>,
    currentOnlinePlatform: String,
    isLoadingOnline: Boolean = false,
    onQueryChange: (String) -> Unit,
    onSongClick: (Song) -> Unit,
    onOnlinePlatformChange: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val dimens = AppTheme.dimens
    var showLocal by remember(query, onlineResults) { mutableStateOf(localResults.isNotEmpty()) }
    val allLocalEmpty = localResults.isEmpty() && !isLoadingOnline && onlineResults.isEmpty()

    Column(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm),
            placeholder = { Text("歌名 / 歌手 / 专辑 / 标签") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )

        // 聚合搜索时展示平台筛选器
        if (query.isNotBlank() && onlinePlatforms.size > 1) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceXs)
            ) {
                onlinePlatforms.forEach { platform ->
                    SegmentedButton(
                        selected = currentOnlinePlatform == platform.id,
                        onClick = { onOnlinePlatformChange(platform.id) },
                        shape = SegmentedButtonDefaults.itemShape(onlinePlatforms.indexOf(platform), onlinePlatforms.size),
                        label = { Text(platform.displayName, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
        }

        when {
            query.isBlank() -> HintText("输入关键词开始搜索")
            allLocalEmpty -> {
                if (onlineFailed.isEmpty()) {
                    HintText("没有匹配的歌曲")
                } else {
                    HintText("在线搜索失败：${onlineFailed.joinToString("、")}")
                }
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                // 聚合模式下同时展示在线 + 本地，带切换开关
                if (currentOnlinePlatform == OnlineSearchRepository.ALL) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.spaceLg, vertical = dimens.spaceXs),
                            horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val toggle = "在线"
                            Text(toggle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            // 在线/本地切开关
                            val labels = listOf(toggle, "本地")
                            var active by remember { mutableStateOf(0) }
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                                labels.forEachIndexed { i, label ->
                                    SegmentedButton(
                                        selected = active == i,
                                        onClick = { active = i; showLocal = i == 1 },
                                        shape = SegmentedButtonDefaults.itemShape(i, labels.size),
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

                // 在线结果
                val onlineList = if (currentOnlinePlatform == OnlineSearchRepository.ALL) onlineResults
                    else onlineResults
                if (showLocal || currentOnlinePlatform != OnlineSearchRepository.ALL) {
                    item {
                        SectionHeader(
                            text = if (currentOnlinePlatform == OnlineSearchRepository.ALL) "在线结果（${onlineResults.size}）"
                                else "在线结果（${onlineResults.size}）",
                        )
                    }
                    items(onlineList, key = { it.id }) { song ->
                        SearchResultRow(song = song, onClick = { onSongClick(song) })
                    }
                }

                // 本地结果
                if (localResults.isNotEmpty()) {
                    item {
                        SectionHeader("本地曲库（${localResults.size}）")
                    }
                    items(localResults, key = { it.id }) { song ->
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

@Composable
private fun SearchResultRow(song: Song, onClick: () -> Unit) {
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