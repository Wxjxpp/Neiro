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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.wxjxpp.musicplayer.core.model.Song
import com.wxjxpp.musicplayer.ui.components.SongCover
import com.wxjxpp.musicplayer.ui.theme.AppTheme

/**
 * 搜索页。
 *
 * 匹配范围：歌名、演唱者、专辑、发行日期、描述、标签
 * （字段定义见 core/search/SongSearch.kt）。
 */
@Composable
fun SearchScreen(
    query: String,
    results: List<Song>,
    onQueryChange: (String) -> Unit,
    onSongClick: (Song) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val dimens = AppTheme.dimens
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

        when {
            query.isBlank() -> HintText("输入关键词开始搜索")
            results.isEmpty() -> HintText("没有匹配的歌曲")
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(results, key = { it.id }) { song ->
                    SearchResultRow(song = song, onClick = { onSongClick(song) })
                }
            }
        }
    }
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