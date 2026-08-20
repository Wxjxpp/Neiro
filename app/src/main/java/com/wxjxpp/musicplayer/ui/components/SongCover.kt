package com.wxjxpp.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * 占位封面。接入真实图片时把内部实现换成 Coil AsyncImage，
 * 调用方无需修改。
 */
@Composable
fun SongCover(
    seed: Color,
    size: Dp,
    radius: Dp,
    modifier: Modifier = Modifier,
) {
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