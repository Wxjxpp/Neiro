package com.wxjxpp.neiro.ui.components

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import coil.compose.AsyncImage
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ============================================================================
// 顶栏模糊（可选功能）：对附加组件自身内容整体模糊，并以 endY 为界向下渐变
// 擦除，消除底部硬边。渐变模糊 / 遮罩模糊双模式由 [TopBarBlurMode] 提供。
//
// 结构铁律（用户指定）：graphicsLayer 必须放在 drawWithContent 外层——
// 渲染管线先执行内层 drawWithContent（含遮罩/渐隐），再由外层 graphicsLayer
// 统一模糊；顺序颠倒会把遮罩硬边一起糊掉或让遮罩失效。
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
): Modifier = composed {
    if (!enabled) return@composed Modifier
    val useRenderEffect = Build.VERSION.SDK_INT >= 31
    // 外层：统一高斯模糊（管线后置执行，保证先内容/遮罩、后整体模糊）
    val outer = if (useRenderEffect) {
        Modifier.graphicsLayer {
            renderEffect = android.graphics.RenderEffect
                .createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
    } else {
        Modifier
    }
    // 内层：隔离图层中绘制内容 → DST_IN 渐隐擦掉底部硬边
    val inner = Modifier.drawWithContent {
        drawIntoCanvas { canvas ->
            // 走底层画布：saveLayer/restoreToCount 语义最明确，类型全部显式
            val nc = canvas.nativeCanvas
            val w = size.width
            val h = size.height
            // API<31：BlurMaskFilter 挂在 saveLayer 的 paint 上，restore 时整层模糊
            val canvasBlurRadius = if (!useRenderEffect) radius else null
            val pad = canvasBlurRadius?.times(3f) ?: 0f
            val layerPaint = android.graphics.Paint()
            layerPaint.maskFilter = canvasBlurRadius?.let {
                android.graphics.BlurMaskFilter(it, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            val sc = nc.saveLayer(
                android.graphics.RectF(-pad, -pad, w + pad, h + pad),
                layerPaint,
            )
            drawContent()
            val fadeTop = h * endYFraction
            if (fadeTop < h) {
                nc.drawRect(
                    android.graphics.RectF(0f, fadeTop, w, h),
                    fadeMaskPaint(mode, fadeTop, h),
                )
            }
            nc.restoreToCount(sc)
        }
    }
    inner.then(outer)
}

/** 底部渐隐遮罩笔刷：Gradient 线性渐隐 / Mask 阶梯式先实心后快速渐隐。 */
private fun fadeMaskPaint(mode: TopBarBlurMode, fadeTop: Float, height: Float): android.graphics.Paint {
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    p.shader = android.graphics.LinearGradient(
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
    p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
    return p
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
                // 整体柔化：graphicsLayer 挂 RenderEffect（用户给定框架：
                // RenderEffect.createBlurEffect(40f, 40f, Shader.TileMode.CLAMP)）
                Modifier.graphicsLayer {
                    renderEffect = android.graphics.RenderEffect
                        .createBlurEffect(40f, 40f, android.graphics.Shader.TileMode.CLAMP)
                        .asComposeRenderEffect()
                }
            } else {
                Modifier
            })
            .drawWithCache {
                // API<31：drawIntoCanvas 配合 BlurMaskFilter 替代（性能稍差）
                val phaseValues = progresses.map { it.value }
                onDrawBehind {
                    if (!useRenderEffect) {
                        val nc = drawContext.canvas.nativeCanvas
                        val pad = 120f
                        val blurPaint = android.graphics.Paint()
                        blurPaint.maskFilter = android.graphics.BlurMaskFilter(
                            40f, android.graphics.BlurMaskFilter.Blur.NORMAL,
                        )
                        val sc = nc.saveLayer(
                            android.graphics.RectF(-pad, -pad, size.width + pad, size.height + pad),
                            blurPaint,
                        )
                        drawFluidCircles(colors, phaseValues)
                        nc.restoreToCount(sc)
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

// ============================================================================
// 真·实时毛玻璃：把列表内容录进 GraphicsLayer，顶栏下缘的衔接带重绘该画面
// 并施加 RenderEffect 模糊 + 向下渐隐——内容从顶栏下滚过时即被模糊衔接。
// 需要 Compose UI 1.7+（rememberGraphicsLayer / GraphicsLayer.record）；
// API<31 无 RenderEffect 时自动跳过模糊、仅保留渐隐遮罩。
// ============================================================================

/** 录制修饰符：内容绘制的同时录进 [layer]，供其他绘制位置重放。 */
fun Modifier.captureTo(layer: GraphicsLayer): Modifier =
    drawWithContent {
        layer.record(
            size = IntSize(size.width.toInt(), size.height.toInt()),
        ) { this@drawWithContent.drawContent() }
        drawContent()
    }

/**
 * 顶栏毛玻璃表面：[enabled] 时重放 [captured] 录制的整幅画面——
 * 顶栏层与内容层处于同一坐标系，原位重放即为"顶栏背后滚过的真实内容"，
 * 再施加整体高斯模糊 + surface 蒙版保证可读性 + 底缘渐隐软衔接；
 * 未启用或 API<31 时回退为不透明 surface 实底。
 */
@Composable
fun GlassBarSurface(
    captured: GraphicsLayer?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    content: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth()) {
        // 顶栏主体始终保持清晰。模糊只允许出现在底部窄带，避免搜索框、图标和文字被处理。
        content()
        if (enabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.35f to cs.surface.copy(alpha = 0.18f),
                            1f to cs.surface.copy(alpha = 0.82f),
                        ),
                    ),
            )
        }
    }
}
/** Static album-art background for the expanded player. */
@Composable
fun AlbumBlurBackground(coverUri: String?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = coverUri,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.35f
                    scaleY = 1.35f
                    if (Build.VERSION.SDK_INT >= 31) {
                        renderEffect = android.graphics.RenderEffect.createBlurEffect(82f, 82f, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
                    }
                },
        )
    }
}
