package com.wxjxpp.neiro.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.wxjxpp.neiro.core.model.Playlist
import com.wxjxpp.neiro.core.model.Song
import androidx.compose.ui.unit.dp
import com.wxjxpp.neiro.ui.theme.AppTheme

/**
 * 批量操作弹层：上半区把选中歌曲连续加入某个歌单，下半区连续收藏。
 * 歌单可现场新建；操作完成后不清空选择，方便用户连续对不同歌单操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SongBatchSheet(
    playlists: List<Playlist>,
    songs: List<Song>,
    favoriteIds: Set<String>,
    onDismiss: () -> Unit,
    onAddToPlaylist: (playlistId: String, songs: List<Song>) -> Unit,
    onCreatePlaylistAndAdd: (name: String, songs: List<Song>) -> Unit,
    onFavorite: (List<Song>) -> Unit,
) {
    val dimens = AppTheme.dimens
    var newPlaylistName by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = dimens.spaceLg)) {
            Text(
                "已选 ${songs.size} 首 · 批量操作",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(dimens.spaceSm))
            Text(
                "加入歌单（可连续添加到多个歌单）",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(dimens.spaceXs))
            LazyColumn(modifier = Modifier.height(180.dp)) {
                items(playlists, key = { it.id }) { pl ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onAddToPlaylist(pl.id, songs)
                            }
                            .padding(vertical = dimens.spaceSm),
                    ) {
                        Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
                        Spacer(Modifier.height(dimens.spaceXs))
                        Text(
                            "${pl.name}（${pl.songIds.size} 首）",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = dimens.spaceSm),
                        )
                    }
                    HorizontalDivider()
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("新建歌单并加入") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreatePlaylistAndAdd(newPlaylistName.trim(), songs)
                            newPlaylistName = ""
                        }
                    },
                    modifier = Modifier.padding(start = dimens.spaceSm),
                ) { Text("创建") }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = dimens.spaceMd))
            FilledTonalButton(onClick = { onFavorite(songs) }) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Text(
                    if (songs.all { it.id in favoriteIds }) "全部已收藏（再点可去重收藏）"
                    else "收藏这 ${songs.size} 首",
                    modifier = Modifier.padding(start = dimens.spaceXs),
                )
            }
            Spacer(Modifier.height(dimens.spaceXl))
        }
    }
}