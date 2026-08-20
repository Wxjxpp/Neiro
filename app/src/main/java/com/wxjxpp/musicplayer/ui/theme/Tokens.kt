package com.wxjxpp.musicplayer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 字体。需要自定义字型时在这里替换 FontFamily，页面不受影响。
 */
val AppTypography = Typography()

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

    val detailCoverSize: Dp = 280.dp,
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