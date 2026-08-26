package com.wxjxpp.neiro.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** 歌曲首字母分组索引表（A-Z + #）。 */
val AlphabetChars: List<Char> = ('A'..'Z').toList() + '#'

/** 从标题提取首字母分组键：英文归一化大写；非 A-Z 归入 '#'。 */
fun initialOf(title: String): Char {
    val c = title.trim().firstOrNull() ?: return '#'
    val upper = c.uppercaseChar()
    return if (upper in 'A'..'Z') upper else '#'
}

/**
 * 字母索引侧边栏（M3E）。
 *
 * 点击与沿条滑动触摸均支持：手指落在哪格就实时回调哪个字母；
 * 激活字母 primary 高亮，其余 onSurfaceVariant；27 格等高均匀分布。
 *
 * @param activeChar 当前激活字母（高亮 + 回调去重依据）。
 * @param onSelect 选中字母回调。
 * @param onDragState 手指按下=true / 全部抬起=false（联动指示球显隐）。
 */
@Composable
fun AlphabetSideBar(
    activeChar: Char?,
    onSelect: (Char) -> Unit,
    onDragState: (Boolean) -> Unit,
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
                    onDragState(true)
                    fun charAt(y: Float): Char = AlphabetChars[
                        ((y - barTop) / (barHeight / AlphabetChars.size))
                            .toInt().coerceIn(0, AlphabetChars.lastIndex)
                    ]
                    var last = charAt(down.position.y)
                    if (last != activeChar) onSelect(last)
                    // 沿条滑动：持续追踪手指所在格，换格即回调
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        val next = charAt(change.position.y)
                        if (next != last) {
                            last = next
                            onSelect(next)
                        }
                        change.consume()
                    }
                    onDragState(false)
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
 * 可拖拽指示小球（M3E）。
 *
 * 停靠屏幕右缘半透明常驻；拖拽跟手（命中区域仅球体自身，不遮挡列表手势）；
 * 松手后以 Spring 弹性曲线吸附到最近屏幕边缘（左或右）。
 * 显隐由 [visible] 经 spring 驱动 alpha 平滑过渡。
 */
@Composable
fun DraggableIndicatorBall(
    label: Char?,
    visible: Boolean,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 52.dp,
) {
    val density = LocalDensity.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var containerW by remember { mutableFloatStateOf(0f) }
    var containerH by remember { mutableFloatStateOf(0f) }
    // 小球中心点位置（px）；首次布局停靠右缘中部
    val center = remember { Animatable(Offset.Zero) }
    val radiusPx = with(density) { sizeDp.toPx() } / 2f
    val ballAlpha by animateFloatAsState(
        targetValue = if (visible) 0.95f else 0.45f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ballAlpha",
    )

    fun edgeTarget(): Offset = if (containerW <= 0f) {
        Offset.Zero
    } else {
        Offset(
            x = if (center.value.x < containerW / 2f) radiusPx else containerW - radiusPx,
            y = center.value.y.coerceIn(radiusPx, containerH - radiusPx),
        )
    }

    LaunchedEffect(containerW, containerH) {
        if (containerW > 0f && center.value == Offset.Zero) {
            center.snapTo(Offset(containerW - radiusPx, containerH / 2f))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                containerW = coords.size.width.toFloat()
                containerH = coords.size.height.toFloat()
            }
            .zIndex(5f),
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = center.value.x - size.width / 2f
                    translationY = center.value.y - size.height / 2f
                    alpha = ballAlpha
                }
                .shadow(elevation = 6.dp, shape = CircleShape)
                .size(sizeDp)
                .clip(CircleShape)
                .background(cs.primaryContainer)
                // 拖拽手势挂在球体自身：不拦截列表滚动
                .pointerInput(containerW, containerH, radiusPx) {
                    detectDragGestures(
                        onDragEnd = {
                            scope.launch { center.animateTo(edgeTarget(), edgeSpring()) }
                        },
                        onDragCancel = {
                            scope.launch { center.animateTo(edgeTarget(), edgeSpring()) }
                        },
                    ) { change, _ ->
                        change.consume()
                        val delta = change.positionChange()
                        val target = Offset(
                            (center.value.x + delta.x).coerceIn(radiusPx, containerW - radiusPx),
                            (center.value.y + delta.y).coerceIn(radiusPx, containerH - radiusPx),
                        )
                        // 非挂起回调内的同步定位：UNDISPATCHED 启动立即执行
                        scope.launch(start = CoroutineStart.UNDISPATCHED) { center.snapTo(target) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (label ?: '#').toString(),
                style = MaterialTheme.typography.titleMedium,
                color = cs.onPrimaryContainer,
            )
        }
    }
}

/** 松手吸附用的弹性曲线（MediumBouncy，规格要求的 Spring 弹性吸附）。 */
private fun edgeSpring() = spring<Offset>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
