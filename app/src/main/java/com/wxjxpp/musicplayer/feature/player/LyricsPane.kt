package com.wxjxpp.musicplayer.feature.player

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import com.wxjxpp.musicplayer.core.lyrics.SyncedLyricsMapper
import com.wxjxpp.musicplayer.core.model.Lyrics
import com.wxjxpp.musicplayer.ui.theme.AppTheme

/**
 * 歌词面板。
 *
 * 渲染交给 accompanist-lyrics-ui 的 [KaraokeLyricsView]：
 * - 带音节时间轴（AMLL TTML / 增强型 LRC / QRC）→ 音节级真逐字卡拉 OK
 * - 只有行时间轴（普通 LRC / SRT）→ 整行高亮 + 平滑滚动
 * - 间奏自动显示呼吸点，非焦点行渐隐模糊
 *
 * 本组件只做一件事：把内部 [Lyrics] 模型映射为 SyncedLyrics 并传入渲染器。
 */
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
    val dimens = AppTheme.dimens

    KaraokeLyricsView(
        listState = listState,
        lyrics = synced,
        currentPosition = currentPosition,
        onLineClicked = { /* 行点击：暂不做 seek，保留交互位 */ },
        onLinePressed = { },
        showTranslation = showTranslation,
        showPhonetic = false,
        textColor = MaterialTheme.colorScheme.primary,
        blendMode = BlendMode.SrcIn,
        useBlurEffect = false,
        normalLineTextStyle = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            textMotion = TextMotion.Animated,
        ),
        accompanimentLineTextStyle = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.Bold,
            textMotion = TextMotion.Animated,
        ),
        modifier = modifier,
    )
}