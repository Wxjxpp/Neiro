package com.wxjxpp.neiro.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
/**
 * Expr：流体渐变背景（多点取色版）。
 *
 * 输入：封面多点取样得到的明亮色板（≥5 色）。
 * 效果：每个颜色是一个大尺寸径向光斑，以不同周期/相位在屏幕上做李萨如漂移，
 * 相互叠加融合（SrcOver 高透明度渐进），视觉上近似“流体”色彩交汇。
 * 底层用最亮主色的极浅化版本铺底（明亮风），整体观感鲜艳、明快。
 */
@Composable
fun FluidGlowBackground(
    palette: List<Color>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (!enabled || palette.isEmpty()) return
    // 底色：取最亮主色向白提亮 72% —— 明亮画布，鲜艳光斑在其上融合
    val base = remember(palette) {
        palette.maxByOrNull { it.luminance() } ?: palette.first()
    }
    val lightBase = remember(base) {
        Color(androidx.core.graphics.ColorUtils.blendARGB(
            android.graphics.Color.argb(
                (base.alpha * 255).toInt(), (base.red * 255).toInt(), (base.green * 255).toInt(), (base.blue * 255).toInt(),
            ),
            android.graphics.Color.WHITE, 0.72f,
        ))
    }
    val transition = rememberInfiniteTransition(label = "fluid")
    // 每个光斑独立周期（18~34s），错开初始相位，避免同步感
    val phases = palette.take(6).mapIndexed { i, _ ->
        val period = 18_000 + i * 3_400
        transition.animateFloat(
            initialValue = i * 0.9f,
            targetValue = i * 0.9f + (2 * Math.PI).toFloat() * 2,
            animationSpec = infiniteRepeatable(tween(period, easing = LinearEasing), RepeatMode.Restart),
            label = "fluid$i",
        )
    }
    Canvas(modifier = modifier) {
        drawRect(Brush.verticalGradient(listOf(lightBase.copy(alpha = 0.96f), lightBase)))
        val w = size.width
        val h = size.height
        phases.forEachIndexed { i, phase ->
            val c = palette[i % palette.size]
            val p = phase.value
            val cx = w * (0.5f + 0.42f * kotlin.math.cos(p + i))
            val cy = h * (0.45f + 0.36f * kotlin.math.sin(p * 0.8f + i * 1.7f) / 2f)
            val r = maxOf(w, h) * (0.55f + 0.08f * i)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c.copy(alpha = 0.5f), c.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
        }
        // 轻微白色雾化层：让色彩交汇处呈现柔和的“融进水里”感
        drawRect(Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
            center = Offset(w * 0.5f, h * 0.42f),
            radius = maxOf(w, h) * 0.8f,
        ))
    }
}