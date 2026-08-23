package com.wxjxpp.neiro.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.ui.theme.AppTheme

/**
 * 播放列表面板。
 *
 * 每行支持：点击播放、收藏（心形）、下载在线歌曲。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<Song>,
    currentSongId: String?,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
    /** 单曲下载回调（null = 不显示下载按钮）。 */
    onDownload: ((Song) -> Unit)? = null,
    downloadingIds: Set<String> = emptySet(),
    favoriteIds: Set<String> = emptySet(),
    onToggleFavorite: ((Song) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val dimens = AppTheme.dimens

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            text = "播放列表 · ${queue.size} 首",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm),
        )
        if (queue.isEmpty()) {
            Text(
                text = "队列为空",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(dimens.spaceLg),
            )
            return@ModalBottomSheet
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            itemsIndexed(queue, key = { _, song -> song.id }) { index, song ->
                val isCurrent = song.id == currentSongId
                val isFavorite = song.id in favoriteIds
                val isDownloading = song.id in downloadingIds
                ListItem(
                    modifier = Modifier.clickable { onPick(index) },
                    colors = if (isCurrent) {
                        ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        )
                    } else {
                        ListItemDefaults.colors()
                    },
                    leadingContent = {
                        if (isCurrent) {
                            Icon(
                                Icons.Rounded.GraphicEq,
                                contentDescription = "正在播放",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    headlineContent = {
                        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(
                            text = "${song.artistName} · ${song.albumTitle}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        Row {
                            if (onToggleFavorite != null) {
                                IconButton(onClick = { onToggleFavorite(song) }) {
                                    Icon(
                                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = if (isFavorite) "取消收藏" else "收藏",
                                        tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (onDownload != null && song.location is com.wxjxpp.neiro.core.model.MediaLocation.Remote) {
                                if (isDownloading) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.padding(all = 14.dp).size(20.dp),
                                    )
                                } else {
                                    IconButton(onClick = { onDownload(song) }) {
                                        Icon(
                                            Icons.Rounded.Download,
                                            contentDescription = "下载",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}