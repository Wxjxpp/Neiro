@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
)

package com.wxjxpp.neiro.feature.albums

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.ui.components.LocalSharedTransitionScope
import com.wxjxpp.neiro.ui.theme.AppTheme

/** 专辑聚合视图。 */
data class AlbumEntry(
    val title: String,
    val artistName: String,
    val coverUri: String?,
    val songCount: Int,
    val songs: List<Song>,
)

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

/**
 * 专辑墙。
 *
 * 点击专辑 → 二级菜单展示该专辑名下的全部歌曲（本地曲库、收藏夹、所有歌单，
 * 按歌曲 id 去重）。原"点图直接播放"已移除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    songs: List<Song>,
    extraSongs: List<Song> = emptyList(),
    favoriteIds: Set<String> = emptySet(),
    downloadingIds: Set<String> = emptySet(),
    onDownloadSong: ((Song) -> Unit)? = null,
    sortField: AlbumSortField,
    sortDescending: Boolean,
    onSortFieldChange: (AlbumSortField) -> Unit,
    onSortDirectionToggle: () -> Unit,
    onOpenDrawer: () -> Unit,
    onPlayAlbum: (AlbumEntry) -> Unit = {},
    /** 二级菜单中点击某首歌的回调。 */
    onSongClick: (Song) -> Unit = {},
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var opened by remember { mutableStateOf<AlbumEntry?>(null) }
    BackHandler(enabled = opened != null) { opened = null }
    // Container Transform：网格 → 详情，专辑封面作为共享元素飞入详情头部。
    // 曲线用 MD3E motionScheme 的空间规格（expressive 弹簧），符合 Transition Choreography
    val morphSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    AnimatedContent(
        targetState = opened,
        label = "albumContainer",
        transitionSpec = {
            fadeIn(morphSpec) togetherWith fadeOut(morphSpec)
        },
        modifier = modifier,
    ) { current ->
        // Container Transform 的动画作用域必须是本 AnimatedContent（而非外层路由），
        // 否则共享元素匹配不到活动转场，退化为普通淡入淡出
        val animScope = this
        val sts = LocalSharedTransitionScope.current
        if (current != null) {
            val albumKey = "${current.title}|${current.artistName}"
            // 详情整页作为"容器"端：与被点击卡片同 key，sharedBounds 驱动容器变形
            val containerModifier = if (sts != null) {
                with(sts) {
                    Modifier.sharedBounds(
                        rememberSharedContentState(key = "album_container_$albumKey"),
                        animatedVisibilityScope = animScope,
                    )
                }
            } else {
                Modifier
            }
            Column(modifier = containerModifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(current.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = { opened = null }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    },
                )
                AlbumDetailList(
                    album = current,
                    songs = current.songs,
                    extraSongs = extraSongs,
                    onSongClick = onSongClick,
                    favoriteIds = favoriteIds,
                    downloadingIds = downloadingIds,
                    onDownloadSong = onDownloadSong,
                    contentPadding = contentPadding,
                    sharedKey = "album_cover_$albumKey",
                    animScope = animScope,
                )
            }
        } else {
            val albums = remember(songs, sortField, sortDescending) {
                groupAlbums(songs, sortField, sortDescending)
            }
            Column(modifier = Modifier.fillMaxSize()) {
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
                    AlbumCard(
                        album = album,
                        onClick = { opened = album },
                        sharedKey = "album_cover_${album.title}|${album.artistName}",
                        animScope = animScope,
                    )
                }
            }
            }
        }
    }
}

/** 专辑详情：头部信息（封面/歌手/总时长）+ 本专辑曲目（本地）+ 收藏/歌单中同专辑名的歌曲（去重），支持播放。 */
@Composable
private fun AlbumDetailList(
    album: AlbumEntry,
    songs: List<Song>,
    extraSongs: List<Song>,
    onSongClick: (Song) -> Unit,
    favoriteIds: Set<String>,
    downloadingIds: Set<String>,
    onDownloadSong: ((Song) -> Unit)?,
    contentPadding: PaddingValues,
    sharedKey: String? = null,
    animScope: AnimatedVisibilityScope? = null,
) {
    val merged = remember(songs, extraSongs) {
        buildList {
            addAll(songs)
            val seen = songs.mapTo(mutableSetOf()) { it.id }
            extraSongs.filter { it.id !in seen }.forEach { extra ->
                if (extra.albumTitle in songs.map { it.albumTitle }) add(extra)
            }
        }
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val headerHeight = 280.dp
    val titleBlockHeight = 80.dp
    val maxOffsetPx = with(density) { headerHeight.toPx() }
    val listState = rememberLazyListState()
    // 收缩进度 = 首个可见 item 的累计滚动偏移 / 最大偏移；
    // 用户指定：progress 改为带阻尼的缓动（spring 无回弹），收缩过程更自然
    val rawProgress = if (maxOffsetPx > 0f) {
        (listState.firstVisibleItemIndex * maxOffsetPx +
            listState.firstVisibleItemScrollOffset) / maxOffsetPx
    } else {
        0f
    }
    val progress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "albumCollapseProgress",
    )
    // 布局结构（用户规格）：Box + LazyColumn 占位撑开 + 顶部固定覆盖层
    // 底色改走主题（原来硬编码 Color.Black，浅色主题下就是一整块黑）
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
        ) {
            item(key = "header_spacer") {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerHeight + titleBlockHeight),
                )
            }
            lazyItems(merged, key = { it.id }) { song ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSongClick(song) }
                    .padding(horizontal = AppTheme.dimens.spaceLg, vertical = AppTheme.dimens.spaceSm),
            ) {
                Text(
                    text = if (song.id in favoriteIds) "♥" else "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
                Column(modifier = Modifier.weight(1f).padding(start = AppTheme.dimens.spaceXs)) {
                    Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        Icon(Icons.Rounded.Download, contentDescription = "下载", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider()
        }
        }
        AlbumCollapsingHeader(
            entry = album,
            songCount = merged.size,
            sharedKey = sharedKey,
            animScope = animScope,
            progress = progress,
        )
    }
}

