package com.wxjxpp.neiro.feature.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.ui.theme.AppTheme

/**
 * 喜爱页：本地收藏夹（在线/本地歌曲均可），入口在侧边栏。
 *
 * - 点击播放；心形按钮取消收藏；在线歌曲带下载入口
 */
@Composable
fun FavoritesScreen(
    songs: List<Song>,
    downloadingIds: Set<String> = emptySet(),
    onOpenDrawer: () -> Unit,
    onSongClick: (Song) -> Unit,
    onRemoveFavorite: (Song) -> Unit,
    onDownloadSong: ((Song) -> Unit)? = null,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val dimens = AppTheme.dimens
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = WindowInsets(0),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null)
                    Text(
                        text = "喜爱",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = dimens.spaceSm),
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Rounded.Menu, contentDescription = "打开导航")
                }
            },
            actions = {
                Text(
                    text = "${songs.size} 首",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = dimens.spaceLg),
                )
            },
        )
        if (songs.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = dimens.spaceXl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "还没有喜欢的歌曲",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = dimens.spaceMd),
                )
                Text(
                    text = "在队列、发现页或播放页点♥收藏",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
        ) {
            items(songs, key = { it.id }) { song ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSongClick(song) }
                        .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm),
                ) {
                    Text(
                        text = "♥",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = dimens.spaceXs)) {
                        Text(
                            song.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            song.artistName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (song.id in downloadingIds) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else if (onDownloadSong != null &&
                        song.location is com.wxjxpp.neiro.core.model.MediaLocation.Remote
                    ) {
                        IconButton(onClick = { onDownloadSong(song) }) {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = "下载",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { onRemoveFavorite(song) }) {
                        Icon(
                            Icons.Rounded.Favorite,
                            contentDescription = "取消收藏",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
