package com.wxjxpp.neiro.feature.player

import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.wxjxpp.neiro.core.model.LyricLine
import com.wxjxpp.neiro.core.model.Lyrics

/**
 * 领域歌词模型 → Accompanist [SyncedLyrics] 转换器。
 *
 * 映射规则：
 * - 逐字行（syllables 非空）→ [KaraokeLine.MainKaraokeLine]，保留音节级时间轴，
 *   罗马音映射到 phonetic、翻译映射到 translation；
 * - 普通行 → [SyncedLine]（逐行高亮模式）；
 * - 整体 offset 在转换时统一减去，Accompanist 内部直接用绝对毫秒。
 */
fun Lyrics.toSyncedLyrics(title: String, artistName: String): SyncedLyrics {
    if (isEmpty) return SyncedLyrics(lines = emptyList(), title = title)
    val shift = offsetMs
    // 行结束时间优先级：显式 endMs → 下一行起点前留 60ms → 字数估算兜底。
    // （纯字数估算普遍短于真实演唱时长，曾导致句子未唱完就切下一句）
    val lines = lines.mapIndexed { index, line ->
        line.toSyncedLine(shift, lines.getOrNull(index + 1))
    }
    return SyncedLyrics(
        lines = lines,
        title = title,
        artists = artistName.takeIf { it.isNotBlank() }
            ?.let { listOf(com.mocharealm.accompanist.lyrics.core.model.Artist("singer", it)) },
    )
}

private fun LyricLine.toSyncedLine(offsetMs: Long, next: LyricLine?): ISyncedLine {
    val start = (startMs - offsetMs).toInt()
    val rawEnd = endMs
        ?: next?.startMs?.let { nextStart -> maxOf(nextStart - 60, startMs + 600) }
        ?: estimatedEndMs()
    val end = (rawEnd - offsetMs).toInt().coerceAtLeast(start + 1)
    return if (syllables.isEmpty()) {
        SyncedLine(
            content = text,
            translation = translation,
            start = start,
            end = end,
        )
    } else {
        KaraokeLine.MainKaraokeLine(
            syllables = syllables.map { s ->
                val sStart = (s.startMs - offsetMs).toInt()
                KaraokeSyllable(
                    content = if (text.any { it == ' ' || it == '\u00A0' } && !s.text.endsWith(' ') && !s.text.endsWith('\u00A0')) s.text + '\u00A0' else s.text,
                    start = sStart,
                    end = ((s.endMs ?: s.startMs + DEFAULT_SYLLABLE_DURATION_MS) - offsetMs)
                        .toInt()
                        .coerceAtLeast(sStart),
                )
            },
            translation = translation,
            alignment = when (agent.isNullOrBlank()) {
                true -> KaraokeAlignment.Unspecified
                false -> KaraokeAlignment.Start
            },
            start = start,
            end = end,
            phonetic = romanization,
        )
    }
}

/** 无显式结束时间的行：按字数估算朗读时长（260ms/字），下限 800ms 上限 6s。 */
private fun LyricLine.estimatedEndMs(): Long =
    startMs + (text.length * 260L).coerceIn(800L, 6_000L)

private const val DEFAULT_SYLLABLE_DURATION_MS = 400L