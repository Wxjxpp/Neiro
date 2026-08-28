package com.wxjxpp.neiro.ui.components

import android.view.HapticFeedbackConstants
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.nio.charset.Charset

/** 歌曲首字母分组索引表（A-Z + #）。 */
val AlphabetChars: List<Char> = ('A'..'Z').toList() + '#'

/** 从标题提取首字母分组键：拉丁字母大写；常用汉字按 GB2312 一级字库映射拼音；其余归入 #。 */
fun initialOf(title: String): Char {
    val c = title.trim().firstOrNull() ?: return '#'
    val upper = c.uppercaseChar()
    if (upper in 'A'..'Z') return upper
    val bytes = c.toString().toByteArray(Charset.forName("GB2312"))
    if (bytes.size != 2) return '#'
    val zone = ((bytes[0].toInt() and 0xff) - 160) * 100 + ((bytes[1].toInt() and 0xff) - 160)
    val starts = intArrayOf(1601,1637,1833,2078,2274,2302,2433,2594,2787,3106,3212,3472,3635,3722,3730,3858,4027,4086,4390,4558,4684,4925,5249,5590)
    val letters = "ABCDEFGHJKLMNOPQRSTWXYZ"
    val i = starts.indexOfLast { zone >= it }
    return if (i >= 0 && i < letters.length) letters[i] else '#'
}

/**
 * 字母索引侧边栏（M3E）——单手势模型。
 *
 * 手指按住侧边栏即弹出指示球并跟手移动（含滑离侧边栏的屏幕内自由区）；
 * 纵向位置直接映射列表快速滚动；松手即消失（静止态不驻留）。
 *
 * 坐标约定（上一版错位的教训）：手势回调统一使用**条内局部 y（px）**——
 * [Modifier.pointerInput] 的 `position.y` 本就相对本节点，绝不再减根坐标；
 * 需要根坐标定位指示球时，通过 [onBarTopInRoot] / [onBarHeightInRoot] 上报，
 * 调用方与球共用同一套根坐标系，保证"球即手指位置"。
 *
 * @param heightFraction 侧边栏高度占可用空间比例（0.7f = 原整高的 70%）。
 * @param onSelect 手指所在字母回调（用于定位列表）。
 * @param onProgress 手指在条内的纵向比例 0..1（用于列表快滚映射与球定位）。
 * @param onTouch 手指按下=true / 松开=false（联动底栏下沉/回归）。
 * @param onBarTopInRoot 条顶部在根坐标系中的 y（px），布局变化时上报。
 * @param onBarHeightInRoot 条实际高度（px），布局变化时上报。
 */
@Composable
fun AlphabetSideBar(
    activeChar: Char?,
    onSelect: (Char) -> Unit,
    onProgress: (Float) -> Unit,
    onTouch: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    heightFraction: Float = 0.7f,
    onBarTopInRoot: (Float) -> Unit = {},
    onBarHeightInRoot: (Float) -> Unit = {},
) {
    var barHeight by remember { mutableFloatStateOf(1f) }
    // 触觉反馈：划过字母时单次 CLOCK_TICK 脉冲（轻点一下，拒绝长震）
    val view = LocalView.current
    var hapticChar by remember { mutableStateOf<Char?>(null) }

    Column(
        modifier = modifier
            .fillMaxHeight(heightFraction.coerceIn(0.3f, 1f))
            .width(30.dp)
            .onGloballyPositioned { coords ->
                val h = coords.size.height.toFloat().coerceAtLeast(1f)
                barHeight = h
                onBarTopInRoot(coords.positionInRoot().y)
                onBarHeightInRoot(h)
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onTouch(true)
                    // 局部坐标直接归一化：y 相对本节点，无需再减任何根坐标偏移
                    fun progressOf(y: Float): Float = (y / barHeight).coerceIn(0f, 1f)
                    fun charAt(y: Float): Char = AlphabetChars[
                        ((y / barHeight) * AlphabetChars.size)
                            .toInt().coerceIn(0, AlphabetChars.lastIndex)
                    ]
                    fun hapticTick(c: Char) {
                        if (c != hapticChar) {
                            hapticChar = c
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                    val startY = down.position.y
                    val startChar = charAt(startY)
                    onSelect(startChar)
                    hapticTick(startChar)
                    onProgress(progressOf(startY))
                    // 持续追踪：滑出侧边栏后仍在屏幕内跟踪（拖球快移），y 可能超出条范围需钳制
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        change.consume()
                        val y = change.position.y
                        val c = charAt(y.coerceIn(0f, barHeight))
                        onSelect(c)
                        hapticTick(c)
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
 * 纵向位置由调用方用根坐标换算后传入 [progress]（0..1，条内比例）；
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
    // 只按 label 判空：progress=0（手指在条顶）时球也要可见
    if (label == null) return
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