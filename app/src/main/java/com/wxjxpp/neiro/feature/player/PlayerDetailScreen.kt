package com.wxjxpp.neiro.feature.player
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Translate
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wxjxpp.neiro.core.model.Lyrics
import com.wxjxpp.neiro.core.model.PlaybackState
import com.wxjxpp.neiro.core.model.RepeatMode
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.ui.components.AmbientGlowBackground
import com.wxjxpp.neiro.ui.components.SongCover
import com.wxjxpp.neiro.ui.theme.AppTheme

/**
 * 播放详情页。
 *
 * - 顶部尊重状态栏安全区（[WindowInsets.statusBars]）
 * - 标题置顶（大字），歌手在标题下方小字，过长跑马灯滚动
 * - 封面/歌词双模式：右下角按钮 + **带滑动动画**的 [AnimatedContent] 切换（无手势，防误触）
 * - 封面模式底部同时显示当前歌词行，与控制台之间用渐隐+模糊自然过渡
 * - 歌词模式：渐进式模糊、点击跳转、紧凑偏移面板（±50ms）
 * - 动态流光背景：封面位图模糊铺底 + 光斑漂移（可选）
 */
@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun SharedTransitionScope.PlayerDetailScreen(
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
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
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
) {
    val song = state.current ?: return
    val dimens = AppTheme.dimens
    var dragging by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    // 翻译显示开关：默认开启，可在播放页直接切换（同时通知设置持久化）
    var translationOn by remember(showTranslation) { mutableStateOf(showTranslation) }
    // 纯净模式：长按播放键或设置页开关开启，只留播放/换曲键；点播放键暂停时退出
    var pureMode by remember { mutableStateOf(pureModeDefault) }
    var showQueue by remember { mutableStateOf(false) }
    var showOffsetPanel by remember { mutableStateOf(false) }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    Box(modifier = Modifier.fillMaxSize()) {
        // 动态流光背景（封面位图铺底 + 光斑漂移）
        AmbientGlowBackground(
            baseColor = Color(song.coverSeedColor),
            coverUri = song.coverUri,
            enabled = ambientGlow,
            modifier = Modifier.fillMaxSize(),
        )
        // 顶部红线外区域实心填充（含状态栏），只在交界处留小段过渡
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
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarPadding.calculateTopPadding()),
        ) {
            Spacer(Modifier.height(dimens.spaceSm))
            // 顶部：标题 + 歌手（跑马灯）
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
            // 主区域：封面 / 歌词，AnimatedContent 滑动切换动画
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = showLyrics,
                    transitionSpec = {
                        if (targetState) {
                            // 进入歌词：从右滑入；退出到封面：向左滑出
                            (slideInVertically(tween(380)) { it / 6 } + fadeIn(tween(380))) togetherWith
                                (slideOutVertically(tween(380)) { -it / 6 } + fadeOut(tween(280)))
                        } else {
                            (slideInVertically(tween(380)) { -it / 6 } + fadeIn(tween(380))) togetherWith
                                (slideOutVertically(tween(380)) { it / 6 } + fadeOut(tween(280)))
                        }
                    },
                    label = "coverLyricsSwitch",
                ) { lyricsMode ->
                    if (lyricsMode) {
                        Box(modifier = Modifier.fillMaxSize()) {
                        // Apple Music 风格：歌词模式左上角小封面（sharedElement 切换时自动动画）
                            SongCover(
                                song = song,
                                size = 56.dp,
                                radius = 10.dp,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = 56.dp, top = 8.dp)
                                    .sharedElement(
                                        sharedContentState = rememberSharedContentState(key = PlayerSharedKeys.Cover),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                    ),
                            )
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
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            SongCover(
                                song = song,
                                size = dimens.detailCoverSize,
                                radius = dimens.detailCoverRadius,
                                modifier = Modifier.sharedElement(
                                    sharedContentState = rememberSharedContentState(key = PlayerSharedKeys.Cover),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                ),
                            )
                        }
                    }
                }
            }
        }
        // ---- 控制台 ----
        // 红线外区域实心填充，只在与歌词交接处留一小段渐变过渡（不做大面积半透明）
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
            // 封面模式下：控制台上方显示当前歌词行（单行，随播放更新）
            if (!showLyrics && !lyrics.isEmpty) {
                CurrentLineBanner(
                    lyrics = lyrics,
                    positionMs = state.positionMs,
                    offsetMs = lyricsOffsetMs,
                    showTranslation = showTranslation,
                    onClick = { showLyrics = true },
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
            // 播放控制行：纯净模式下只留上一首/播放/下一首
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spaceXl, vertical = dimens.spaceMd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                if (!pureMode) {
                    IconButton(onClick = onToggleShuffle) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "随机播放",
                            tint = if (state.shuffle) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首")
                }
                IconButton(
                    onClick = {
                        // 纯净模式下点暂停即退出纯净模式
                        if (pureMode && state.isPlaying) pureMode = false
                        onTogglePlay()
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { pureMode = true })
                        },
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停（长按进入纯净模式）" else "播放",
                        modifier = Modifier.size(44.dp),
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "下一首")
                }
                if (!pureMode) {
                    IconButton(onClick = onCycleRepeat) {
                        Icon(
                            imageVector = if (state.repeatMode == RepeatMode.One) {
                                Icons.Filled.RepeatOne
                            } else {
                                Icons.Filled.Repeat
                            },
                            contentDescription = "循环模式",
                            tint = if (state.repeatMode == RepeatMode.Off) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }
            }
            // 功能按钮行：歌词偏移 / 翻译开关 / 歌词切换 / 播放列表（在播放控件下方，不与其重叠）
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
                            Icons.Filled.Schedule,
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
                                Icons.Filled.Translate,
                                contentDescription = if (translationOn) "关闭翻译" else "开启翻译",
                                tint = if (translationOn) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            Icons.Filled.Lyrics,
                            contentDescription = if (showLyrics) "显示封面" else "显示歌词",
                            tint = if (showLyrics) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = { showQueue = true }) {
                        Icon(Icons.Filled.QueueMusic, contentDescription = "播放列表")
                    }
                }
            }
        }
        // 歌词偏移调节面板：紧凑版，±50ms
        if (showOffsetPanel && showLyrics && !pureMode) {
            LyricsOffsetPanel(
                offsetMs = lyricsOffsetMs,
                onChange = onLyricsOffsetChange,
                onDismiss = { showOffsetPanel = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp),
            )
        }
        // 返回按钮：悬浮左上角（安全区内，纯净模式隐藏）
        if (!pureMode) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 4.dp, top = statusBarPadding.calculateTopPadding() + 4.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                    text = line.translation,
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
        shape = RoundedCornerShape(16.dp),
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
/** 文本超长时启用跑马灯滚动（basicMarquee）。 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.marqueeIfLong(): Modifier =
    this.then(Modifier.basicMarquee(iterations = Int.MAX_VALUE))
private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}