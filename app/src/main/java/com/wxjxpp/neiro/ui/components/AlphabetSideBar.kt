package com.wxjxpp.neiro.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 歌曲首字母分组索引表（A-Z + #）。 */
val AlphabetChars: List<Char> = ('A'..'Z').toList() + '#'

/** 从标题提取首字母分组键：英文归一化大写；非 A-Z 归入 '#'。 */
fun initialOf(title: String): Char {
    val c = title.trim().firstOrNull() ?: return '#'
    val upper = c.uppercaseChar()
    return if (upper in 'A'..'Z') upper else '#'
}

/**
 * 字母索引侧边栏（M3E）——单手势模型：
 *
 * 手指按住侧边栏即弹出指示球并跟手移动（含滑离侧边栏的屏幕内自由区）；
 * 纵向位置直接映射列表快速滚动；松手即消失（静止态不驻留）。
 *
 * @param activeChar 当前激活字母（球内文字与侧边栏高亮）。
 * @param onSelect 手指所在字母回调（用于定位列表）。
 * @param onProgress 手指在条内的纵向比例 0..1（用于列表快滚映射）。
 * @param onTouch 手指按下=true / 松开=false（联动底栏下沉/回归）。
 */
@Composable
fun AlphabetSideBar(
    activeChar: Char?,
    onSelect: (Char) -> Unit,
    onProgress: (Float) -> Unit,
    onTouch: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var barTop by remember { mutableFloatStateOf(0f) }
    var barHeight by remember { mutableFloatStateOf(1f) }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(30.dp)
            .onGloballyPositioned { coords ->
                barTop = coords.positionInRoot().y
                barHeight = coords.size.height.toFloat().coerceAtLeast(1f)
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onTouch(true)
                    fun progressOf(y: Float): Float =
                        ((y - barTop) / barHeight).coerceIn(0f, 1f)
                    fun charAt(y: Float): Char = AlphabetChars[
                        ((y - barTop) / (barHeight / AlphabetChars.size))
                            .toInt().coerceIn(0, AlphabetChars.lastIndex)
                    ]
                    val startY = down.position.y
                    onSelect(charAt(startY))
                    onProgress(progressOf(startY))
                    // 持续追踪：滑出侧边栏后仍在屏幕内跟手（拖球快移）
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        change.consume()
                        val y = change.position.y
                        onSelect(charAt(y))
                        onProgress(progressOf(y))
                    }
                    onTouch(false)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AlphabetChars.forEach { c ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = c.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (c == activeChar) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * 字母索引指示球（M3E）。
 *
 * 跟随 [progress]（0..1，容器纵向比例）显示当前字母；
 * 出现/消失经 Spring 弹性缩放过渡；拖动期间由外部控制显隐。
 */
@Composable
fun AlphabetIndicatorBall(
    label: Char?,
    progress: Float,
    visible: Boolean,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 52.dp,
) {
    if (label == null || progress <= 0f) return
    val cs = MaterialTheme.colorScheme
    // 弹性出现/消失（MediumBouncy 回弹）
    val appearScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "ballAppear",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = if (visible) 1f else 0f
                scaleX = appearScale
                scaleY = appearScale
            }
            .size(sizeDp)
            .clip(CircleShape)
            .background(cs.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = cs.onPrimaryContainer,
        )
    }
}