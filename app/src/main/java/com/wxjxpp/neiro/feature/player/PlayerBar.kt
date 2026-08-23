package com.wxjxpp.neiro.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wxjxpp.neiro.core.model.PlaybackState
import com.wxjxpp.neiro.ui.components.SongCover
import com.wxjxpp.neiro.ui.theme.AppTheme

/**
 * 底部播放栏（悬浮卡片态）。
 *
 * 上滑唤起播放页是**拖拽跟手**的：手指移动量实时回调给外壳写入 Sheet 进度，
 * 播放页像一张 Sheet 从播放栏背后连续展开，停在手指所在的位置。
 * 点击 = 程序化展开（带动画）。松手后由外壳把进度收敛到最近锚点。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerBar(
    state: PlaybackState,
    floating: Boolean,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    /** 上滑拖拽：deltaPx 为本帧位移（向上为负），由外壳换算成 Sheet 进度。 */
    onDragProgress: (Float) -> Unit = {},
    /** 拖拽结束（松手）：外壳据此收敛到最近锚点。 */
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val song = state.current ?: return
    val dimens = AppTheme.dimens
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.floatingBarMargin)
            .padding(bottom = dimens.floatingBarBottomMargin),
        shape = RoundedCornerShape(dimens.floatingBarRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = dimens.floatingBarElevation,
    ) {
        Column {
            // 固定高度容器：播放/暂停切换不改变播放栏总高度；
            // 暂停时波幅归零，平滑退化为直线（不是替换成另一个组件）。
            Box(
                modifier = Modifier.fillMaxWidth().height(dimens.spaceLg),
                contentAlignment = Alignment.Center,
            ) {
                LinearWavyProgressIndicator(
                    progress = { state.progress },
                    amplitude = { if (state.isPlaying) 1f else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.spaceMd),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.playerBarHeight)
                    // 上滑跟手展开播放页；点击也可
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                        ) { change, dragAmount ->
                            change.consume()
                            if (dragAmount.y < 0f) {
                                onExpand()
                                onDragProgress(dragAmount.y)
                            }
                        }
                    }
                    .clickable(onClick = onExpand)
                    .padding(horizontal = dimens.spaceMd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
            ) {
                SongCover(
                    song = song,
                    size = dimens.playerBarCoverSize,
                    radius = dimens.playerBarCoverRadius,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        song.artistName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "下一首")
                }
                IconButton(onClick = onOpenQueue) {
                    Icon(Icons.Rounded.QueueMusic, contentDescription = "播放列表")
                }
            }
        }
    }
}