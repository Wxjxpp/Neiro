package com.wxjxpp.musicplayer.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wxjxpp.musicplayer.core.model.LyricLine
import com.wxjxpp.musicplayer.core.model.Lyrics

/**
 * 自研逐字歌词渲染器（纯 Compose，无第三方渲染依赖）。
 *
 * - 音节时间轴（增强型 LRC / TTML / QRC）→ 音节级逐字填充
 * - 仅行时间轴（普通 LRC / SRT）→ 整行按行进度填充
 * - 当前行**垂直居中**显示并高亮；非当前行模糊 + 降透明度
 * - 点击任意行跳转到该行时间（[onSeekTo]）
 * - [offsetMs] 用户手动偏移，正数让歌词提前、负数延后
 *
 * 排版注意：逐字音节必须用 [FlowRow] 而不是 Row——Row 不换行，
 * 长句会被挤压成一条竖线。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricsPane(
    lyrics: Lyrics,
    positionMs: Long,
    modifier: Modifier = Modifier,
    showTranslation: Boolean = true,
    offsetMs: Long = 0L,
    onSeekTo: (Long) -> Unit = {},
) {
    if (lyrics.isEmpty) return
    val lines = lyrics.lines
    // 用户偏移 + LRC 内嵌 offset：正数 = 歌词提前显示
    val effectiveOffset = offsetMs + lyrics.offsetMs
    val listState = rememberLazyListState()

    fun activeIndexOf(pos: Long): Int {
        val p = pos - effectiveOffset
        var found = -1
        for (i in lines.indices) {
            if (lines[i].startMs <= p) found = i else break
        }
        return found
    }

    var activeIndex by remember(lines) { mutableIntStateOf(activeIndexOf(positionMs)) }
    LaunchedEffect(positionMs, lines, effectiveOffset) {
        activeIndex = activeIndexOf(positionMs)
    }

    // 当前行变化时滚动到列表中心
    var lastScrolledIndex by remember { mutableIntStateOf(-1) }
    val density = LocalDensity.current
    LaunchedEffect(activeIndex) {
        if (activeIndex < 0 || activeIndex == lastScrolledIndex) return@LaunchedEffect
        lastScrolledIndex = activeIndex
        runCatching {
            listState.animateScrollToItem(index = activeIndex, scrollOffset = -centeringOffset(listState, density))
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(
            start = 24.dp, end = 24.dp,
            top = 300.dp, bottom = 400.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        itemsIndexed(lines, key = { i, _ -> i }) { index, line ->
            LyricRow(
                line = line,
                isActive = index == activeIndex,
                positionMs = positionMs - effectiveOffset,
                showTranslation = showTranslation,
                onClick = { onSeekTo(line.startMs + effectiveOffset + 1) },
            )
        }
    }
}

/** 让目标行滚到视口中心的偏移量（负值向上滚）。 */
private fun centeringOffset(
    listState: androidx.compose.foundation.lazy.LazyListState,
    density: androidx.compose.ui.unit.Density,
): Int = with(density) {
    val viewportHeight = listState.layoutInfo.viewportSize.height
    (viewportHeight / 4).coerceAtLeast(0)
}

@Composable
private fun LyricRow(
    line: LyricLine,
    isActive: Boolean,
    positionMs: Long,
    showTranslation: Boolean,
    onClick: () -> Unit,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)

    // 非当前行：模糊 + 半透明；当前行：原尺寸高亮
    val blurRadius = if (isActive) 0.dp else 3.dp
    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.5f,
        animationSpec = tween(350),
        label = "lyricAlpha",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.blur(blurRadius).alpha(alpha)) {
            if (line.syllables.isNotEmpty()) {
                KaraokeText(
                    line = line,
                    isActive = isActive,
                    positionMs = positionMs,
                    activeColor = activeColor,
                    inactiveColor = inactiveColor,
                    style = style,
                )
            } else {
                val lineProgress = when {
                    !isActive -> 0f
                    line.endMs != null && line.endMs > line.startMs ->
                        ((positionMs - line.startMs).toFloat() / (line.endMs - line.startMs)).coerceIn(0f, 1f)
                    else -> 1f
                }
                ProgressFilledText(
                    text = line.text,
                    progress = lineProgress,
                    activeColor = activeColor,
                    baseColor = inactiveColor,
                    style = style,
                    maxLines = 4,
                )
            }
        }
        if (showTranslation && !line.translation.isNullOrBlank()) {
            Text(
                text = line.translation,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isActive) activeColor else inactiveColor,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .alpha(if (isActive) 1f else 0.45f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 逐字卡拉 OK：FlowRow 保证长句正常换行。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KaraokeText(
    line: LyricLine,
    isActive: Boolean,
    positionMs: Long,
    activeColor: Color,
    inactiveColor: Color,
    style: TextStyle,
) {
    if (!isActive) {
        Text(
            text = line.text,
            style = style,
            color = inactiveColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        line.syllables.forEach { syl ->
            val end = syl.endMs ?: (syl.startMs + 300)
            when {
                positionMs >= end -> Text(text = syl.text, style = style, color = activeColor)
                positionMs >= syl.startMs -> ProgressFilledText(
                    text = syl.text,
                    progress = ((positionMs - syl.startMs).toFloat() / (end - syl.startMs)).coerceIn(0f, 1f),
                    activeColor = activeColor,
                    baseColor = inactiveColor,
                    style = style,
                )
                else -> Text(text = syl.text, style = style, color = inactiveColor)
            }
        }
    }
}

/**
 * 进度填充文本：两层 Text 叠加，上层用 DstOut 混合的透明渐变
 * 从 progress 位置开始把已画内容"擦掉"，形成从左到右的填充效果。
 */
@Composable
private fun ProgressFilledText(
    text: String,
    progress: Float,
    activeColor: Color,
    baseColor: Color,
    style: TextStyle,
    maxLines: Int = Int.MAX_VALUE,
) {
    Box {
        Text(
            text = text, style = style, color = baseColor,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = text,
            style = style,
            color = activeColor,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    val edge = size.width * progress
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, Color.Black),
                            startX = edge,
                            endX = edge + 1f,
                        ),
                        blendMode = BlendMode.DstOut,
                    )
                },
        )
    }
}