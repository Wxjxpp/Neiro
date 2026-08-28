package com.wxjxpp.neiro.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.ui.theme.AppTheme

/**
 * 播放列表面板。
 *
 * 每行支持：点击播放、收藏（心形）、下载在线歌曲。
 *
 * ## 配色（v8 修复「一团黑」）
 *
 * 播放页运行在**沉浸配色**下：那是一份由封面取色派生的 `darkColorScheme().copy(...)`，
 * 只覆盖了 `background / surface / onSurface / onSurfaceVariant / surfaceVariant` 等
 * 少数角色。像 `surfaceContainerLow`、`secondaryContainer` 这类**没被覆盖**的角色
 * 仍是 `darkColorScheme()` 的默认深色值。
 *
 * 旧实现把 Sheet 背景设成 `surfaceContainerLow`（深色默认值），而 `ListItem` 的
 * 文字取自被覆盖过的 `onSurface` —— 浅色封面时 onSurface 是接近黑的深色。
 * 深底 + 深字 = 截图里那块「一团黑」。
 *
 * 现在所有颜色都显式取自**同一组已被沉浸配色覆盖的角色**：
 * 容器用 `surface`，文字用 `onSurface` / `onSurfaceVariant`，
 * 当前行高亮用 `primary` 的低透明度叠加（而不是未覆盖的 secondaryContainer）。
 * 这样无论画布是深还是浅，前景背景永远来自同一套明暗决策，不可能互相吞掉。
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
    val scheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // 必须用 surface：它一定被沉浸配色覆盖过，与 onSurface 成对匹配
        containerColor = scheme.surface,
        contentColor = scheme.onSurface,
        tonalElevation = 0.dp,
        // 拖拽把手也要跟随前景色，否则浅画布上会是一条看不见的深色横杠
        dragHandle = {
            androidx.compose.material3.BottomSheetDefaults.DragHandle(
                color = scheme.onSurface.copy(alpha = 0.4f),
            )
        },
    ) {
        Text(
            text = "播放列表 · ${queue.size} 首",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
            modifier = Modifier.padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm),
        )
        if (queue.isEmpty()) {
            Text(
                text = "队列为空",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(dimens.spaceLg),
            )
            return@ModalBottomSheet
        }
        // 打开时自动定位到当前播放歌曲
        val listState = rememberLazyListState()
        LaunchedEffect(currentSongId, queue.size) {
            val index = queue.indexOfFirst { it.id == currentSongId }
            if (index > 0) listState.scrollToItem(index)
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
            state = listState,
        ) {
            itemsIndexed(queue, key = { _, song -> song.id }) { index, song ->
                val isCurrent = song.id == currentSongId
                val isFavorite = song.id in favoriteIds
                val isDownloading = song.id in downloadingIds
                ListItem(
                    modifier = Modifier.clickable { onPick(index) },
                    // 当前行：primary 低透明度叠加。primary 已被沉浸配色设为 accent，
                    // 与画布必有足够对比（见 PlayerDetailScreen 的 accentColor 推导）。
                    colors = ListItemDefaults.colors(
                        containerColor = if (isCurrent) {
                            scheme.primary.copy(alpha = 0.16f)
                        } else {
                            Color.Transparent
                        },
                        headlineColor = if (isCurrent) scheme.primary else scheme.onSurface,
                        supportingColor = scheme.onSurfaceVariant,
                        leadingIconColor = scheme.onSurfaceVariant,
                        trailingIconColor = scheme.onSurfaceVariant,
                    ),
                    leadingContent = {
                        if (isCurrent) {
                            Icon(
                                Icons.Rounded.GraphicEq,
                                contentDescription = "正在播放",
                                tint = scheme.primary,
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant,
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
                                        tint = if (isFavorite) scheme.error else scheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (onDownload != null && song.location is com.wxjxpp.neiro.core.model.MediaLocation.Remote) {
                                if (isDownloading) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = scheme.primary,
                                        modifier = Modifier.padding(all = 14.dp).size(20.dp),
                                    )
                                } else {
                                    IconButton(onClick = { onDownload(song) }) {
                                        Icon(
                                            Icons.Rounded.Download,
                                            contentDescription = "下载",
                                            tint = scheme.onSurfaceVariant,
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