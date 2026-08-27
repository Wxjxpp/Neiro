package com.wxjxpp.neiro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.wxjxpp.neiro.core.model.Song

/**
 * 歌曲封面。
 *
 * 有 coverUri 时用 Coil 加载真实封面（MediaStore albumart 是 content URI，Coil 可直接读）；
 * 加载中或失败时回落到按专辑散列的渐变占位，避免闪空白。
 *
 * ## 性能（v5）
 * 原实现用 `SubcomposeAsyncImage`。它为 loading/error 状态各起一次
 * **子组合（subcomposition）**，Coil 官方文档明确标注其显著慢于 `AsyncImage`，
 * 不应在 LazyColumn/LazyGrid 的 item 中使用——每次 item 复用都要重跑子组合。
 * 现在改为 `AsyncImage` + 底层渐变占位：占位由 `Modifier.background` 直接绘制，
 * 图片加载完成后覆盖在其上，视觉效果一致但没有子组合开销。
 */
@Composable
fun SongCover(
    song: Song,
    size: Dp,
    radius: Dp,
    modifier: Modifier = Modifier,
    fullQuality: Boolean = false,
) = SongCover(
    coverUri = song.coverUri,
    seedColor = song.coverSeedColor,
    size = size,
    radius = radius,
    modifier = modifier,
    fullQuality = fullQuality,
)
@Composable
fun SongCover(
    coverUri: String?,
    seedColor: Long,
    size: Dp,
    radius: Dp,
    modifier: Modifier = Modifier,
    /** Expr：详情页大图模式——按原图解码并固定缓存键，避免动画尺寸变化命中低清缓存。 */
    fullQuality: Boolean = false,
) {
    val shaped = modifier.size(size).clip(RoundedCornerShape(radius))
    if (coverUri.isNullOrBlank()) {
        GradientPlaceholder(seedColor, shaped)
        return
    }
    val context = LocalContext.current
    // 渐变占位直接作为背景绘制在同一节点下层：图片就绪后覆盖，无子组合
    val seed = Color(seedColor)
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val placeholderBrush = remember(seedColor, surfaceVariant) {
        Brush.linearGradient(
            listOf(
                seed.copy(alpha = 0.95f),
                seed.copy(alpha = 0.55f),
                surfaceVariant,
            ),
        )
    }
    // ImageRequest 只在 uri/尺寸模式变化时重建（避免每次重组都构造新请求对象，
    // 那会导致 Coil 认为是新请求并重新走一遍 key 计算）
    val request = remember(coverUri, fullQuality) {
        ImageRequest.Builder(context)
            .data(coverUri)
            .crossfade(true)
            .apply {
                // 两种模式都按物理像素精确解码，禁止 Coil 缩到逻辑像素导致大图发糊
                precision(Precision.EXACT)
                scale(Scale.FIT)
                if (fullQuality) {
                    // 固定全尺寸缓存键：详情页封面在播放页↔歌词页之间尺寸连续变化，
                    // Coil 默认按请求尺寸缓存——从歌词页回来时命中小图缓存导致低清
                    memoryCacheKey("neiro_full_$coverUri")
                }
            }
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = shaped.background(placeholderBrush),
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