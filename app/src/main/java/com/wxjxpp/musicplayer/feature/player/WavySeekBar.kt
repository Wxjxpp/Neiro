package com.wxjxpp.musicplayer.feature.player

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp

/**
 * Expressive 波形拖动条。
 *
 * 用一条 [LinearWavyProgressIndicator] 同时承担「显示进度」和「拖动定位」，
 * 避免波形条 + Slider 两条控件并存造成的割裂。
 *
 * [animated] 为 false（暂停或正在拖动）时波幅归零，退化为直线，
 * 不会在暂停时给出"仍在播放"的错觉。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavySeekBar(
    progress: Float,
    animated: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 轨道实际宽度，用于把手指 x 坐标换算成 0f..1f
    val trackWidthPx = remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .height(40.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val width = trackWidthPx.floatValue
                    if (width > 0f) {
                        onValueChange((offset.x / width).coerceIn(0f, 1f))
                        onValueChangeFinished()
                    }
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { onValueChangeFinished() },
                    onDragCancel = { onValueChangeFinished() },
                ) { change, _ ->
                    val width = trackWidthPx.floatValue
                    if (width > 0f) {
                        onValueChange((change.position.x / width).coerceIn(0f, 1f))
                    }
                }
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                trackWidthPx.floatValue = placeable.width.toFloat()
                layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
            },
        contentAlignment = Alignment.Center,
    ) {
        LinearWavyProgressIndicator(
            progress = { progress },
            amplitude = { if (animated) 1f else 0f },
            modifier = Modifier.fillMaxWidth().height(14.dp),
        )
    }
}