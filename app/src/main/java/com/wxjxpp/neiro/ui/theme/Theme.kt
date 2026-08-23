package com.wxjxpp.neiro.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = SeedPrimaryDark,
    secondary = SeedSecondaryDark,
    tertiary = SeedTertiaryDark,
)

private val LightColors = lightColorScheme(
    primary = SeedPrimaryLight,
    secondary = SeedSecondaryLight,
    tertiary = SeedTertiaryLight,
)

/**
 * 应用主题入口，基于 Material 3 Expressive。
 *
 * 与旧版 MaterialTheme 的区别：
 * - 使用 [MaterialExpressiveTheme]，组件默认走 Expressive 形状与配色
 * - 额外提供 [MotionScheme]，弹性动效由主题统一下发，组件不各自写时长
 *
 * 页面只允许读取 MaterialTheme.* 与 [AppTheme] 暴露的 token，
 * 禁止硬编码颜色 / 尺寸，方便后续整体换肤。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    /** 全局字号缩放（0.8~1.4），设置页实时下发。 */
    fontScale: Float = 1f,
    /** 字体样式标识：default / serif / mono / cursive。 */
    fontFamilyId: String = "default",
    /** 想要更克制的动效时传 MotionScheme.standard()。 */
    motionScheme: MotionScheme = MotionScheme.expressive(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }
    // 字号/字族变化时重建 Typography，全局文本即时生效
    val typography = remember(fontScale, fontFamilyId) {
        appTypography(fontScale = fontScale, fontFamily = fontFamilyFor(fontFamilyId))
    }

    ProvideAppTokens(darkTheme = darkTheme) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = motionScheme,
            shapes = AppShapes,
            typography = typography,
            content = content,
        )
    }
}