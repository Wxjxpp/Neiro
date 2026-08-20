package com.wxjxpp.musicplayer.feature.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wxjxpp.musicplayer.core.model.PlaybackState
import com.wxjxpp.musicplayer.ui.components.SongCover
import com.wxjxpp.musicplayer.ui.theme.AppTheme

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
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
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()
    val horizontalMargin by animateDpAsState(if (floating) dimens.floatingBarMargin else 0.dp, spatial, label = "barHorizontalMargin")
    val bottomMargin by animateDpAsState(if (floating) dimens.floatingBarBottomMargin else 0.dp, spatial, label = "barBottomMargin")
    val corner by animateDpAsState(if (floating) dimens.floatingBarRadius else 0.dp, spatial, label = "barCorner")
    val elevation by animateDpAsState(if (floating) dimens.floatingBarElevation else 0.dp, spatial, label = "barElevation")

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = horizontalMargin).padding(bottom = bottomMargin),
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = elevation,
    ) {
        Column {
            // 正在播放才用 Expressive Wave；暂停时退化为静态直线，避免视觉暗示仍在播放。
            if (state.isPlaying) {
                LinearWavyProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth().padding(start = dimens.spaceMd, end = dimens.spaceMd, top = dimens.spaceSm),
                )
            } else {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth().height(dimens.spaceXs),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(dimens.playerBarHeight).clickable(onClick = onExpand).padding(horizontal = dimens.spaceMd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
            ) {
                SongCover(
                    song = song,
                    size = dimens.playerBarCoverSize,
                    radius = dimens.playerBarCoverRadius,
                    modifier = Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState(key = PlayerSharedKeys.Cover),
                        animatedVisibilityScope = animatedVisibilityScope,
                    ),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artistName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onTogglePlay) {
                    Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
                }
                IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, contentDescription = null) }
                IconButton(onClick = onOpenQueue) { Icon(Icons.Filled.QueueMusic, contentDescription = null) }
            }
        }
    }
}

object PlayerSharedKeys {
    const val Cover = "player-cover"
    const val Title = "player-title"
}