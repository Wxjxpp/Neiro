package com.wxjxpp.neiro.feature.player

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import com.wxjxpp.neiro.core.model.Lyrics

/**
 * Expr 实验分支：Accompanist 歌词渲染包装器。
 *
 * 核心卖点：行切换时 [springPlacement] 的 ApproachLayout 弹簧动画——
 * 行位置变化不是瞬间跳变，而是带阻尼的"拉扯/挤压后复位"，产生黏滞感。
 *
 * 职责：
 * - 把领域模型 [Lyrics] 经 [toSyncedLyrics] 转为库所需的 SyncedLyrics；
 * - 沿用播放页沉浸配色（primary 高亮 / onSurface 正文）；
 * - 字号跟随用户设置（fontScale 映射到 normalLineTextStyle）；
 * - 关闭库内置 BlendMode.Plus 发光（与我们的深色画布叠加会过曝）。
 */
@Composable
fun AccompanistLyricsPane(
    lyrics: Lyrics,
    positionMs: Long,
    title: String,
    artistName: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    showTranslation: Boolean = true,
    offsetMs: Long = 0L,
    fontScale: Float = 1f,
    onSeekTo: (Long) -> Unit = {},
) {
    val synced = remember(lyrics, title, artistName, offsetMs) {
        lyrics.toSyncedLyrics(title, artistName)
    }
    val listState = rememberLazyListState()
    val cs = MaterialTheme.colorScheme
    // 关键桥接：库内部用 derivedStateOf 调用 currentPosition()，
    // 必须让它读到 Compose State 才会在进度变化时重新求值——
    // 直接捕获普通参数会导致闭包冻结，歌词不滚动不高亮不响应跳转
    val positionState = androidx.compose.runtime.rememberUpdatedState(positionMs.toInt())
    val playingState = androidx.compose.runtime.rememberUpdatedState(isPlaying)
    // 帧级插值时钟：播放进度源的采样频率低（数百毫秒级），直接喂给逐字染色会卡顿；
    // 这里锚定最近一次外部进度，按真实流逝时间每帧外推，卡拉OK才能丝滑走字
    val smoothPosition = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableLongStateOf(positionMs)
    }
    LaunchedEffect(Unit) {
        var anchorPos = positionState.value.toLong()
        var anchorNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { frame ->
                val external = positionState.value.toLong()
                if (external != anchorPos) {
                    // 外部源刷新（含 seek 回退）：重新锚定
                    anchorPos = external
                    anchorNanos = frame
                }
                smoothPosition.longValue = if (playingState.value) {
                    anchorPos + (frame - anchorNanos) / 1_000_000L
                } else {
                    anchorPos
                }
            }
        }
    }
    val normalStyle = remember(cs.primary, fontScale) {
        TextStyle(
            fontSize = 30.sp * fontScale,
            fontWeight = FontWeight.Bold,
            textMotion = TextMotion.Animated,
            color = cs.onSurface,
        )
    }
    val accompanimentStyle = remember(cs.primary, fontScale) {
        TextStyle(
            fontSize = 18.sp * fontScale,
            fontWeight = FontWeight.Bold,
            textMotion = TextMotion.Animated,
            color = cs.onSurfaceVariant,
        )
    }
    KaraokeLyricsView(
        listState = listState,
        lyrics = synced,
        currentPosition = { smoothPosition.longValue.toInt() },
        onLineClicked = { line -> onSeekTo(line.start.toLong()) },
        onLinePressed = {},
        showTranslation = showTranslation,
        showPhonetic = true,
        normalLineTextStyle = normalStyle,
        accompanimentLineTextStyle = accompanimentStyle,
        textColor = cs.onSurface,
        // 关闭 Plus 发光：深色画布上白字叠加会过曝，用普通绘制
        blendMode = BlendMode.SrcOver,
        // 距离渐变模糊（用户要求恢复）：距聚焦行越远模糊越重，梯度加大更明显
        useBlurEffect = true,
        blurDelta = 6f,
        // 聚焦行位置下移（用户指定）：行顶对齐到视口约 35%~40% 高度处
        offset = 150.dp,
        modifier = modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Auto
        },
    )
}