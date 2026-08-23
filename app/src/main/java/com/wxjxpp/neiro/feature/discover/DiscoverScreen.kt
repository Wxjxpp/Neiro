package com.wxjxpp.neiro.feature.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateBottomPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wxjxpp.neiro.core.discover.DiscoverRepository
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.ui.components.SongCover
import com.wxjxpp.neiro.ui.theme.AppTheme

/**
 * 发现页：从一个稳定源（网易云公开榜单）获取内容，
 * 歌曲播放全部走用户导入的音源（自动换源回退）。
 *
 * 区块：猜你喜欢（个性化种子）→ 热歌 → 新歌 → 飙升 → 原创。
 * 每个区块支持"全部播放"；单首点击即插播。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DiscoverScreen(
    sections: List<DiscoverRepository.Section>,
    isLoading: Boolean,
    onOpenDrawer: () -> Unit,
    onSongClick: (Song) -> Unit,
    onPlaySection: (DiscoverRepository.Section) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Explore, contentDescription = null)
                    Text(
                        text = "发现",
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
        )
        when {
            isLoading && sections.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularWavyProgressIndicator()
            }

            sections.isEmpty() -> DiscoverEmpty()
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            ) {
                sections.forEach { section ->
                    item(key = "header_${section.id}") {
                        SectionHeader(
                            title = section.title,
                            subtitle = section.subtitle,
                            onPlayAll = { onPlaySection(section) },
                        )
                    }
                    item(key = "list_${section.id}") {
                        // 横向滚动卡片：紧凑展示，不占纵向空间
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = AppTheme.dimens.spaceLg),
                            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceMd),
                        ) {
                            items(section.songs, key = { "${section.id}_${it.id}" }) { song ->
                                SongCard(song = song, onClick = { onSongClick(song) })
                            }
                        }
                    }
                    item(key = "gap_${section.id}") {
                        Spacer(Modifier.height(AppTheme.dimens.spaceMd))
                    }
                }
            }
        }
    }
}

/** 无可用内容（通常是未导入音源导致搜索委托不可用）。 */
@Composable
private fun DiscoverEmpty() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppTheme.dimens.spaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("暂时拉取不到榜单数据", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(AppTheme.dimens.spaceSm))
        Text(
            text = "请检查网络连接后下拉重试；\n歌曲播放需要已导入的自定义音源。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    onPlayAll: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AppTheme.dimens.spaceLg, top = AppTheme.dimens.spaceMd, bottom = AppTheme.dimens.spaceSm)
            .padding(end = AppTheme.dimens.spaceSm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(onClick = onPlayAll) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("播放", modifier = Modifier.padding(start = AppTheme.dimens.spaceXs))
        }
    }
}

/** 发现页歌曲卡片：竖向封面 + 双行文字，固定宽度。 */
@Composable
private fun SongCard(song: Song, onClick: () -> Unit) {
    val dimens = AppTheme.dimens
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(dimens.spaceXs),
    ) {
        SongCover(
            song = song,
            size = 124.dp,
            radius = 12.dp,
        )
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = dimens.spaceXs),
        )
        Text(
            text = song.artistName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}