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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.wxjxpp.musicplayer.core.model.Song

/**
 * 歌曲封面。
 *
 * 有 coverUri 时用 Coil 加载真实封面（MediaStore albumart 是 content URI，Coil 可直接读）；
 * 加载中或失败时回落到按专辑散列的渐变占位，避免闪空白。
 */
@Composable
fun SongCover(
    song: Song,
    size: Dp,
    radius: Dp,
    modifier: Modifier = Modifier,
) = SongCover(
    coverUri = song.coverUri,
    seedColor = song.coverSeedColor,
    size = size,
    radius = radius,
    modifier = modifier,
)

@Composable
fun SongCover(
    coverUri: String?,
    seedColor: Long,
    size: Dp,
    radius: Dp,
    modifier: Modifier = Modifier,
) {
    val shaped = modifier.size(size).clip(RoundedCornerShape(radius))

    if (coverUri.isNullOrBlank()) {
        GradientPlaceholder(seedColor, shaped)
        return
    }

    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(coverUri)
            .crossfade(true)
            // 按物理像素精确解码，禁止 Coil 缩到逻辑像素导致大图发糊
            .precision(Precision.EXACT)
            .scale(Scale.FIT)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = shaped,
        loading = { GradientPlaceholder(seedColor, Modifier.size(size)) },
        error = { GradientPlaceholder(seedColor, Modifier.size(size)) },
    )
}

@Composable
private fun GradientPlaceholder(seedColor: Long, modifier: Modifier) {
    val seed = Color(seedColor)
    Box(
        modifier = modifier.background(
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