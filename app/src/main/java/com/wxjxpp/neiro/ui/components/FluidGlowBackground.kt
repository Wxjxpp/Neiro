package com.wxjxpp.neiro.ui.components

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * 流体渐变背景（v6 重写）。
 *
 * ## v5 为什么会出现硬边三角形
 *
 * v5 把「底色 + 光斑」画在同一个 `graphicsLayer` 里再整层 `rotationZ`。
 * graphicsLayer 的绘制内容被裁剪到组件边界，**旋转后这个矩形边界本身
 * 就变成了画面里的斜切硬边** —— 截图里那两条从左上到右下的直线就是它。
 * 底色 `drawRect` 向外扩 35% 也救不了，因为扩出去的部分同样在裁剪之外。
 *
 * ## v6 结构：三层，各管一件事
 *
 * 1. **底色层**（不参与动画、不裁剪）：整屏铺封面主色的浅化版，永远填满，
 *    所以任何情况下都不可能露出硬边或空白。
 * 2. **光斑层**（参与动画）：只画数个 alpha 渐变到全透明的径向光斑，
 *    自身没有任何实心矩形，因此就算被边界裁剪也看不出接缝。
 *    动画改为**纯平移**（各光斑沿独立的慢速李萨如轨迹漂移），不再旋转整层。
 *    位移写在 `graphicsLayer` 的 `translationX/Y` 上 —— 只更新 RenderNode
 *    变换属性，不触发重组也不触发重绘，绘制指令由 [drawWithCache] 缓存。
 * 3. **柔化层**：API 31+ 用 `RenderEffect` 给光斑层加一次大半径高斯模糊，
 *    把渐变边界彻底抹平，得到真正"融进水里"的流体感；低版本自动跳过，
 *    退化为纯径向渐变叠加（仍然柔和，只是层次略少）。
 */
@Composable
fun FluidGlowBackground(
    palette: List<Color>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * 画布底色。由调用方传入沉浸配色方案的 `background`——它已经根据
     * **封面明度 + 系统深浅色模式**算好了该走浅底还是深底。
     *
     * v6 修复：旧实现在组件内部无条件 `blendARGB(主色, WHITE, 0.72f)`，
     * 于是深色模式下遇到白色封面也会渲染出米黄/粉的亮底 —— 用户报的
     * "暗色模式下背景是淡黄色"就是这里。底色的明暗决策不属于本组件，
     * 统一交给上层配色方案。
     */
    canvasColor: Color,
) {
    if (!enabled || palette.isEmpty()) return
    val darkCanvas = canvasColor.luminance() < 0.5f
    // 底色在传入画布色上做轻微的纵向渐变，制造纵深；不改变其明暗属性
    val baseTop = remember(canvasColor, darkCanvas) {
        val target = if (darkCanvas) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        Color(
            androidx.core.graphics.ColorUtils.blendARGB(canvasColor.toArgb(), target, 0.10f),
        )
    }
    val spots = remember(palette) { palette.take(4) }
    val transition = rememberInfiniteTransition(label = "fluid")
    // 单一相位驱动所有光斑：每个光斑用不同的频率系数与初相，轨迹互不同步。
    // 37s 一周期，慢到不会让人察觉在"动"，只感到画面在缓慢呼吸。
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(37_000, easing = LinearEasing), RepeatMode.Restart),
        label = "fluidPhase",
    )
    Box(modifier = modifier) {
        // ---- 第 1 层：底色，永远铺满，不参与动画 ----
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val bg = Brush.verticalGradient(listOf(baseTop, canvasColor))
                    onDrawBehind { drawRect(bg) }
                },
        ) {}
        // ---- 第 2 层：光斑组，单层平移 + 一次大半径模糊 ----
        //
        // 所有光斑画在**同一个** Canvas 里：只需要一个离屏缓冲和一次模糊，
        // 而不是每个光斑各来一次（4 个全屏 blur 会比 v5 更慢）。
        // 组内光斑位置静态、由 drawWithCache 缓存；整组做慢速平移 + 呼吸缩放，
        // 视觉上足够产生"色彩在缓慢交汇流动"的观感。
        //
        // 层内只有 alpha 收敛到全透明的径向渐变，没有任何实心矩形，
        // 因此即使内容被层边界裁掉也不会像 v5 那样出现斜切硬边。
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 只读动画值：仅更新 RenderNode 变换属性，不重绘
                    translationX = size.width * 0.10f * kotlin.math.cos(phase)
                    translationY = size.height * 0.07f * kotlin.math.sin(phase * 1.37f)
                    val s = 1.04f + 0.05f * kotlin.math.sin(phase * 0.61f)
                    scaleX = s
                    scaleY = s
                    compositingStrategy = CompositingStrategy.Offscreen
                    if (Build.VERSION.SDK_INT >= 31) {
                        // 大半径高斯模糊：把径向渐变的层次彻底抹开成流体交融。
                        // DECAL 让层外按透明处理，避免边缘颜色被拉伸复制。
                        renderEffect = android.graphics.RenderEffect
                            .createBlurEffect(80f, 80f, android.graphics.Shader.TileMode.DECAL)
                            .asComposeRenderEffect()
                    }
                }
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    // 四个象限错开分布；半径足够大保证互相重叠融合
                    val precomputed = spots.mapIndexed { i, color ->
                        val cx = w * (0.30f + 0.40f * (i % 2))
                        val cy = h * (0.26f + 0.46f * (i / 2))
                        val r = maxOf(w, h) * (0.50f + 0.07f * i)
                        val center = Offset(cx, cy)
                        // 三段式 alpha：中心较实 → 中段半透 → 边缘全透，
                        // 全透明收尾保证与相邻光斑和底色无缝融合
                        val brush = Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = 0.46f),
                                color.copy(alpha = 0.20f),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = r,
                        )
                        Triple(brush, center, r)
                    }
                    onDrawBehind {
                        precomputed.forEach { (brush, center, r) ->
                            drawCircle(brush = brush, radius = r, center = center)
                        }
                    }
                },
        ) {}
        // ---- 第 3 层：极轻的雾化，统一整体色调 ----
        // 深色画布上叠白会发灰，改叠黑做压角；浅色画布保持叠白
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val veil = if (darkCanvas) {
                        Color.Black.copy(alpha = 0.12f)
                    } else {
                        Color.White.copy(alpha = 0.10f)
                    }
                    val haze = Brush.radialGradient(
                        listOf(veil, Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.42f),
                        radius = maxOf(size.width, size.height) * 0.8f,
                    )
                    onDrawBehind { drawRect(haze) }
                },
        ) {}
    }
}