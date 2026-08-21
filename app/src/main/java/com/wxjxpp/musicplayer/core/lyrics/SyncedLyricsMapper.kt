package com.wxjxpp.musicplayer.core.lyrics

import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.wxjxpp.musicplayer.core.model.LyricLine
import com.wxjxpp.musicplayer.core.model.Lyrics

/**
 * 内部歌词模型 → accompanist-lyrics-core 模型转换（纯函数）。
 *
 * 渲染层（KaraokeLyricsView）只认 SyncedLyrics：
 * - 有音节时间轴的行 → KaraokeLine（真逐字，音节级卡拉 OK）
 * - 只有行时间轴的行 → SyncedLine（整行高亮）
 *
 * 结束时间缺失时用下一行起点补齐（间奏行则 +5s）；
 * 音节结束缺失时用下一音节起点（LrcParser 已做）或 300ms 兜底。
 */
object SyncedLyricsMapper {

    private const val FALLBACK_SYLLABLE_MS = 300L
    private const val FALLBACK_LINE_MS = 5_000L

    fun map(lyrics: Lyrics): SyncedLyrics {
        if (lyrics.lines.isEmpty()) return SyncedLyrics(emptyList())
        val offset = lyrics.offsetMs
        val lines = lyrics.lines.mapIndexed { index, line ->
            val nextStart = lyrics.lines.getOrNull(index + 1)?.startMs
            line.toSyncedLine(offset, nextStart)
        }
        return SyncedLyrics(lines)
    }

    private fun LyricLine.toSyncedLine(offsetMs: Long, nextLineStart: Long?): ISyncedLine {
        val start = (startMs + offsetMs).coerceAtLeast(0)
        val fallbackEnd = nextLineStart ?: (startMs + FALLBACK_LINE_MS)
        val end = ((endMs ?: fallbackEnd) + offsetMs).coerceAtLeast(start)
        val duration = (end - start).toInt().coerceAtLeast(200)

        return if (syllables.isNotEmpty()) {
            val syllables = syllables.map { syl ->
                val s = (syl.startMs + offsetMs).coerceAtLeast(start)
                val e = ((syl.endMs ?: (syl.startMs + FALLBACK_SYLLABLE_MS)) + offsetMs)
                    .coerceAtLeast(s)
                KaraokeSyllable(
                    content = syl.text,
                    start = s.toInt(),
                    duration = (e - s).toInt().coerceAtLeast(1),
                )
            }
            KaraokeLine.MainKaraokeLine(
                syllables = syllables,
                translation = translation,
                alignment = KaraokeAlignment.Unspecified,
                start = start.toInt(),
                duration = maxOf(duration, syllables.sumOf { it.duration }),
            )
        } else {
            SyncedLine(
                content = text,
                translation = translation,
                start = start.toInt(),
                duration = duration,
            )
        }
    }
}