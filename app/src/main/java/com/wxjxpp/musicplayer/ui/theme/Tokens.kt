package com.wxjxpp.musicplayer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val AppTypography = Typography()

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * 布局与形状 token。
 *
 * 播放栏、悬浮栏、封面等尺寸集中在此，UI 改版只需要改这里，
 * 不需要翻页面代码。
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
    val playerBarCoverRadius: Dp = 12.dp,

    val floatingBarMargin: Dp = 12.dp,
    val floatingBarBottomMargin: Dp = 16.dp,
    val floatingBarRadius: Dp = 28.dp,
    val floatingBarElevation: Dp = 6.dp,

    val detailCoverSize: Dp = 280.dp,
    val detailCoverRadius: Dp = 24.dp,

    val listItemHeight: Dp = 64.dp,
    val listCoverSize: Dp = 48.dp,
)

/** 动画时长 token，统一控制过渡节奏。 */
@Immutable
data class AppMotion(
    val fast: Int = 160,
    val medium: Int = 260,
    val slow: Int = 420,
)

@Immutable
data class AppTokens(
    val dimens: AppDimens = AppDimens(),
    val motion: AppMotion = AppMotion(),
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

/** 页面里用 AppTheme.dimens / AppTheme.motion 取值。 */
object AppTheme {
    val dimens: AppDimens
        @Composable get() = LocalAppTokens.current.dimens

    val motion: AppMotion
        @Composable get() = LocalAppTokens.current.motion
}