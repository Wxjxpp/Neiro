package com.wxjxpp.musicplayer.feature.player

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.wxjxpp.musicplayer.core.model.PlaybackState
import com.wxjxpp.musicplayer.ui.components.SongCover
import com.wxjxpp.musicplayer.ui.theme.AppTheme

/**
 * 底部播放栏。
 *
 * 常规态与悬浮态共用同一份布局，通过 token 动画插值切换，
 * 避免两套实现导致的行为不一致。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.PlayerBar(
    state: PlaybackState,
    floating: Boolean,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.current ?: return
    val dimens = AppTheme.dimens
    val motion = AppTheme.motion

    val horizontalMargin by animateDpAsState(
        targetValue = if (floating) dimens.floatingBarMargin else 0.dp,
        animationSpec = tween(motion.medium),
        label = "barHorizontalMargin",
    )
    val bottomMargin by animateDpAsState(
        targetValue = if (floating) dimens.floatingBarBottomMargin else 0.dp,
        animationSpec = tween(motion.medium),
        label = "barBottomMargin",
    )
    val corner by animateDpAsState(
        targetValue = if (floating) dimens.floatingBarRadius else 0.dp,
        animationSpec = tween(motion.medium),
        label = "barCorner",
    )
    val elevation by animateDpAsState(
        targetValue = if (floating) dimens.floatingBarElevation else 0.dp,
        animationSpec = tween(motion.medium),
        label = "barElevation",
    )
    val tonal by animateFloatAsState(
        targetValue = if (floating) 3f else 2f,
        animationSpec = tween(motion.medium),
        label = "barTonal",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalMargin)
            .padding(bottom = bottomMargin),
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = tonal.dp,
        shadowElevation = elevation,
    ) {
        Column {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.playerBarHeight)
                    .clickable(onClick = onExpand)
                    .padding(horizontal = dimens.spaceMd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
            ) {
                SongCover(
                    seed = song.coverSeed,
                    size = dimens.playerBarCoverSize,
                    radius = dimens.playerBarCoverRadius,
                    modifier = Modifier.sharedElement(
                        rememberSharedContentState(key = PlayerSharedKeys.Cover),
                        animatedVisibilityScope = animatedVisibilityScope,
                    ),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = null)
                }
                IconButton(onClick = onOpenQueue) {
                    Icon(Icons.Filled.QueueMusic, contentDescription = null)
                }
            }
        }
    }
}

object PlayerSharedKeys {
    const val Cover = "player-cover"
    const val Title = "player-title"
}