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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance

/**
 * Expr：流体渐变背景（多点取色版）。
 *
 * ## 性能模型（v5 重写，本文件的核心约束）
 *
 * 旧实现把动画值读进 `Canvas` 的 draw lambda，导致**每一帧**都重新构造 6 个
 * 全屏尺寸的 `Brush.radialGradient`（每次一次 native Shader 分配 + 全屏渐变
 * 光栅化）。仅这一层就能吃掉中低端机整个 16ms 预算，是播放页卡顿的主因之一。
 *
 * 现在改为 **静态绘制 + GPU 变换**：
 * 1. 光斑内容用 [drawWithCache] 绘制，**不读取任何动画 state**——只在尺寸/
 *    调色板变化时重绘一次，Shader 只构造一次；
 * 2. 动画值只在 [graphicsLayer] 的 lambda 里读。Compose 对 graphicsLayer block
 *    的 state 读取做了特化：只更新 RenderNode 变换属性，**不重组、不重绘**，
 *    位移由 GPU 合成器完成；
 * 3. 光斑数 6 → 4（边际视觉收益极低，成本线性增长），底色按对角线放大预绘制，
 *    保证旋转/缩放时四角不露底。
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
        Color(
            androidx.core.graphics.ColorUtils.blendARGB(
                android.graphics.Color.argb(
                    (base.alpha * 255).toInt(),
                    (base.red * 255).toInt(),
                    (base.green * 255).toInt(),
                    (base.blue * 255).toInt(),
                ),
                android.graphics.Color.WHITE,
                0.72f,
            ),
        )
    }
    val spots = remember(palette) { palette.take(4) }
    val transition = rememberInfiniteTransition(label = "fluid")
    // 整层缓慢旋转（56s/圈）+ 呼吸缩放（23s 往复）：两者叠加后各光斑在屏幕上的
    // 相对轨迹仍是非周期漂移观感，但绘制只发生过一次。
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(56_000, easing = LinearEasing), RepeatMode.Restart),
        label = "fluidAngle",
    )
    val breath by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(23_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "fluidBreath",
    )
    Canvas(
        modifier = modifier
            .graphicsLayer {
                // 只在此处读动画 state：仅更新 RenderNode 变换，不触发重绘
                rotationZ = angle
                scaleX = breath
                scaleY = breath
                // 光斑之间是高透明叠加，隔离到离屏缓冲避免与下层反复混合
                compositingStrategy = CompositingStrategy.Offscreen
            }
            // 缓存绘制指令：不依赖动画值，尺寸/色板不变就不重绘、不重建 Shader
            .drawWithCache {
                val w = size.width
                val h = size.height
                val bg = Brush.verticalGradient(listOf(lightBase.copy(alpha = 0.96f), lightBase))
                // 旋转 + 放大后仍要铺满：底色向外扩 35% 预绘制
                val pad = maxOf(w, h) * 0.35f
                // 固定相位（不随时间变化），构成初始光斑分布
                val precomputed = spots.mapIndexed { i, c ->
                    val p = i * 1.31f
                    val cx = w * (0.5f + 0.40f * kotlin.math.cos(p + i))
                    val cy = h * (0.45f + 0.34f * kotlin.math.sin(p * 0.8f + i * 1.7f) / 2f)
                    val r = maxOf(w, h) * (0.52f + 0.08f * i)
                    val brush = Brush.radialGradient(
                        colors = listOf(c.copy(alpha = 0.5f), c.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = r,
                    )
                    Triple(brush, Offset(cx, cy), r)
                }
                val hazeBrush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.42f),
                    radius = maxOf(w, h) * 0.8f,
                )
                onDrawBehind {
                    drawRect(
                        brush = bg,
                        topLeft = Offset(-pad, -pad),
                        size = Size(w + pad * 2, h + pad * 2),
                    )
                    precomputed.forEach { (brush, center, r) ->
                        drawCircle(brush = brush, radius = r, center = center)
                    }
                    drawRect(hazeBrush)
                }
            },
    ) {}
}
