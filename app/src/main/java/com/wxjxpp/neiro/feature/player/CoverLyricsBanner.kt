package com.wxjxpp.neiro.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLineText
import com.wxjxpp.neiro.core.model.Lyrics

/** 横幅固定高度：当前行 + 译文 + 下一行预览。固定高度 = 切句时页面不会跳动。 */
private val BannerHeight = 128.dp

/**
 * 封面页的当前歌词横幅（取代旧的 5 行窗口版）。
 *
 * 旧版的问题：
 * 1. 5 行 + 译文塞进可变高度的 AnimatedContent，切句时新旧行上下重叠 → 挤成一坨；
 * 2. 自己拼 AnnotatedString，音节之间的空格丢失（歌词页没问题是因为转换器补了 NBSP）；
 * 3. 逐字进度按“音节字数平均分配时间”估算，且直接吃低频采样的进度 → 不是真逐字。
 *
 * 现在：直接复用歌词页的 [KaraokeLineText]（同一份 [toSyncedLyrics] 数据、同一个帧级插值时钟），
 * 所以逐字时间轴、空格、换行、发光/渐变擦除与歌词页完全一致。
 * 布局只显示“当前行（含译文）+ 下一行淡色预览”，固定高度，底部渐隐。
 */
@Composable
fun CoverLyricsBanner(
    lyrics: Lyrics,
    positionMs: Long,
    isPlaying: Boolean,
    /** 页面是否可见。不可见时不跑逐帧时钟（但仍保持组合，避免展开时才做首次布局）。 */
    active: Boolean = true,
    title: String,
    artistName: String,
    showTranslation: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val synced = remember(lyrics, title, artistName) { lyrics.toSyncedLyrics(title, artistName) }
    val lines = synced.lines
    if (lines.isEmpty()) return

    val smooth = rememberSmoothPositionMs(positionMs, isPlaying && active)
    // 当前行 = 最后一个已经开始的行；只有换句时才会重组
    val index by remember(lines) {
        derivedStateOf { lines.indexOfLast { it.start <= smooth.longValue } }
    }
    if (index < 0) return

    val cs = MaterialTheme.colorScheme
    val currentStyle = remember(cs.onSurface) {
        TextStyle(
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textMotion = TextMotion.Animated,
            color = cs.onSurface,
        )
    }
    val accompanimentStyle = remember(cs.onSurfaceVariant) {
        currentStyle.copy(fontSize = 14.sp, color = cs.onSurfaceVariant)
    }
    val phoneticStyle = remember(currentStyle) {
        currentStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BannerHeight)
            .clipToBounds()
            // 底部渐隐：长句/长译文被裁掉时不会出现硬切边
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = 28.dp.toPx().coerceAtMost(size.height)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startY = size.height - fade,
                        endY = size.height,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                // 淡入 + 轻微上浮；旧行快速淡出，几乎不与新行重叠
                (fadeIn(tween(260, delayMillis = 90)) +
                    slideInVertically(tween(320)) { it / 8 }) togetherWith fadeOut(tween(140))
            },
            contentAlignment = Alignment.TopStart,
            label = "coverLyricLine",
        ) { i ->
            val line = lines.getOrNull(i)
            val next = lines.getOrNull(i + 1)
            Column(modifier = Modifier.fillMaxWidth()) {
                when (line) {
                    is KaraokeLine -> KaraokeLineText(
                        line = line,
                        currentTimeProvider = { smooth.longValue.toInt() },
                        normalLineTextStyle = currentStyle,
                        accompanimentLineTextStyle = accompanimentStyle,
                        phoneticTextStyle = phoneticStyle,
                        activeColor = cs.onSurface,
                        blendMode = BlendMode.SrcOver,
                        showTranslation = showTranslation,
                        showPhonetic = false,
                    )
                    is SyncedLine -> Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = line.content,
                            style = currentStyle,
                            color = cs.primary,
                        )
                        if (showTranslation && !line.translation.isNullOrBlank()) {
                            Text(
                                text = line.translation!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                    else -> Unit
                }
                val nextText = next?.plainText().orEmpty()
                if (nextText.isNotBlank()) {
                    Text(
                        text = nextText,
                        style = currentStyle.copy(fontSize = 16.sp),
                        color = cs.onSurface.copy(alpha = 0.36f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** 预览行用的纯文本：逐字行把音节拼回去，并把转换器补的 NBSP 还原成普通空格以便正常换行/省略。 */
private fun ISyncedLine.plainText(): String = when (this) {
    is KaraokeLine -> syllables.joinToString("") { it.content }.replace('\u00A0', ' ').trim()
    is SyncedLine -> content.trim()
    else -> ""
}
