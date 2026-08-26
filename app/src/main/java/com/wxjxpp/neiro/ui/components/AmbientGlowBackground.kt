package com.wxjxpp.neiro.ui.components
import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    // 从 seed 色派生两个邻近色相光斑
    val spotA = remember(baseColor) { baseColor.copy(alpha = 0.55f).shiftHue(24f) }
    val spotB = remember(baseColor) { baseColor.copy(alpha = 0.45f).shiftHue(-32f) }
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
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // 第 1 层：封面位图铺底（低分辨率放大 = 天然重度模糊）
        coverBitmap?.let { bmp ->
            drawCoverBlurred(bmp, alpha = 0.85f)
        } ?: run {
            // 无封面：seed 色对角渐变兜底
            drawRect(
                Brush.linearGradient(
                    listOf(
                        baseColor.copy(alpha = 0.5f),
                        baseColor.copy(alpha = 0.2f),
                        Color.Black.copy(alpha = 0.3f),
                    )
                )
            )
        }
        // 第 2 层：两个大光斑缓慢漂移（李萨如轨迹）
        drawSpot(
            center = Offset(
                x = w * (0.5f + 0.38f * cos(phase1)),
                y = h * (0.42f + 0.30f * sin(phase1 * 2f) / 2f),
            ),
            radius = maxOf(w, h) * 0.75f,
            color = spotA,
        )
        drawSpot(
            center = Offset(
                x = w * (0.5f + 0.42f * cos(phase2 + 1.2f)),
                y = h * (0.58f + 0.34f * sin(phase2)),
            ),
            radius = maxOf(w, h) * 0.65f,
            color = spotB,
        )
        // 第 3 层：上下暗角，保证控制区可读性
        drawRect(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.35f),
                0.35f to Color.Transparent,
                0.62f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.45f),
            )
        )
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
private fun DrawScope.drawSpot(center: Offset, radius: Float, color: Color) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
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