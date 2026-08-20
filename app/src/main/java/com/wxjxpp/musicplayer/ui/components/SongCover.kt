package com.wxjxpp.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.wxjxpp.musicplayer.core.model.Song

/**
 * 歌曲封面。
 *
 * 当前用渐变占位；接入图片加载后，把内部实现换成
 * Coil 的 AsyncImage(model = song.coverUri, ...)，
 * 所有调用点都不需要改动 —— 这就是把它单独抽成组件的原因。
 */
@Composable
fun SongCover(
    song: Song,
    size: Dp,
    radius: Dp,
    modifier: Modifier = Modifier,
) = SongCover(
    seedColor = song.coverSeedColor,
    size = size,
    radius = radius,
    modifier = modifier,
)

@Composable
fun SongCover(
    seedColor: Long,
    size: Dp,
    radius: Dp,
    modifier: Modifier = Modifier,
) {
    val seed = Color(seedColor)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(
                Brush.linearGradient(
                    listOf(
                        seed.copy(alpha = 0.95f),
                        seed.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.surfaceVariant,
                    )
                )
            )
    )
}