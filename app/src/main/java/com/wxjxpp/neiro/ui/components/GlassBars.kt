package com.wxjxpp.neiro.ui.components

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.TileMode as AndroidTileMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ============================================================================
// 顶栏模糊（可选功能）：对附加组件自身内容整体模糊，并以 endY 为界向下渐变
// 擦除，消除底部硬边。渐变模糊 / 遮罩模糊双模式由 [TopBarBlurMode] 提供。
//
// 结构铁律（用户指定）：graphicsLayer 必须放在 drawWithContent 外层——
// 渲染管线先执行内层 drawWithContent（含遮罩/渐隐），再由外层 graphicsLayer
// 统一模糊，得到"先模糊再遮罩"的合成结果；顺序颠倒会糊掉硬边或让遮罩失效。
// ============================================================================

/** 顶栏模糊底部过渡模式。 */
enum class TopBarBlurMode {
    /** 渐变模糊：endY 之后线性渐变到全透明。 */
    Gradient,

    /** 遮罩模糊：endY 后先保持一段实心区，再快速渐隐（阶梯遮罩）。 */
    Mask,
}

/**
 * 顶栏毛玻璃修饰符。
 *
 * @param enabled 功能开关（设置页可控，默认关）。
 * @param mode 渐变模糊 / 遮罩模糊。
 * @param endYFraction 过渡终点 Y 占自身高度的比例；设为 0.6f 时底部 40% 区域渐变透明。
 * @param radius 模糊半径 px（API≥31 RenderEffect；API<31 降级 BlurMaskFilter，性能稍差）。
 */
fun Modifier.topBarBlur(
    enabled: Boolean,
    mode: TopBarBlurMode = TopBarBlurMode.Gradient,
    endYFraction: Float = 0.6f,
    radius: Float = 24f,
): Modifier = this.then(
    androidx.compose.ui.composed {
        if (!enabled) return@composed Modifier
        val useRenderEffect = Build.VERSION.SDK_INT >= 31
        // 外层：统一高斯模糊（管线后置执行，保证先内容/遮罩、后整体模糊）
        val outer = if (useRenderEffect) {
            Modifier.graphicsLayer {
                renderEffect = RenderEffect
                    .createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                    .asComposeRenderEffect()
            }
        } else {
            Modifier
        }
        // 内层：隔离图层中绘制内容 → DST_IN 渐隐擦掉底部硬边
        val inner = Modifier.drawWithContent {
            drawIntoCanvas { canvas ->
                val w = size.width
                val h = size.height
                // API<31：BlurMaskFilter 挂在 saveLayer 的 paint 上，restore 时整层模糊
                val canvasBlurRadius = if (!useRenderEffect) radius else null
                val pad = canvasBlurRadius?.times(3f) ?: 0f
                val layerPaint = Paint().also { p ->
                    p.asFrameworkPaint().maskFilter = canvasBlurRadius?.let {
                        BlurMaskFilter(it, BlurMaskFilter.Blur.NORMAL)
                    }
                }
                val sc = canvas.saveLayer(Rect(-pad, -pad, w + pad, h + pad), layerPaint)
                drawContent()
                val fadeTop = h * endYFraction
                if (fadeTop < h) {
                    canvas.drawRect(Rect(0f, fadeTop, w, h), fadeMaskPaint(mode, fadeTop, h))
                }
                canvas.restoreToCount(sc)
            }
        }
        inner.then(outer)
    }
)

/** 底部渐隐遮罩笔刷：Gradient 线性渐隐 / Mask 阶梯式先实心后快速渐隐。 */
private fun fadeMaskPaint(mode: TopBarBlurMode, fadeTop: Float, height: Float): android.graphics.Paint =
    Paint().asFrameworkPaint().apply {
        shader = android.graphics.LinearGradient(
            0f, fadeTop, 0f, height,
            intArrayOf(
                android.graphics.Color.BLACK,
                android.graphics.Color.BLACK,
                android.graphics.Color.TRANSPARENT,
            ),
            floatArrayOf(
                0f,
                if (mode == TopBarBlurMode.Mask) 0.55f else 0.12f,
                1f,
            ),
            android.graphics.Shader.TileMode.CLAMP,
        )
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
    }

