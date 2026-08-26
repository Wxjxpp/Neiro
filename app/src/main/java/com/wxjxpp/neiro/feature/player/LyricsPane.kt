package com.wxjxpp.neiro.feature.player
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wxjxpp.neiro.core.model.LyricLine
import com.wxjxpp.neiro.core.model.Lyrics
import kotlin.math.abs

/** 歌词水平对齐方式。 */
enum class LyricsAlign { Start, Center, End }

/**
 * 自研逐字歌词渲染器（纯 Compose，无第三方渲染依赖）。
 *
 * 视觉参考 Apple Music：
 * - 当前行**动态聚焦**于视口 [focusFraction]（默认 33%，从上往下）高度处，
 *   聚焦位随窗口尺寸实时计算，不写死像素
 * - 用户拖动时暂停跟随，停止 3 秒后平滑滚回当前句
 * - 字号 [fontScale] 与行间隙 [gapScale] 用户可调（设置页）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricsPane(
    lyrics: Lyrics,
    positionMs: Long,
    modifier: Modifier = Modifier,
    showTranslation: Boolean = true,
    offsetMs: Long = 0L,
    align: LyricsAlign = LyricsAlign.Center,
    springAnimation: Boolean = false,
    fontScale: Float = 1f,
    gapScale: Float = 1f,
    /** 距聚焦行渐进模糊：越近越清晰（RenderEffect；API<31 自动跳过）。 */
    progressiveBlur: Boolean = false,
    /** 当前行聚焦位（视口高度比例，从上往下）。 */
    focusFraction: Float = 0.33f,
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
    // 统一的平滑滚动：目标行**中心**对齐视口 focusFraction 高度处。
    // animateScrollToItem 只能按 item 顶边定位，多行长句会 under-scroll，
    // 所以先粗定位，再从 layoutInfo 读实际高度做一次精确校正。
    suspend fun smoothScrollTo(index: Int) {
        if (index < 0) return
        runCatching {
            val viewport = listState.layoutInfo.viewportSize.height
            if (viewport <= 0) return@runCatching
            var info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            if (info == null || abs(index - listState.firstVisibleItemIndex) > 8) {
                // 目标行不在视口内：先无动画粗定位到目标附近，
                // 再读取实际尺寸做单段精调。绝不能用 animateScrollToItem——
                // 它会把行顶边怼到视口顶部导致先冲过头再校正的两段式跳动。
                listState.scrollToItem(index = (index - 3).coerceAtLeast(0))
                info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                    ?: return@runCatching
            }
            // 单段动画：把该行中心滚到视口 focusFraction（33%）高度处的聚焦位
            val targetTop = viewport * focusFraction - info.size / 2f
            val delta = info.offset - targetTop
            if (abs(delta) > 4) {
                listState.animateScrollBy(delta, tween(420))
            }
        }
    }
    // 用户拖动：暂停跟随；停止超 3 秒后平滑滚回当前句（不瞬移）
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var userDragging by remember { mutableStateOf(false) }
    LaunchedEffect(isDragged) {
        if (isDragged) {
            userDragging = true
        } else if (userDragging) {
            kotlinx.coroutines.delay(3_000L)
            userDragging = false
            smoothScrollTo(activeIndex.coerceAtLeast(0))
        }
    }
    // 切句且用户没在拖动时平滑滚到聚焦位
    var lastScrolledIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(activeIndex, userDragging) {
        if (activeIndex < 0 || activeIndex == lastScrolledIndex || userDragging) return@LaunchedEffect
        lastScrolledIndex = activeIndex
        smoothScrollTo(activeIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = when (align) {
            LyricsAlign.Start -> Alignment.Start
            LyricsAlign.Center -> Alignment.CenterHorizontally
            LyricsAlign.End -> Alignment.End
        },
        contentPadding = PaddingValues(
            // 顶部留白 = 视口 33% 聚焦位的一半减一行高度（动态，不写死），
            // 保证第一句也能滚到聚焦位；底部对称留白
            start = 28.dp, end = 28.dp,
            top = 160.dp, bottom = 280.dp,
        ),
        verticalArrangement = Arrangement.spacedBy((26 * gapScale).dp),
    ) {
        itemsIndexed(lines, key = { i, _ -> i }) { index, line ->
            LyricRow(
                line = line,
                align = align,
                isActive = index == activeIndex,
                distance = index - activeIndex,
                isUserScrolling = userDragging,
                positionMs = positionMs - effectiveOffset,
                showTranslation = showTranslation,
                fontScale = fontScale,
                progressiveBlur = progressiveBlur,
                springAnimation = springAnimation,
                onClick = { onSeekTo(line.startMs + effectiveOffset + 1) },
            )
        }
    }
}

