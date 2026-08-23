package com.wxjxpp.neiro.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateTopPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wxjxpp.neiro.core.model.Lyrics
import com.wxjxpp.neiro.core.model.MediaLocation
import com.wxjxpp.neiro.core.model.PlaybackState
import com.wxjxpp.neiro.core.model.Quality
import com.wxjxpp.neiro.core.model.RepeatMode
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.ui.components.AmbientGlowBackground
import com.wxjxpp.neiro.ui.components.SongCover
import com.wxjxpp.neiro.ui.theme.AppTheme

/**
 * 播放详情页（Sheet 形态）。
 *
 * 由连续的 [sheetProgress] 驱动（0=收起 1=播放页 2=歌词页）：
 * - 封面 ↔ 歌词的过渡不是页面切换，而是同一进度上的**连续变换**：
 *   进度 1→1.5 封面缩小上移淡出，1.5→2 歌词从下方滑入聚焦；
 *   反向拖拽完全对称。手指停在哪，画面就停在哪（跟手）。
 * - 封面定位：顶部约 25% 屏高，尺寸比常规放大 ~10%（用户指定比例）
 * - 歌词聚焦位：视口 33% 高度处（由 LyricsPane 内部处理）
 * - 手势层级：顶部区域下滑 = 整体收起；封面/标题上滑 = 进入歌词页；
 *   歌词页仅在小封面上做手势（不与歌词滚动冲突），返回按钮逐层回退。
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun PlayerDetailScreen(
    state: PlaybackState,
    lyrics: Lyrics,
    showTranslation: Boolean,
    lyricsOffsetMs: Long,
    ambientGlow: Boolean,
    queue: List<Song>,
    lyricsAlign: String = "center",
    springLyrics: Boolean = false,
    lyricsFontScale: Float = 1f,
    lyricsGapScale: Float = 1f,
    pureModeDefault: Boolean = false,
    /** Sheet 连续进度：0 收起 / 1 播放页 / 2 歌词页。 */
    sheetProgress: Float = 1f,
    onCollapseToPlayer: () -> Unit = {},
    onExpandLyrics: () -> Unit = {},
    /** 垂直拖拽增量回调（px，向上为负）：交给外壳换算进度。 */
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPickQueueItem: (Int) -> Unit,
    onLyricsOffsetChange: (Long) -> Unit,
    onMatchLyrics: () -> Unit = {},
    onToggleTranslation: () -> Unit = {},
    onSpeedChange: (Float) -> Unit = {},
    currentQuality: Quality = Quality.Standard,
    onQualityChange: (Quality) -> Unit = {},
) {
    val song = state.current ?: return
    val dimens = AppTheme.dimens
    var dragging by remember { mutableStateOf(false) }
    // 翻译显示开关：默认开启，可在播放页直接切换（同时通知设置持久化）
    var translationOn by remember(showTranslation) { mutableStateOf(showTranslation) }
    var pureModeOverride by remember { mutableStateOf<Boolean?>(null) }
    val pureMode = pureModeOverride ?: pureModeDefault
    var showQueue by remember { mutableStateOf(false) }
    var showOffsetPanel by remember { mutableStateOf(false) }
    var showQualityPicker by remember { mutableStateOf(false) }
    val isRemoteSong = song.location is MediaLocation.Remote
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()

    // 播放页(0..1) 与 歌词段(1..2) 的局部进度，用于连续变换
    val toPlayer = sheetProgress.coerceIn(0f, 1f)
    val lyricPhase = ((sheetProgress - 1f) / 1f).coerceIn(0f, 1f)
    // 歌词模式判定阈值（内容切换在过半时发生，避免中途闪烁）
    val lyricsMode = lyricPhase > 0.5f

    Box(modifier = Modifier.fillMaxSize()) {
        // 动态流光背景（封面位图铺底 + 光斑漂移）
        AmbientGlowBackground(
            baseColor = Color(song.coverSeedColor),
            coverUri = song.coverUri,
            enabled = ambientGlow && !lyricsMode,
            modifier = Modifier.fillMaxSize(),
        )
        // 顶部安全区实心填充 + 下滑整体收起的手势区
        Spacer(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(statusBarPadding.calculateTopPadding() + 88.dp)
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.surface,
                        0.82f to MaterialTheme.colorScheme.surface,
                        1f to MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                    ),
                )
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                    ) { change, dragAmount ->
                        change.consume()
                        if (dragAmount.y > 0f) onDrag(dragAmount.y) // 下滑收起
                    }
                },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarPadding.calculateTopPadding()),
        ) {
            Spacer(Modifier.height(dimens.spaceSm))
            // 标题区：播放页居中大字；过渡到歌词页时缩小让位给小封面行
            if (!lyricsMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.spaceXl, vertical = dimens.spaceSm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artistName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.marqueeIfLong(),
                    )
                }
            } else {
                // 歌词页头部：左侧小封面（按住上滑回播放页）+ 右侧标题歌手
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = dimens.spaceXl, top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SongCover(
                        song = song,
                        size = 52.dp,
                        radius = 9.dp,
                        modifier = Modifier
                            // 仅这个小封面响应手势：上滑回播放页、下滑进歌词页，
                            // 其余区域全部留给歌词列表滚动
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = onDragEnd,
                                    onDragCancel = onDragEnd,
                                ) { change, dragAmount ->
                                    change.consume()
                                    if (dragAmount.y < -30f || dragAmount.y > 30f) {
                                        onDrag(dragAmount.y)
                                    }
                                }
                            },
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = song.artistName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // 主区域：封面与歌词在同一连续进度上的变换
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (!lyricsMode) {
                    // 封面：屏幕最佳位置——图片顶边约在屏高 25%，尺寸放大 ~10%
                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val density = LocalDensity.current
                    val coverTarget = (dimens.detailCoverSize * 1.10f)
                    val screenH = with(density) { configuration.screenHeightDp.dp.toPx() }
                    val coverTopOffset = with(density) { (screenH * 0.25f).toDp() } -
                        statusBarPadding.calculateTopPadding() - dimens.spaceSm
                    Box(modifier = Modifier.fillMaxSize()) {
                        SongCover(
                            song = song,
                            size = coverTarget,
                            radius = dimens.detailCoverRadius,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = coverTopOffset.coerceAtLeast(0.dp))
                                // 拖拽跟手：上滑进入歌词（连续），点击直接展开
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragEnd = onDragEnd,
                                        onDragCancel = onDragEnd,
                                    ) { change, dragAmount ->
                                        change.consume()
                                        if (dragAmount.y < 0f) {
                                            onExpandLyrics()
                                            onDrag(dragAmount.y)
                                        }
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { onExpandLyrics() })
                                },
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (lyrics.isEmpty) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = "没有找到歌词",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                androidx.compose.material3.TextButton(onClick = onMatchLyrics) {
                                    Text("从网络匹配歌词")
                                }
                            }
                        } else {
                            LyricsPane(
                                lyrics = lyrics,
                                positionMs = state.positionMs,
                                showTranslation = translationOn,
                                offsetMs = lyricsOffsetMs,
                                align = when (lyricsAlign) {
                                    "start" -> LyricsAlign.Start
                                    "end" -> LyricsAlign.End
                                    else -> LyricsAlign.Center
                                },
                                springAnimation = springLyrics,
                                fontScale = lyricsFontScale,
                                gapScale = lyricsGapScale,
                                onSeekTo = onSeekTo,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
        // ---- 控制台 ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                        0.12f to MaterialTheme.colorScheme.surface,
                        1f to MaterialTheme.colorScheme.surface,
                    ),
                ),
        ) {
            // 封面模式下：控制台上方显示当前歌词横幅
            if (!lyricsMode && !lyrics.isEmpty) {
                CurrentLineBanner(
                    lyrics = lyrics,
                    positionMs = state.positionMs,
                    offsetMs = lyricsOffsetMs,
                    showTranslation = showTranslation,
                    onClick = onExpandLyrics,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            var draggingProgress by remember(song.id) { mutableFloatStateOf(state.progress) }
            val shownProgress = if (dragging) draggingProgress else state.progress
            WavySeekBar(
                progress = shownProgress,
                animated = state.isPlaying && !dragging,
                onValueChange = {
                    draggingProgress = it
                    dragging = true
                },
                onValueChangeFinished = {
                    onSeekFraction(draggingProgress)
                    dragging = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spaceXl),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spaceXl),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatDuration(if (dragging) (state.durationMs * shownProgress).toLong() else state.positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatDuration(state.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 播放控制行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spaceXl, vertical = dimens.spaceSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (!pureMode) {
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(onLongPress = { onCycleRepeat() })
                        },
                    ) {
                        Icon(
                            imageVector = if (state.shuffle) Icons.Rounded.Shuffle else when (state.repeatMode) {
                                RepeatMode.One -> Icons.Rounded.RepeatOne
                                RepeatMode.All -> Icons.Rounded.Repeat
                                RepeatMode.Off -> Icons.Rounded.Shuffle
                            },
                            contentDescription = "随机/循环（长按切循环）",
                            tint = if (state.shuffle || state.repeatMode != RepeatMode.Off) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
                ) {
                    IconButton(onClick = onPrevious) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一首")
                    }
                    IconButton(
                        onClick = {
                            if (pureMode && state.isPlaying) pureModeOverride = false
                            onTogglePlay()
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(onLongPress = { pureModeOverride = true })
                            },
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "暂停（长按进入纯净模式）" else "播放",
                            modifier = Modifier.size(44.dp),
                        )
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "下一首")
                    }
                }
                if (!pureMode) {
                    IconButton(onClick = { showQueue = true }) {
                        Icon(Icons.Rounded.QueueMusic, contentDescription = "播放列表")
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }
            // 功能按钮行（紧凑：间距收窄）
            if (!pureMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { showOffsetPanel = !showOffsetPanel }) {
                        Icon(
                            Icons.Rounded.Schedule,
                            contentDescription = "歌词偏移",
                            tint = if (showOffsetPanel || lyricsOffsetMs != 0L) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    if (lyrics.hasTranslation) {
                        IconButton(onClick = {
                            translationOn = !translationOn
                            onToggleTranslation()
                        }) {
                            Icon(
                                Icons.Rounded.Translate,
                                contentDescription = if (translationOn) "关闭翻译" else "开启翻译",
                                tint = if (translationOn) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    IconButton(onClick = onExpandLyrics) {
                        Icon(
                            Icons.Rounded.Lyrics,
                            contentDescription = "显示歌词",
                            tint = if (lyricsMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(
                        onClick = {
                            val next = when (state.speed) {
                                1f -> 1.25f
                                1.25f -> 1.5f
                                1.5f -> 2f
                                else -> 1f
                            }
                            onSpeedChange(next)
                        },
                    ) {
                        Text(
                            text = if (state.speed == 1f) "倍速" else "%.2fx".format(state.speed).removeSuffix("0"),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.speed != 1f) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(
                        onClick = { showQualityPicker = true },
                        enabled = isRemoteSong,
                    ) {
                        Text(
                            text = qualityLabel(currentQuality),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isRemoteSong && currentQuality != Quality.Standard) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
        // 歌词偏移调节面板
        if (showOffsetPanel && lyricsMode && !pureMode) {
            LyricsOffsetPanel(
                offsetMs = lyricsOffsetMs,
                onChange = onLyricsOffsetChange,
                onDismiss = { showOffsetPanel = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp),
            )
        }
        // 返回按钮：悬浮左上角（安全区内，纯净模式隐藏）；歌词页返回到播放页
        if (!pureMode) {
            IconButton(
                onClick = onCollapseToPlayer,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 4.dp, top = statusBarPadding.calculateTopPadding() + 4.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
        }
    }
    if (showQueue && !pureMode) {
        QueueSheet(
            queue = queue,
            currentSongId = song.id,
            onDismiss = { showQueue = false },
            onPick = { index ->
                onPickQueueItem(index)
                showQueue = false
            },
        )
    }
    if (showQualityPicker && isRemoteSong) {
        QualityPickerDialog(
            current = currentQuality,
            onPick = { picked ->
                showQualityPicker = false
                if (picked != currentQuality) onQualityChange(picked)
            },
            onDismiss = { showQualityPicker = false },
        )
    }
}

/** 音质档位的短标签。 */
internal fun qualityLabel(q: Quality): String = when (q) {
    Quality.Low -> "低"
    Quality.Standard -> "标"
    Quality.High -> "高"
    Quality.Lossless -> "无"
    Quality.HiRes -> "Hi"
}

/** 音质选择弹窗：列出全部档位，高亮当前项。 */
@Composable
private fun QualityPickerDialog(
    current: Quality,
    onPick: (Quality) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("在线播放音质") },
        text = {
            Column {
                Text(
                    text = "取流失败时会自动换源 / 调整音质重试。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Quality.entries.forEach { q ->
                    val selected = q == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(q) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(selected = selected, onClick = { onPick(q) })
                        Column(modifier = Modifier.padding(start = 6.dp)) {
                            Text(qualityLabel(q), style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = when (q) {
                                    Quality.Low -> "流量友好，音质一般"
                                    Quality.Standard -> "标准音质，日常够用"
                                    Quality.High -> "较高码率，细节更丰富"
                                    Quality.Lossless -> "无损压缩（FLAC / APE）"
                                    Quality.HiRes -> "高解析度，需要音源支持"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 封面模式下的当前歌词横幅：单行居中，点击进入完整歌词页。 */
@Composable
private fun CurrentLineBanner(
    lyrics: Lyrics,
    positionMs: Long,
    offsetMs: Long,
    showTranslation: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effectiveOffset = offsetMs + lyrics.offsetMs
    val p = positionMs - effectiveOffset
    var index = -1
    for (i in lyrics.lines.indices) {
        if (lyrics.lines[i].startMs <= p) index = i else break
    }
    if (index < 0) return
    val line = lyrics.lines[index]
    androidx.compose.material3.Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = modifier.padding(horizontal = 40.dp, vertical = 4.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = line.text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showTranslation && !line.translation.isNullOrBlank()) {
                Text(
                    text = line.translation!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 歌词偏移调节面板：紧凑版，±50ms。 */
@Composable
private fun LyricsOffsetPanel(
    offsetMs: Long,
    onChange: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Surface(
        modifier = modifier.padding(horizontal = 56.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("偏移", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "%+d ms".format(offsetMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = offsetMs.toFloat().coerceIn(-50f, 50f),
                onValueChange = { onChange(it.toLong()) },
                valueRange = -50f..50f,
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("-50", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.material3.TextButton(onClick = { onChange(0L) }) {
                    Text("重置", style = MaterialTheme.typography.labelMedium)
                }
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text("完成", style = MaterialTheme.typography.labelMedium)
                }
                Text("+50", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 文本超长时启用跑马灯滚动。 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.marqueeIfLong(): Modifier =
    this.then(Modifier.basicMarquee(iterations = Int.MAX_VALUE))

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}