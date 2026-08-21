package com.wxjxpp.musicplayer.feature.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wxjxpp.musicplayer.core.model.Lyrics
import com.wxjxpp.musicplayer.core.model.PlaybackState
import com.wxjxpp.musicplayer.core.model.RepeatMode
import com.wxjxpp.musicplayer.core.model.Song
import com.wxjxpp.musicplayer.ui.components.AmbientGlowBackground
import com.wxjxpp.musicplayer.ui.components.SongCover
import com.wxjxpp.musicplayer.ui.theme.AppTheme

/**
 * 播放详情页。
 *
 * - 标题置顶（大字），歌手在标题下方小字，过长跑马灯滚动
 * - 主区域支持**右滑切换到歌词**；右下角也有歌词/队列按钮
 * - 歌词模式：当前行居中高亮、其余模糊、点击行跳转、可调偏移
 * - 可选动态流光背景（封面主色派生）
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
) {
    val song = state.current ?: return
    val dimens = AppTheme.dimens
    var dragging by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showOffsetPanel by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 动态流光背景（可选）
        AmbientGlowBackground(
            baseColor = Color(song.coverSeedColor).copy(alpha = 0.6f),
            enabled = ambientGlow && !showLyrics,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 右滑切换到歌词视图
                .pointerInput(showLyrics) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        if (dragAmount < -40 && !showLyrics) {
                            showLyrics = true
                            change.consume()
                        } else if (dragAmount > 40 && showLyrics) {
                            showLyrics = false
                            change.consume()
                        }
                    }
                },
        ) {
            // 顶部：标题 + 歌手（跑马灯）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spaceXl, vertical = dimens.spaceMd),
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
            // 主区域：封面 / 歌词二选一
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (showLyrics) {
                    if (lyrics.isEmpty) {
                        Text(
                            text = "没有找到歌词",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LyricsPane(
                            lyrics = lyrics,
                            positionMs = state.positionMs,
                            showTranslation = showTranslation,
                            offsetMs = lyricsOffsetMs,
                            onSeekTo = onSeekTo,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
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
            Spacer(Modifier.height(dimens.spaceLg))
            // 只有一条进度控件：Expressive 波形滑杆
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
            Spacer(Modifier.height(dimens.spaceLg))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spaceXl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
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
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首")
                }
                FilledIconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(72.dp),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "下一首")
                }
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
            Spacer(Modifier.height(dimens.spaceLg))
        }

        // 返回按钮：悬浮左上角
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 4.dp, top = 24.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }

        // 右下角操作列：歌词切换 / 播放列表 / 歌词偏移
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = dimens.spaceLg, bottom = dimens.spaceXl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
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

        // 歌词偏移调节面板
        if (showOffsetPanel && showLyrics) {
            LyricsOffsetPanel(
                offsetMs = lyricsOffsetMs,
                onChange = onLyricsOffsetChange,
                onDismiss = { showOffsetPanel = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp),
            )
        }
    }
    if (showQueue) {
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

/** 歌词偏移调节面板：滑杆 ±2000ms。 */
@Composable
private fun LyricsOffsetPanel(
    offsetMs: Long,
    onChange: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("歌词偏移", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "%+d ms".format(offsetMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = offsetMs.toFloat(),
                onValueChange = { onChange(it.toLong()) },
                valueRange = -2000f..2000f,
            )
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("延后", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.material3.TextButton(onClick = { onChange(0L) }) {
                    Text("重置", style = MaterialTheme.typography.labelMedium)
                }
                Text("提前", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("完成")
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