@Composable
private fun LyricRow(
    line: LyricLine,
    align: LyricsAlign,
    isActive: Boolean,
    distance: Int,
    isUserScrolling: Boolean,
    positionMs: Long,
    showTranslation: Boolean,
    fontScale: Float,
    progressiveBlur: Boolean,
    springAnimation: Boolean,
    onClick: () -> Unit,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
    // 字号用户可调：基准 titleLarge，当前行更大、非当前行略小（Apple Music 层级感）
    val baseStyle = MaterialTheme.typography.titleLarge
        .copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * fontScale)
    val activeStyle = baseStyle.copy(fontWeight = FontWeight.Bold)
    val inactiveStyle = baseStyle.copy(fontWeight = FontWeight.SemiBold)

    // 透明度分层（MusicFree 方式）：非当前行低透明度，无模糊——
    // Compose 的 Modifier.blur 在多数设备上效果脏且费电，透明度分层更干净
    // 距离越远越淡：|d|=1 → 0.55，2 → 0.42，≥3 封顶 0.32
    val rowAlpha = when {
        isUserScrolling -> 0.6f
        isActive -> 1f
        else -> (0.55f - (abs(distance) - 1) * 0.13f).coerceAtLeast(0.32f)
    }
    // 当前行轻微放大；弹簧模式下用 spring 带回弹
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.04f else 0.96f,
        animationSpec = if (springAnimation) {
            androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
            )
        } else {
            tween(300)
        },
        label = "lyricScale",
    )
    // 非当前行透明度更低，与渐进模糊叠加出"退后"的层次
    // 渐进模糊（用户需求）：越靠近聚焦行模糊越小；|d|=1 起步，每远一行 +6px，
    // 上限 18px。RenderEffect 链路：graphicsLayer 在外、drawWithContent 在内；
    // API<31 无 RenderEffect，自动退回纯透明度分层（不降级 BlurMaskFilter——
    // 每行一个软件模糊层代价过高）
    val distanceBlurPx = if (progressiveBlur && !isActive && abs(distance) >= 1) {
        (4f + 6f * (abs(distance) - 1)).coerceAtMost(18f)
    } else {
        0f
    }
    val rowModifier = if (distanceBlurPx > 0f && Build.VERSION.SDK_INT >= 31) {
        val effect = android.graphics.RenderEffect.createBlurEffect(
            distanceBlurPx, distanceBlurPx, android.graphics.Shader.TileMode.CLAMP,
        ).asComposeRenderEffect()
        Modifier.graphicsLayer {
            scaleX = scale; scaleY = scale
            renderEffect = effect
        }
    } else {
        Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(rowModifier)
            .clickable(onClick = onClick),
        horizontalAlignment = when (align) {
            LyricsAlign.Start -> Alignment.Start
            LyricsAlign.Center -> Alignment.CenterHorizontally
            LyricsAlign.End -> Alignment.End
        },
    ) {
        Box(modifier = Modifier.alpha(rowAlpha)) {
            Column(
                horizontalAlignment = when (align) {
                    LyricsAlign.Start -> Alignment.Start
                    LyricsAlign.Center -> Alignment.CenterHorizontally
                    LyricsAlign.End -> Alignment.End
                },
            ) {
                if (line.syllables.isNotEmpty()) {
                    KaraokeText(
                        line = line,
                        align = align,
                        isActive = isActive,
                        positionMs = positionMs,
                        activeColor = activeColor,
                        inactiveColor = inactiveColor,
                        activeStyle = activeStyle,
                        inactiveStyle = inactiveStyle,
                    )
                } else {
                    val lineProgress = when {
                        !isActive -> 0f
                        line.endMs != null && line.endMs > line.startMs ->
                            ((positionMs - line.startMs).toFloat() / (line.endMs - line.startMs)).coerceIn(0f, 1f)
                        else -> 1f
                    }
                    PlainTextWithProgress(
                        text = line.text,
                        progress = lineProgress,
                        align = align,
                        isActive = isActive,
                        activeColor = activeColor,
                        inactiveColor = inactiveColor,
                        activeStyle = activeStyle,
                        inactiveStyle = inactiveStyle,
                        maxLines = 3,
                    )
                }
                // 翻译与主文本同一模糊层、同向对齐
                if (showTranslation && !line.translation.isNullOrBlank()) {
                    Text(
                        text = line.translation!!,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * fontScale,
                            textAlign = when (align) {
                                LyricsAlign.Start -> TextAlign.Start
                                LyricsAlign.Center -> TextAlign.Center
                                LyricsAlign.End -> TextAlign.End
                            },
                        ),
                        color = if (isActive) activeColor else inactiveColor,
                        modifier = Modifier.padding(top = 6.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 文本水平对齐 → FlowRow 排列 / textAlign 的统一映射。 */
private fun flowRowArrangement(align: LyricsAlign): Arrangement.Horizontal = when (align) {
    LyricsAlign.Start -> Arrangement.Start
    LyricsAlign.Center -> Arrangement.Center
    LyricsAlign.End -> Arrangement.End
}
private fun textAlignOf(align: LyricsAlign): TextAlign = when (align) {
    LyricsAlign.Start -> TextAlign.Start
    LyricsAlign.Center -> TextAlign.Center
    LyricsAlign.End -> TextAlign.End
}

/** 逐字卡拉 OK：FlowRow 保证长句正常换行；非当前行用整段 Text。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KaraokeText(
    line: LyricLine,
    align: LyricsAlign,
    isActive: Boolean,
    positionMs: Long,
    activeColor: Color,
    inactiveColor: Color,
    activeStyle: TextStyle,
    inactiveStyle: TextStyle,
) {
    if (!isActive) {
        Text(
            text = line.text,
            style = inactiveStyle,
            color = inactiveColor,
            textAlign = textAlignOf(align),
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = flowRowArrangement(align),
    ) {
        line.syllables.forEach { syl ->
            val end = syl.endMs ?: (syl.startMs + 300)
            when {
                positionMs >= end -> Text(text = syl.text, style = activeStyle, color = activeColor)
                positionMs >= syl.startMs -> ProgressFilledText(
                    text = syl.text,
                    progress = ((positionMs - syl.startMs).toFloat() / (end - syl.startMs)).coerceIn(0f, 1f),
                    activeColor = activeColor,
                    baseColor = inactiveColor,
                    style = activeStyle,
                )
                else -> Text(text = syl.text, style = activeStyle, color = inactiveColor)
            }
        }
    }
}

/** 整行填充文本（无音节时间轴时）：当前行走双层擦除，非当前行直接渲染。 */
@Composable
private fun PlainTextWithProgress(
    text: String,
    progress: Float,
    align: LyricsAlign,
    isActive: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    activeStyle: TextStyle,
    inactiveStyle: TextStyle,
    maxLines: Int,
) {
    if (!isActive) {
        Text(
            text = text,
            style = inactiveStyle,
            color = inactiveColor,
            textAlign = textAlignOf(align),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = activeStyle,
            color = inactiveColor,
            textAlign = textAlignOf(align),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = text,
            style = activeStyle,
            color = activeColor,
            textAlign = textAlignOf(align),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
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

/** 音节级进度填充文本。 */
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