package com.wxjxpp.musicplayer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 动态流光背景（借鉴 miuix 的"澎湃"效果思路）。
 *
 * 用封面主色 [baseColor] 派生出 2-3 个邻近色相的光斑，
 * 以不同速度/轨迹缓慢漂移，叠加出流动的氛围光。
 * 纯 Canvas 实现，无位图采样开销；颜色由调用方从封面 seed 色派生。
 */
@Composable
fun AmbientGlowBackground(
    baseColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (!enabled) return
    val transition = rememberInfiniteTransition(label = "ambientGlow")
    // 三个光斑各自独立漂移，周期错开避免同步感
    val phase1 by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(17_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase1",
    )
    val phase2 by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(23_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase2",
    )
    val phase3 by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(29_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase3",
    )
    val base = baseColor
    val glowA = lerpHue(base, 0.12f)
    val glowB = lerpHue(base, -0.10f)
    val glowC = lerpHue(base, 0.05f).copy(alpha = 0.5f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // 底色铺满
        drawRect(Brush.verticalGradient(listOf(base.copy(alpha = 0.35f), Color.Transparent)))
        fun spot(phase: Float, rx: Float, ry: Float, radius: Float, color: Color) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(
                        x = w * (0.5f + rx * kotlin.math.cos(phase)),
                        y = h * (0.5f + ry * kotlin.math.sin(phase * 0.8f)),
                    ),
                    radius = radius,
                ),
                radius = radius,
            )
        }
        spot(phase1, 0.32f, 0.25f, w * 0.55f, glowA)
        spot(phase2, -0.30f, -0.20f, w * 0.60f, glowB)
        spot(phase3, 0.15f, -0.35f, w * 0.45f, glowC)
    }
}

/** 简单色相偏移：向白/黑两端插值模拟邻近色。 */
private fun lerpHue(color: Color, amount: Float): Color {
    val target = if (amount >= 0) Color.White else Color.Black
    val t = kotlin.math.abs(amount)
    return Color(
        red = color.red + (target.red - color.red) * t,
        green = color.green + (target.green - color.green) * t,
        blue = color.blue + (target.blue - color.blue) * t,
        alpha = color.alpha,
    )
}