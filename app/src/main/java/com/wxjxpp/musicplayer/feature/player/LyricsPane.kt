package com.wxjxpp.musicplayer.feature.player

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import com.wxjxpp.musicplayer.R
import com.wxjxpp.musicplayer.core.lyrics.SyncedLyricsMapper
import com.wxjxpp.musicplayer.core.model.Lyrics

/**
 * 歌词面板。
 *
 * 渲染交给 accompanist-lyrics-ui 的 [KaraokeLyricsView]：
 * - 带音节时间轴（AMLL TTML / 增强型 LRC / QRC）→ 音节级真逐字卡拉 OK
 * - 只有行时间轴（普通 LRC / SRT）→ 整行高亮 + 平滑滚动
 * - 间奏自动显示呼吸点，非焦点行渐隐模糊
 *
 * 本组件只做一件事：把内部 [Lyrics] 模型映射为 SyncedLyrics 并传入渲染器。
 *
 * **字体注意**：lyrics-ui 的 NativeTextEngine 需要字体文件字节来构建 SDF 图集，
 * 系统默认字体获取链路依赖 `SystemFonts.getAvailableFonts()`（API 29+），
 * 在 API < 29 上拿不到字节 → 图集为空 → 整页黑块。
 * 因此这里显式传一个打包进 APK 的字体（res/font），渲染器会通过
 * `Resources.openRawResource()` 反射读取，全版本可用。
 */
private val LyricsFontFamily = FontFamily(Font(R.font.noto_sans_sc_regular))
@Composable
fun LyricsPane(
    lyrics: Lyrics,
    positionMs: Long,
    modifier: Modifier = Modifier,
    showTranslation: Boolean = true,
) {
    if (lyrics.isEmpty) return
    val listState = rememberLazyListState()
    // offset 已在映射时应用，这里直接给原始播放位置
    val synced = remember(lyrics) { SyncedLyricsMapper.map(lyrics) }
    val currentPosition = remember(synced) { { positionMs.toInt() } }

    KaraokeLyricsView(
        listState = listState,
        lyrics = synced,
        currentPosition = currentPosition,
        onLineClicked = { /* 行点击：暂不做 seek，保留交互位 */ },
        onLinePressed = { },
        textColor = MaterialTheme.colorScheme.primary,
        blendMode = BlendMode.SrcIn,
        useBlurEffect = false,
        normalLineTextStyle = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            fontFamily = LyricsFontFamily,
            textMotion = TextMotion.Animated,
        ),
        accompanimentLineTextStyle = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.Bold,
            fontFamily = LyricsFontFamily,
            textMotion = TextMotion.Animated,
        ),
        modifier = modifier,
    )
}