// ============================================================================
// FluidBackground：Apple Music 风格流体渐变动态背景。
// 4-5 个大型径向渐变圆各自独立动画，沿李萨如曲线缓慢漂移、速度各不相同，
// 整体经 RenderEffect 高斯模糊柔化；API<31 降级 drawIntoCanvas + BlurMaskFilter。
// ============================================================================

@Composable
fun FluidBackground(
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    if (colors.isEmpty()) return
    val transition = rememberInfiniteTransition(label = "fluidBackground")
    // 圆数量收敛到规格要求的 4-5 个；每圆独立相位进度，周期互质化保证轨迹长期不重合
    val circleCount = colors.size.coerceIn(4, 5)
    val periods = remember(colors) {
        IntArray(circleCount) { 17_000 + it * 3_100 }
    }
    val progresses = periods.map { period ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(period, easing = LinearEasing)),
            label = "fluid_$period",
        )
    }
    val useRenderEffect = Build.VERSION.SDK_INT >= 31
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(if (useRenderEffect) {
                // 整体柔化：graphicsLayer 挂 RenderEffect（用户给定框架）
                Modifier.graphicsLayer {
                    renderEffect = RenderEffect
                        .createBlurEffect(40f, 40f, AndroidTileMode.CLAMP)
                        .asComposeRenderEffect()
                }
            } else {
                Modifier
            })
            .drawWithCache {
                // API<31：drawIntoCanvas 配合 BlurMaskFilter 替代（性能稍差）
                val lowApiBlur = if (!useRenderEffect) {
                    Paint().asFrameworkPaint().apply {
                        maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL)
                    }
                } else {
                    null
                }
                val phaseValues = progresses.map { it.value }
                onDrawBehind {
                    if (lowApiBlur != null) {
                        drawIntoCanvas { canvas ->
                            val pad = 120f
                            val sc = canvas.saveLayer(
                                Rect(-pad, -pad, size.width + pad, size.height + pad),
                                Paint().asFrameworkPaint().apply { maskFilter = lowApiBlur.maskFilter },
                            )
                            drawFluidCircles(colors, phaseValues)
                            canvas.restoreToCount(sc)
                        }
                    } else {
                        drawFluidCircles(colors, phaseValues)
                    }
                }
            },
    ) {
        // 绘制全部由上方 drawWithCache 承担；此处空实现仅满足 Canvas API 形参
    }
}

/** 李萨如漂移的径向渐变圆群：频率比互异 + 初相错开，运动缓慢且互不同步。 */
private fun DrawScope.drawFluidCircles(colors: List<Color>, phases: List<Float>) {
    val w = size.width
    val h = size.height
    val baseRadius = minOf(w, h) * 0.52f
    val freqPairs = listOf(1 to 2, 2 to 1, 3 to 2, 1 to 3)
    repeat(phases.size) { i ->
        val t = phases[i % phases.size] * 2f * PI.toFloat()
        val (fa, fb) = freqPairs[i % freqPairs.size]
        val ax = w * (0.22f + 0.03f * (i % 3))
        val ay = h * (0.20f + 0.04f * ((i + 1) % 3))
        val cx = w / 2f + ax * sin(fa * t + i * 1.7f)
        val cy = h / 2f + ay * cos(fb * t + i * 2.3f)
        val r = baseRadius * (1f + 0.16f * sin(t * 0.7f + i))
        val color = colors[i % colors.size]
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.82f), Color.Transparent),
                center = Offset(cx, cy),
                radius = r,
            ),
            radius = r,
            center = Offset(cx, cy),
        )
    }
}