/** 专辑详情头部：滚动时封面从大图收缩为顶部胶囊，标题上滑合并（用户指定交互）。 */
@Composable
private fun AlbumCollapsingHeader(
    entry: AlbumEntry,
    songCount: Int,
    sharedKey: String?,
    animScope: AnimatedVisibilityScope?,
    progress: Float,
) {
    val dimens = AppTheme.dimens
    val totalMs = entry.songs.sumOf { it.durationMs }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val headerHeight = 280.dp
    val collapsedHeight = 56.dp
    val titleBlockHeight = 80.dp
    val maxOffsetPx = with(density) { headerHeight.toPx() }
    // 高度 280→56、圆角 0→28（胶囊）；标题块自下方上滑并淡出，字号 24→16
    val height = lerp(headerHeight, collapsedHeight, progress)
    val cornerRadius = lerp(0.dp, 28.dp, progress)
    val shape = RoundedCornerShape(cornerRadius)
    val titleOffsetPx = with(density) { titleBlockHeight.toPx() * (1f - progress) }
    val titleAlpha = (1f - progress * 1.15f).coerceIn(0f, 1f)
    val sts = LocalSharedTransitionScope.current
    Box(modifier = Modifier.fillMaxSize()) {
        // 封面容器：随 progress 收缩为顶部胶囊。
        // 底色走主题 surfaceVariant——原来硬编码 Color.Black，封面未加载/无封面时
        // 就是用户看到的"大黑块"。
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .height(height)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val baseModifier = Modifier.fillMaxSize()
            if (sts != null && animScope != null && sharedKey != null) {
                with(sts) {
                    AsyncImage(
                        model = entry.coverUri,
                        contentDescription = entry.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .sharedElement(
                                rememberSharedContentState(key = sharedKey),
                                animatedVisibilityScope = animScope,
                            )
                            .then(baseModifier),
                    )
                }
            } else {
                AsyncImage(
                    model = entry.coverUri,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = baseModifier,
                )
            }
        }
        // 标题区：位于图片下方占位区，滚动时上滑与图片合并、渐隐、缩小（24sp→16sp）
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .graphicsLayer { translationY = titleOffsetPx; alpha = titleAlpha }
                .fillMaxWidth()
                .padding(top = headerHeight + dimens.spaceSm),
        ) {
            Text(
                text = entry.title,
                fontSize = lerp(24.sp, 16.sp, progress),
                fontWeight = FontWeight.Bold,
                // 标题位于列表背景之上，随主题走（原硬编码白色在浅色主题下不可读）
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${entry.artistName.ifBlank { "未知歌手" }} · $songCount 首 · ${formatTotalDuration(totalMs)}",
                fontSize = lerp(14.sp, 12.sp, progress),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 毫秒 → 「X时Y分」/「Y分钟」。 */
private fun formatTotalDuration(ms: Long): String {
    val minutes = ms / 60000
    return when {
        minutes >= 60 -> "${minutes / 60} 时 ${minutes % 60} 分"
        minutes > 0 -> "$minutes 分钟"
        else -> "时长未知"
    }
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
        windowInsets = WindowInsets(0),
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
                    HorizontalDivider()
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
private fun AlbumCard(
    album: AlbumEntry,
    onClick: () -> Unit,
    sharedKey: String? = null,
    animScope: AnimatedVisibilityScope? = null,
) {
    val dimens = AppTheme.dimens
    val sts = LocalSharedTransitionScope.current
    // 卡片整体作为"容器"起点：与详情整页同 key，sharedBounds 驱动容器变形
    val containerModifier = if (sts != null && animScope != null) {
        with(sts) {
            Modifier.sharedBounds(
                rememberSharedContentState(key = "album_container_${album.title}|${album.artistName}"),
                animatedVisibilityScope = animScope,
            )
        }
    } else {
        Modifier
    }
    Column(
        modifier = containerModifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(dimens.spaceXs),
    ) {
        val imageModifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
        if (sts != null && animScope != null && sharedKey != null) {
            with(sts) {
                AsyncImage(
                    model = album.coverUri,
                    contentDescription = album.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .sharedElement(
                            rememberSharedContentState(key = sharedKey),
                            animatedVisibilityScope = animScope,
                        )
                        .then(imageModifier),
                )
            }
        } else {
            AsyncImage(
                model = album.coverUri,
                contentDescription = album.title,
                contentScale = ContentScale.Crop,
                modifier = imageModifier,
            )
        }
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = dimens.spaceXs),
        )
        Text(
            text = "${album.artistName} · ${album.songCount} 首",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}