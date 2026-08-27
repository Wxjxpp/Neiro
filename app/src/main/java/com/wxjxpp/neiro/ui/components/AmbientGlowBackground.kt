package com.wxjxpp.neiro.ui.components
import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin
/**
 * 动态流光背景（澎湃壁纸风格，参考 miuix 实现）。
 *
 * 三层结构：
 * 1. **封面位图铺底**：加载封面原图 → 缩小采样 → RenderEffect 级别的
 *    重度模糊（用低分辨率放大替代，等效高斯模糊且零 GPU 成本），
 *    这是"动态"的根基——颜色来自真实封面而非单色近似；
 * 2. **两个大光斑**：从封面主色的邻近色相派生，以 20s+ 周期缓慢漂移，
 *    叠加出流动的光感；
 * 3. **顶部/底部暗角**：保证控制区文字可读。
 *
 * [coverUri] 为空或加载失败时退化为纯 seed 色渐变漂移。
 */
@Composable
fun AmbientGlowBackground(
    baseColor: Color,
    coverUri: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (!enabled) return
    val context = LocalContext.current
    // 异步加载封面缩略图（inSampleSize=48：3000px 原图缩到 ~64px，天然模糊 + 极低内存）
    val coverBitmap by produceState<ImageBitmap?>(initialValue = null, coverUri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 48 }
                val uri = coverUri?.takeIf { it.isNotBlank() } ?: return@runCatching null
                when {
                    uri.startsWith("content://") ->
                        context.contentResolver.openInputStream(android.net.Uri.parse(uri))
                            ?.use { BitmapFactory.decodeStream(it, null, opts) }
                    // 网络歌曲封面走 Coil（原实现只支持本地路径，在线歌直接加载失败）
                    uri.startsWith("http") -> {
                        val request = coil.request.ImageRequest.Builder(context)
                            .data(uri)
                            .allowHardware(false)
                            .build()
                        (coil.Coil.imageLoader(context).execute(request)
                            as? coil.request.SuccessResult)
                            ?.drawable.let { it as? android.graphics.drawable.BitmapDrawable }?.bitmap
                    }
                    else -> BitmapFactory.decodeFile(uri, opts)
                }?.asImageBitmap()
            }.getOrNull()
        }
    }
    // 从 seed 色派生两个邻近色相光斑（主题自适应强度：浅色鲜亮/暗色深沉）
    // 主题判定必须留在 Composable 作用域——Canvas 的 draw lambda 不是 @Composable 上下文，
    // 在里面读 MaterialTheme 会编译失败
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val glowAlphaA = if (isDarkTheme) 0.55f else 0.85f
    val glowAlphaB = if (isDarkTheme) 0.45f else 0.72f
    val glowBlend = if (isDarkTheme) {
        androidx.compose.ui.graphics.BlendMode.Plus
    } else {
        androidx.compose.ui.graphics.BlendMode.SrcOver
    }
    val spotA = remember(baseColor, glowAlphaA) { baseColor.copy(alpha = glowAlphaA).shiftHue(24f) }
    val spotB = remember(baseColor, glowAlphaB) { baseColor.copy(alpha = glowAlphaB).shiftHue(-32f) }
    val transition = rememberInfiniteTransition(label = "ambientGlow")
    // 光斑相位：周期长、错开，避免同步感
    val phase1 by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(21_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase1",
    )
    val phase2 by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(29_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase2",
    )
    // ---- 性能重构（v5）：三层拆分，动画层只做 GPU 变换 ----
    //
    // 旧实现把 phase1/phase2 读进单个 Canvas 的 draw lambda：每帧都要重新
    // 构造两个全屏 radialGradient Shader、重新缩放绘制封面位图、重画暗角，
    // 全屏重绘 60 次/秒。这是播放页掉帧的主要来源。
    //
    // 现在：静态层（封面底 + 暗角）用 drawWithCache 缓存绘制指令，只在尺寸/
    // 位图变化时重绘一次；两个光斑放进独立 Canvas，动画值**只在
    // graphicsLayer block 里读取**——只更新 RenderNode 变换属性，不触发重组
    // 也不触发重绘，位移交给 GPU 合成器。
    androidx.compose.foundation.layout.Box(modifier = modifier) {
        // 第 1 层：封面位图铺底（低分辨率放大 = 天然重度模糊），静态
        // alpha 0.45：给光斑留出透出空间（0.85 会把光斑完全盖死）
        Canvas(
            modifier = androidx.compose.ui.Modifier
                .matchParentSize()
                .drawWithCache {
                    val bmp = coverBitmap
                    val fallback = Brush.linearGradient(
                        listOf(
                            baseColor.copy(alpha = 0.5f),
                            baseColor.copy(alpha = 0.2f),
                            Color.Black.copy(alpha = 0.3f),
                        ),
                    )
                    onDrawBehind {
                        if (bmp != null) drawCoverBlurred(bmp, alpha = 0.45f) else drawRect(fallback)
                    }
                },
        ) {}
        // 第 2 层：两个大光斑。绘制静态（固定相位），漂移由整层旋转 + 呼吸缩放产生。
        // 旋转会把内容转出视口，故光斑半径与偏移量都按对角线留足冗余。
        Canvas(
            modifier = androidx.compose.ui.Modifier
                .matchParentSize()
                .graphicsLayer {
                    rotationZ = phase1 * 57.2958f // 弧度→度，21s 一圈
                    val s = 1f + 0.10f * sin(phase2)
                    scaleX = s
                    scaleY = s
                    // 光斑用 Plus/SrcOver 叠加，隔离到离屏层避免与下层反复混合
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                }
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val rA = maxOf(w, h) * 0.75f
                    val rB = maxOf(w, h) * 0.65f
                    val cA = Offset(w * 0.5f, h * 0.42f)
                    val cB = Offset(w * 0.5f + w * 0.18f, h * 0.62f)
                    val brushA = Brush.radialGradient(
                        colors = listOf(spotA, Color.Transparent),
                        center = cA,
                        radius = rA,
                    )
                    val brushB = Brush.radialGradient(
                        colors = listOf(spotB, Color.Transparent),
                        center = cB,
                        radius = rB,
                    )
                    onDrawBehind {
                        drawCircle(brush = brushA, radius = rA, center = cA, blendMode = glowBlend)
                        drawCircle(brush = brushB, radius = rB, center = cB, blendMode = glowBlend)
                    }
                },
        ) {}
        // 第 3 层：上下暗角收边（静态）
        Canvas(
            modifier = androidx.compose.ui.Modifier
                .matchParentSize()
                .drawWithCache {
                    val vignette = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.18f),
                        0.35f to Color.Transparent,
                        0.62f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.26f),
                    )
                    onDrawBehind { drawRect(vignette) }
                },
        ) {}
    }
}
/** 把位图按 Cover 模式铺满画布。位图本身只有几十像素宽，放大后即重度模糊效果。 */
private fun DrawScope.drawCoverBlurred(bitmap: ImageBitmap, alpha: Float) {
    val scale = maxOf(size.width / bitmap.width, size.height / bitmap.height)
    val dstW = bitmap.width * scale
    val dstH = bitmap.height * scale
    val dstOffset = Offset((size.width - dstW) / 2f, (size.height - dstH) / 2f)
    drawImage(
        image = bitmap,
        dstSize = androidx.compose.ui.unit.IntSize(dstW.toInt(), dstH.toInt()),
        dstOffset = androidx.compose.ui.unit.IntOffset(dstOffset.x.toInt(), dstOffset.y.toInt()),
        alpha = alpha,
        filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
    )
}
/** 径向渐变大光斑。 */
private fun DrawScope.drawSpot(center: Offset, radius: Float, color: Color, blendMode: androidx.compose.ui.graphics.BlendMode = androidx.compose.ui.graphics.BlendMode.SrcOver) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
        blendMode = blendMode,
    )
}
/** HSL 色相偏移，用于从主色派生邻近色。 */
private fun Color.shiftHue(degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()),
        hsv,
    )
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}