package com.wxjxpp.neiro.feature.discover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wxjxpp.neiro.core.discover.DiscoverRepository
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.ui.components.SongCover
import com.wxjxpp.neiro.ui.theme.AppTheme

/**
 * 发现页。
 *
 * 一级：榜单卡片横向预览（每榜前 20 首）；
 * 二级：点榜单标题进入该榜完整曲目（最近 50 首），支持整榜播放。
 *
 * 歌曲元数据来自公开榜单接口；播放一律走用户导入的自定义音源，
 * 未导入音源时点击播放会得到明确提示。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DiscoverScreen(
    sections: List<DiscoverRepository.Section>,
    isLoading: Boolean,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    detailId: String?,
    detailSongs: List<Song>,
    isDetailLoading: Boolean,
    toplists: List<DiscoverRepository.ToplistRef>,
    onOpenDrawer: () -> Unit,
    onSongClick: (Song) -> Unit,
    onOpenDetail: (String) -> Unit,
    onCloseDetail: () -> Unit,
    onPlayList: (List<Song>) -> Unit,
    /** 打开搜索页（顶栏搜索入口）。 */
    onOpenSearch: () -> Unit = {},
    favoriteIds: Set<String> = emptySet(),
    downloadingIds: Set<String> = emptySet(),
    onDownloadSong: ((Song) -> Unit)? = null,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val inDetail = detailId != null
    // 二级菜单返回手势：先回一级，再走外壳的页面级返回
    BackHandler(enabled = inDetail) { onCloseDetail() }
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
                IconButton(onClick = { if (inDetail) onCloseDetail() else onOpenDrawer() }) {
                    Icon(
                        if (inDetail) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Menu,
                        contentDescription = if (inDetail) "返回" else "打开导航",
                    )
                }
            },
            actions = {
                if (!inDetail) {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Rounded.Search, contentDescription = "搜索")
                    }
                }
            },
        )
        when {
            inDetail -> DiscoverDetail(
                title = toplists.firstOrNull { it.id == detailId }?.name.orEmpty(),
                subtitle = toplists.firstOrNull { it.id == detailId }?.updateFreq.orEmpty(),
                songs = detailSongs,
                isLoading = isDetailLoading,
                onPlayAll = { onPlayList(detailSongs) },
                onSongClick = onSongClick,
                favoriteIds = favoriteIds,
                downloadingIds = downloadingIds,
                onDownloadSong = onDownloadSong,
                contentPadding = contentPadding,
            )
            isLoading && sections.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularWavyProgressIndicator()
            }
            sections.isEmpty() -> DiscoverEmpty()
            else -> PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    // MD3 Expressive 波浪指示器：与歌曲页同款，避免混搭
                    androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.LoadingIndicator(
                        state = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState(),
                        isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                },
            ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            ) {
                // 时段问候语
                item(key = "greeting") {
                    Text(
                        text = greetingText(),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = AppTheme.dimens.spaceLg, vertical = AppTheme.dimens.spaceSm),
                    )
                }
                // 榜单快捷入口（横向胶囊）
                if (toplists.isNotEmpty()) {
                    item(key = "quick_toplists") {
                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = PaddingValues(horizontal = AppTheme.dimens.spaceLg),
                            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceXs),
                        ) {
                            androidx.compose.foundation.lazy.items(toplists) { ref ->
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.clickable { onOpenDetail(ref.id) },
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(
                                            horizontal = AppTheme.dimens.spaceMd,
                                            vertical = 8.dp,
                                        ),
                                    ) {
                                        Icon(
                                            Icons.Rounded.Explore,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = ref.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(start = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // 当日最热：热歌榜第 1 名大图卡（文字在图上，底部渐变保证可读性）
                val hotFirst = sections.firstOrNull { it.id == toplists.firstOrNull()?.id }?.songs?.firstOrNull()
                if (hotFirst != null) {
                    item(key = "daily_hot") {
                        DailyHotCard(song = hotFirst, onClick = { onSongClick(hotFirst) })
                    }
                }
                sections.forEach { section ->
                    item(key = "header_${section.id}") {
                        SectionHeader(
                            title = section.title,
                            subtitle = section.subtitle,
                            onOpen = { onOpenDetail(section.id) },
                            onPlayAll = { onPlayList(section.songs) },
                        )
                    }
                    item(key = "list_${section.id}") {
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
}

/** 二级菜单：单个榜单的完整曲目（最近 50 首）。 */
@Composable
private fun DiscoverDetail(
    title: String,
    subtitle: String,
    songs: List<Song>,
    isLoading: Boolean,
    onPlayAll: () -> Unit,
    onSongClick: (Song) -> Unit,
    favoriteIds: Set<String>,
    downloadingIds: Set<String>,
    onDownloadSong: ((Song) -> Unit)?,
    contentPadding: PaddingValues,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.dimens.spaceLg, vertical = AppTheme.dimens.spaceXs),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onPlayAll, enabled = songs.isNotEmpty()) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("播放全榜", modifier = Modifier.padding(start = AppTheme.dimens.spaceXs))
            }
        }
        when {
            isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator()
            }
            songs.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂时拉取不到该榜单", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            ) {
                items(songs, key = { it.id }) { song ->
                    val rowDownload: (() -> Unit)? = onDownloadSong
                        ?.takeIf { song.location is com.wxjxpp.neiro.core.model.MediaLocation.Remote }
                        ?.let { fn -> { fn(song) } }
                    SongRowDetailed(
                        song = song,
                        onClick = { onSongClick(song) },
                        isFavorite = song.id in favoriteIds,
                        isDownloading = song.id in downloadingIds,
                        onDownload = rowDownload,
                    )
                }
            }
        }
    }
}

