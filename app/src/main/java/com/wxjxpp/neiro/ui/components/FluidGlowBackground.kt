package com.wxjxpp.neiro.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 流体渐变背景（v7：每个光斑独立流动，Apple Music 式“流光”）。
 *
 * ## v6 为什么“不像在流”
 *
 * v6 把所有光斑画在同一层里，**整组一起**做缓慢平移 + 缩放：光斑之间的相对位置
 * 永远不变，所以画面只是在“整体晃”，看不出颜色在交汇、流动。
 *
 * ## v7 做法
 *
 * 每个光斑有自己的李萨如轨迹：X / Y 方向各用不同的整数频率（1~3 圈 / 周期）、
 * 不同的初相和半径“呼吸”，光斑之间会不断靠近、重叠、分开 —— 颜色在交汇处混合，
 * 这就是流光感的来源。
 *
 * - **无缝循环**：所有频率都是整数，周期结束时每个光斑都精确回到起点，
 *   `RepeatMode.Restart` 不会有跳变。
 * - **不再需要 RenderEffect 模糊**：光斑本身就是多段 alpha 衰减到全透明的径向渐变，
 *   天然柔和；去掉整层 80px 模糊后，所有 API 级别的观感一致，GPU 开销也更低。
 * - **只重绘不重组**：动画值只在 Canvas 的绘制 lambda 里读取，每帧只触发绘制，
 *   不触发重组。
 * - 底色仍由调用方传入的 [canvasColor] 决定（明暗交给上层配色方案），
 *   光斑全是半透明叠加，不会出现 v5 的斜切硬边。
 */
@Composable
fun FluidGlowBackground(
    palette: List<Color>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * 画布底色。由调用方传入沉浸配色方案的 `background`——它已经根据
     * **封面明度 + 系统深浅色模式**算好了该走浅底还是深底。
     * 底色的明暗决策不属于本组件，统一交给上层配色方案。
     */
    canvasColor: Color,
) {
    if (!enabled || palette.isEmpty()) return
    val darkCanvas = canvasColor.luminance() < 0.5f
    val baseTop = remember(canvasColor, darkCanvas) {
        val target = if (darkCanvas) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        Color(androidx.core.graphics.ColorUtils.blendARGB(canvasColor.toArgb(), target, 0.10f))
    }
    // 每个光斑的颜色：封面取色板循环取用；取色板不足 5 色时，多出来的光斑用邻近色相补齐，
    // 避免几个光斑同色而看不出流动
    val blobColors = remember(palette) {
        List(BLOBS.size) { i ->
            val base = palette[BLOBS[i].colorIndex % palette.size]
            if (BLOBS[i].colorIndex < palette.size) base else base.shiftHue(if (i % 2 == 0) 26f else -26f)
        }
    }
    val transition = rememberInfiniteTransition(label = "fluid")
    // 一个周期 40s；各光斑频率是 1~3 的整数，对应 13~40s 绕一圈 —— 肉眼可见在流，但不躁
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart),
        label = "fluidPhase",
    )
    Canvas(modifier = modifier.fillMaxSize()) {
        // 只在绘制阶段读取动画值 → 每帧仅重绘，不重组
        val t = phase.value
        val w = size.width
        val h = size.height
        val m = max(w, h)
        // 底色：纵向轻渐变制造纵深，永远铺满
        drawRect(Brush.verticalGradient(listOf(baseTop, canvasColor)))
        BLOBS.forEachIndexed { i, b ->
            val cx = w * (b.anchorX + b.ampX * cos(t * b.kx + b.phase))
            val cy = h * (b.anchorY + b.ampY * sin(t * b.ky + b.phase * 1.7f))
            val r = m * (b.radius + 0.06f * sin(t * b.breath + b.phase))
            val c = blobColors[i]
            val center = Offset(cx, cy)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to c.copy(alpha = b.alpha),
                    0.40f to c.copy(alpha = b.alpha * 0.72f),
                    0.70f to c.copy(alpha = b.alpha * 0.28f),
                    1f to Color.Transparent,
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
        }
        // 底部压暗：保证下方控制区文字/图标的对比度（深色画布才需要）
        if (darkCanvas) {
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.60f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.35f),
                ),
            )
        }
    }
}

/**
 * 光斑参数。位置/振幅为占屏幕宽高的比例；kx / ky / breath 必须是整数以保证 40s 周期无缝循环。
 */
private class Blob(
    val anchorX: Float,
    val anchorY: Float,
    val ampX: Float,
    val ampY: Float,
    val kx: Int,
    val ky: Int,
    val phase: Float,
    val radius: Float,
    val breath: Int,
    val colorIndex: Int,
    val alpha: Float,
)

private val BLOBS = listOf(
    Blob(0.25f, 0.22f, 0.30f, 0.18f, 1, 2, 0.0f, 0.62f, 2, 0, 0.95f),
    Blob(0.78f, 0.30f, 0.26f, 0.20f, 2, 1, 1.3f, 0.58f, 3, 1, 0.85f),
    Blob(0.30f, 0.62f, 0.30f, 0.16f, 1, 3, 2.6f, 0.60f, 1, 2, 0.80f),
    Blob(0.75f, 0.70f, 0.24f, 0.20f, 3, 2, 3.9f, 0.56f, 2, 3, 0.80f),
    Blob(0.50f, 0.45f, 0.34f, 0.28f, 2, 3, 5.1f, 0.52f, 3, 4, 0.80f),
    Blob(0.55f, 0.90f, 0.30f, 0.08f, 1, 1, 0.7f, 0.60f, 1, 0, 0.70f),
)

/** HSV 色相偏移，用于取色板颜色不足时派生邻近色。 */
private fun Color.shiftHue(degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}
