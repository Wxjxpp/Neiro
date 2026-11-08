package com.wxjxpp.neiro.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs

/**
 * 帧级插值播放时钟（与 [AccompanistLyricsPane] 内部的算法一致）。
 *
 * 播放器上报的进度采样频率低（数百毫秒一次），直接喂给逐字染色会一卡一卡；
 * 这里锚定最近一次外部进度，播放中按真实流逝时间每帧外推：
 * - 大偏差（seek / 起播 / 缓冲）→ 硬锚定；
 * - 小漂移 → 渐进吸收，避免每次采样都回跳（锯齿）。
 *
 * 暂停时不跑逐帧循环，直接跟随外部进度，省电。
 */
@Composable
fun rememberSmoothPositionMs(positionMs: Long, isPlaying: Boolean): LongState {
    val positionState = rememberUpdatedState(positionMs)
    val smooth = remember { mutableLongStateOf(positionMs) }

    // 暂停 / 拖动进度条期间：直接跟随外部进度
    LaunchedEffect(positionMs, isPlaying) {
        if (!isPlaying) smooth.longValue = positionMs
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var anchorPos = positionState.value
        var anchorNanos = withFrameNanos { it }
        var lastExternal = anchorPos
        smooth.longValue = anchorPos
        while (true) {
            withFrameNanos { frame ->
                val external = positionState.value
                if (external != lastExternal) {
                    lastExternal = external
                    val drift = smooth.longValue - external
                    if (abs(drift) > 300) {
                        anchorPos = external
                        anchorNanos = frame
                        smooth.longValue = external
                    } else {
                        anchorPos -= drift / 8
                    }
                }
                smooth.longValue = anchorPos + (frame - anchorNanos) / 1_000_000L
            }
        }
    }
    return smooth
}
