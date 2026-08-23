package com.wxjxpp.neiro.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 字体。整体比 M3 默认缩小一档（用户反馈默认字号/图标过大）：
 * titleLarge 22→18、titleMedium 16→14、bodyLarge 16→14、
 * bodyMedium 14→13、bodySmall 12→11、labelLarge 14→12。
 *
 * 支持全局缩放（[fontScale]，0.8~1.4）与字族切换（[fontFamily]，
 * null = 跟随系统默认），由设置页实时下发。
 */
fun appTypography(
    fontScale: Float = 1f,
    fontFamily: FontFamily? = null,
): Typography {
    fun style(fontSize: Int, weight: FontWeight): TextStyle = TextStyle(
        fontFamily = fontFamily ?: FontFamily.Default,
        fontSize = (fontSize * fontScale).sp,
        fontWeight = weight,
    )
    return Typography(
        displayLarge = style(57, FontWeight.Normal),
        displayMedium = style(40, FontWeight.Normal),
        displaySmall = style(32, FontWeight.Normal),
        headlineLarge = style(28, FontWeight.Normal),
        headlineMedium = style(24, FontWeight.Normal),
        headlineSmall = style(20, FontWeight.Normal),
        titleLarge = style(18, FontWeight.Bold),
        titleMedium = style(14, FontWeight.SemiBold),
        titleSmall = style(12, FontWeight.Medium),
        bodyLarge = style(14, FontWeight.Normal),
        bodyMedium = style(13, FontWeight.Normal),
        bodySmall = style(11, FontWeight.Normal),
        labelLarge = style(12, FontWeight.Medium),
        labelMedium = style(11, FontWeight.Medium),
        labelSmall = style(10, FontWeight.Medium),
    )
}

/** 设置页可选的字体样式标识 → FontFamily 映射。 */
fun fontFamilyFor(id: String): FontFamily? = when (id) {
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    "cursive" -> FontFamily.Cursive
    else -> null
}

/**
 * M3 Expressive 形状档位。圆角整体偏大是 Expressive 的特征之一。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * 布局尺寸 token。
 *
 * 播放栏高度、封面尺寸、悬浮边距等全部集中在此。
 * 调 UI 只改这里，不要去页面里找 dp。
 */
@Immutable
data class AppDimens(
    val spaceXs: Dp = 4.dp,
    val spaceSm: Dp = 8.dp,
    val spaceMd: Dp = 12.dp,
    val spaceLg: Dp = 16.dp,
    val spaceXl: Dp = 24.dp,

    val playerBarHeight: Dp = 72.dp,
    val playerBarCoverSize: Dp = 52.dp,
    val playerBarCoverRadius: Dp = 14.dp,

    val floatingBarMargin: Dp = 12.dp,
    val floatingBarBottomMargin: Dp = 16.dp,
    val floatingBarRadius: Dp = 28.dp,
    val floatingBarElevation: Dp = 6.dp,

    val detailCoverSize: Dp = 240.dp,
    val detailCoverRadius: Dp = 28.dp,

    val listItemHeight: Dp = 68.dp,
    val listCoverSize: Dp = 48.dp,
)

@Immutable
data class AppTokens(
    val dimens: AppDimens = AppDimens(),
    val isDark: Boolean = false,
)

val LocalAppTokens: ProvidableCompositionLocal<AppTokens> =
    staticCompositionLocalOf { AppTokens() }

@Composable
fun ProvideAppTokens(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAppTokens provides AppTokens(isDark = darkTheme),
        content = content,
    )
}

/**
 * 页面取值入口。
 *
 * 尺寸走 AppTheme.dimens；动效不在这里定义，
 * 统一用 MaterialTheme.motionScheme（由 MaterialExpressiveTheme 下发）。
 */
object AppTheme {
    val dimens: AppDimens
        @Composable get() = LocalAppTokens.current.dimens
}