@Composable
private fun SongRowDetailed(
    song: Song,
    onClick: () -> Unit,
    isFavorite: Boolean,
    isDownloading: Boolean,
    onDownload: (() -> Unit)?,
) {
    val dimens = AppTheme.dimens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceXs),
    ) {
        Text(
            text = if (isFavorite) "♥" else "",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(end = dimens.spaceXs),
        )
        SongCover(song = song, size = dimens.listCoverSize, radius = 8.dp)
        Column(modifier = Modifier.weight(1f).padding(start = dimens.spaceMd)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                song.artistName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isDownloading) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else if (onDownload != null) {
            IconButton(onClick = onDownload) {
                Icon(Icons.Rounded.Download, contentDescription = "下载", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 无可用内容（网络异常等）。 */
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

/** 一级区块头：点标题进入二级菜单，播放按钮播当前预览。 */
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    onOpen: () -> Unit,
    onPlayAll: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = AppTheme.dimens.spaceLg, top = AppTheme.dimens.spaceMd, bottom = AppTheme.dimens.spaceSm)
            .padding(end = AppTheme.dimens.spaceSm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "$subtitle · 查看全部 ›",
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

/** 按当前时段返回问候语。 */
internal fun greetingText(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..10 -> "早上好"
        in 11..12 -> "中午好"
        in 13..17 -> "下午好"
        else -> "晚上好"
    }
}

/**
 * 当日最热大图卡：单张大图 + 文字直接写在图片上。
 *
 * 可读性保障：底部到顶部叠加黑色渐变（0.72 → 透明），文字永远压在渐变最深处，
 * 与封面颜色无关都能看清。
 */
@Composable
private fun DailyHotCard(song: Song, onClick: () -> Unit) {
    val dimens = AppTheme.dimens
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm)
            .height(190.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        SongCover(
            song = song,
            size = 999.dp,
            radius = 0.dp,
            modifier = Modifier.fillMaxSize(),
        )
        // 底部黑色渐变：保证任何封面色上文字都可读
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.05f),
                        0.45f to Color.Black.copy(alpha = 0.25f),
                        1f to Color.Black.copy(alpha = 0.78f),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = "今日最热",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "${song.artistName} · ${song.albumTitle}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}