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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 字体。整体比 M3 默认缩小一档（用户反馈默认字号/图标过大）：
 * titleLarge 22→18、titleMedium 16→14、bodyLarge 16→14、
 * bodyMedium 14→13、bodySmall 12→11、labelLarge 14→12。
 */
val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 57.sp, fontWeight = FontWeight.Normal),
    displayMedium = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Normal),
    displaySmall = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Normal),
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Normal),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Normal),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium),
)

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