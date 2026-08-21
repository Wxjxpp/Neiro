package com.wxjxpp.musicplayer.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wxjxpp.musicplayer.core.model.LyricLine
import com.wxjxpp.musicplayer.core.model.Lyrics

/**
 * 自研逐字歌词渲染器（纯 Compose，无第三方渲染依赖）。
 *
 * 背景：accompanist-lyrics-ui 1.0.16 依赖 native 文本引擎（SDF 图集），
 * 其系统字体获取链路在部分设备上失败导致整页黑块；且该库 1.0.14+ 在
 * Maven Central 的发布产物是坏的（空 jar），无法换版本规避。
 *
 * 本组件用标准 Compose Text + 渐变裁剪实现卡拉 OK 填充：
 * - 音节时间轴（增强型 LRC / TTML / QRC）→ 音节级逐字填充
 * - 仅行时间轴（普通 LRC / SRT）→ 整行按行进度填充
 * - 翻译行跟随显示；非当前行降低透明度；当前行自动滚动居中
 */
@Composable
fun LyricsPane(
    lyrics: Lyrics,
    positionMs: Long,
    modifier: Modifier = Modifier,
    showTranslation: Boolean = true,
) {
    if (lyrics.isEmpty) return
    val lines = lyrics.lines
    val offset = lyrics.offsetMs
    val listState = rememberLazyListState()

    // 当前播放到的行索引（线性扫描足够快：歌词一般 <200 行）
    val activeIndex = remember(lines, offset, positionMs) {
        val pos = positionMs - offset
        var found = -1
        for (i in lines.indices) {
            if (lines[i].startMs <= pos) found = i else break
        }
        found
    }

    // 当前行变化时自动滚动到屏幕上三分之一处
    var lastScrolledIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(activeIndex) {
        if (activeIndex < 0 || activeIndex == lastScrolledIndex) return@LaunchedEffect
        lastScrolledIndex = activeIndex
        runCatching {
            listState.animateScrollToItem(index = (activeIndex - 3).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp, end = 24.dp,
            // 上下大留白，让首尾行也能滚到居中位置
            top = 180.dp, bottom = 260.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        itemsIndexed(lines, key = { i, _ -> i }) { index, line ->
            LyricRow(
                line = line,
                isActive = index == activeIndex,
                positionMs = positionMs - offset,
                showTranslation = showTranslation,
            )
        }
    }
}

@Composable
private fun LyricRow(
    line: LyricLine,
    isActive: Boolean,
    positionMs: Long,
    showTranslation: Boolean,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.45f,
        animationSpec = tween(350),
        label = "lyricAlpha",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clickable(enabled = false) { },
    ) {
        if (line.syllables.isNotEmpty()) {
            KaraokeText(
                line = line,
                isActive = isActive,
                positionMs = positionMs,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
            )
        } else {
            val style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
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
            )
        }
        if (showTranslation && !line.translation.isNullOrBlank()) {
            Text(
                text = line.translation,
                style = MaterialTheme.typography.bodySmall,
                color = if (isActive) activeColor else inactiveColor,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 逐字卡拉 OK：已唱音节高亮，正在唱的音节按进度填充。 */
@Composable
private fun KaraokeText(
    line: LyricLine,
    isActive: Boolean,
    positionMs: Long,
    activeColor: Color,
    inactiveColor: Color,
) {
    val style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    if (!isActive) {
        Text(
            text = line.text,
            style = style,
            color = inactiveColor,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    Row(modifier = Modifier.fillMaxWidth()) {
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
) {
    Box {
        Text(text = text, style = style, color = baseColor)
        Text(
            text = text,
            style = style,
            color = activeColor,
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