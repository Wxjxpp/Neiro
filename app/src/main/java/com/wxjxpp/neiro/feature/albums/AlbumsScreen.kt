package com.wxjxpp.neiro.feature.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateBottomPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.ui.theme.AppTheme

/** 专辑聚合视图。 */
data class AlbumEntry(
    val title: String,
    val artistName: String,
    val coverUri: String?,
    val songCount: Int,
    val songs: List<Song>,
)

/**
 * 专辑墙：双列网格。
 *
 * 点击专辑 = 从第一首开始播放整张专辑（队列即该专辑曲目）。
 * 顶栏支持调整排序方式（标题 / 艺术家 / 歌曲数）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    songs: List<Song>,
    sortField: AlbumSortField,
    sortDescending: Boolean,
    onSortFieldChange: (AlbumSortField) -> Unit,
    onSortDirectionToggle: () -> Unit,
    onOpenDrawer: () -> Unit,
    onPlayAlbum: (AlbumEntry) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val albums = remember(songs, sortField, sortDescending) {
        groupAlbums(songs, sortField, sortDescending)
    }
    Column(modifier = modifier.fillMaxSize()) {
        AlbumsTopBar(
            onOpenDrawer = onOpenDrawer,
            sortField = sortField,
            sortDescending = sortDescending,
            onSortFieldChange = onSortFieldChange,
            onSortDirectionToggle = onSortDirectionToggle,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(
                start = AppTheme.dimens.spaceMd,
                end = AppTheme.dimens.spaceMd,
                top = AppTheme.dimens.spaceSm,
                bottom = contentPadding.calculateBottomPadding(),
            ),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceMd),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceLg),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(albums, key = { "${it.title}|${it.artistName}" }) { album ->
                AlbumCard(album = album, onClick = { onPlayAlbum(album) })
            }
        }
    }
}

/** 专辑排序字段。 */
enum class AlbumSortField(val displayName: String) {
    Title("专辑名"),
    Artist("艺术家"),
    Count("歌曲数"),
}

internal fun groupAlbums(
    songs: List<Song>,
    sortField: AlbumSortField,
    descending: Boolean,
): List<AlbumEntry> {
    val grouped = songs.groupBy { it.albumTitle to it.artistName }
        .map { (key, list) ->
            AlbumEntry(
                title = key.first,
                artistName = key.second,
                coverUri = list.firstOrNull { !it.coverUri.isNullOrBlank() }?.coverUri,
                songCount = list.size,
                songs = list.sortedBy { it.trackNumber ?: Int.MAX_VALUE },
            )
        }
    val sorted = when (sortField) {
        // Collator 需要非空接收者，null 值先排到最前/最后再比较
        AlbumSortField.Title -> grouped.sortedWith(
            compareBy(java.text.Collator.getInstance()) { it.title.ifBlank { "\uFFFF" } },
        )
        AlbumSortField.Artist -> grouped.sortedWith(
            compareBy(java.text.Collator.getInstance()) { it.artistName.ifBlank { "\uFFFF" } },
        )
        AlbumSortField.Count -> grouped.sortedByDescending { it.songCount }
    }
    return if (descending) sorted.asReversed() else sorted
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumsTopBar(
    onOpenDrawer: () -> Unit,
    sortField: AlbumSortField,
    sortDescending: Boolean,
    onSortFieldChange: (AlbumSortField) -> Unit,
    onSortDirectionToggle: () -> Unit,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Album, contentDescription = null)
                Text(
                    text = "专辑",
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
            Box {
                IconButton(onClick = { showSortMenu = !showSortMenu }) {
                    Icon(Icons.Rounded.Sort, contentDescription = "排序方式")
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    AlbumSortField.entries.forEach { field ->
                        DropdownMenuItem(
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
                    HorizontalDividerCompat()
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (sortDescending) "当前：倒序" else "当前：正序",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        onClick = onSortDirectionToggle,
                    )
                }
            }
        },
    )
}

@Composable
private fun HorizontalDividerCompat() {
    androidx.compose.material3.HorizontalDivider()
}

@Composable
private fun AlbumCard(album: AlbumEntry, onClick: () -> Unit) {
    val dimens = AppTheme.dimens
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(dimens.spaceXs),
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (album.coverUri != null) {
                    AsyncImage(
                        model = album.coverUri,
                        contentDescription = album.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Rounded.Album,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxSize().padding(dimens.spaceXl),
                    )
                }
                // 悬浮播放角标：暗示点击行为是播放整张专辑
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(dimens.spaceSm),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = dimens.spaceSm, end = dimens.spaceXs),
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "${album.songCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = dimens.spaceXs),
        )
        Text(
            text = album.artistName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}