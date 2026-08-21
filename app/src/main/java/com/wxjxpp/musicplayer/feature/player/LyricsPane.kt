package com.wxjxpp.musicplayer.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.wxjxpp.musicplayer.core.model.LyricLine
import com.wxjxpp.musicplayer.core.model.Lyrics
import com.wxjxpp.musicplayer.ui.theme.AppTheme

/**
 * 歌词面板。
 *
 * - 按播放进度自动滚动到当前行
 * - 当前行高亮
 * - 若该行带逐字时间戳（增强型 LRC / TTML），用渐变遮罩做卡拉 OK 逐字填充
 */
@Composable
fun LyricsPane(
    lyrics: Lyrics,
    positionMs: Long,
    modifier: Modifier = Modifier,
    showTranslation: Boolean = true,
) {
    if (lyrics.isEmpty) return
    val dimens = AppTheme.dimens
    val listState = rememberLazyListState()

    // 应用 offset 后再定位当前行
    val adjusted = positionMs + lyrics.offsetMs
    val currentIndex = remember(adjusted, lyrics) {
        lyrics.lines.indexOfLast { it.startMs <= adjusted }.coerceAtLeast(0)
    }

    LaunchedEffect(currentIndex) {
        // 让当前行大致居中
        listState.animateScrollToItem(index = currentIndex, scrollOffset = -160)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = dimens.spaceXl),
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            LyricRow(
                line = line,
                isCurrent = index == currentIndex,
                positionMs = adjusted,
                showTranslation = showTranslation,
            )
        }
    }
}

@Composable
private fun LyricRow(
    line: LyricLine,
    isCurrent: Boolean,
    positionMs: Long,
    showTranslation: Boolean,
) {
    val dimens = AppTheme.dimens
    val alpha by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0.45f,
        label = "lyricAlpha",
    )
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    val highlight = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isCurrent && line.isWordByWord) {
                KaraokeLine(line = line, positionMs = positionMs, highlight = highlight, base = baseColor)
            } else {
                Text(
                    text = line.text,
                    style = if (isCurrent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    color = if (isCurrent) highlight else baseColor,
                    textAlign = TextAlign.Center,
                )
            }
            if (showTranslation) {
                line.translation?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * 卡拉 OK 逐字填充。
 *
 * 用线性渐变刷子按已唱进度切分颜色：进度左侧用 highlight，右侧用 base，
 * 中间加一小段过渡避免生硬。这样不需要逐字测量宽度也能得到平滑填充。
 */
@Composable
private fun KaraokeLine(
    line: LyricLine,
    positionMs: Long,
    highlight: Color,
    base: Color,
) {
    val progress = remember(positionMs, line) { line.karaokeProgress(positionMs) }
    val brush = remember(progress, highlight, base) {
        val start = progress.coerceIn(0f, 1f)
        Brush.horizontalGradient(
            0f to highlight,
            start to highlight,
            (start + 0.02f).coerceAtMost(1f) to base,
            1f to base,
        )
    }
    Text(
        text = line.text,
        style = MaterialTheme.typography.titleMedium.copy(brush = brush),
        textAlign = TextAlign.Center,
    )
}

/** 当前行已唱比例：优先按音节时间戳算，缺失时退化为按行区间线性推进。 */
private fun LyricLine.karaokeProgress(positionMs: Long): Float {
    if (syllables.isEmpty()) {
        val end = endMs ?: return 0f
        val span = (end - startMs).toFloat().takeIf { it > 0f } ?: return 0f
        return ((positionMs - startMs) / span).coerceIn(0f, 1f)
    }
    val totalChars = syllables.sumOf { it.text.length }.takeIf { it > 0 } ?: return 0f
    var sung = 0
    for (syllable in syllables) {
        val end = syllable.endMs ?: (syllable.startMs + 300)
        when {
            positionMs >= end -> sung += syllable.text.length
            positionMs >= syllable.startMs -> {
                val span = (end - syllable.startMs).toFloat().takeIf { it > 0f } ?: 1f
                val ratio = ((positionMs - syllable.startMs) / span).coerceIn(0f, 1f)
                sung += (syllable.text.length * ratio).toInt()
                break
            }
            else -> break
        }
    }
    return (sung.toFloat() / totalChars).coerceIn(0f, 1f)
}