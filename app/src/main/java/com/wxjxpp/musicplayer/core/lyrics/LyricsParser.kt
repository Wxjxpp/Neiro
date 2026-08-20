package com.wxjxpp.musicplayer.core.lyrics

import com.wxjxpp.musicplayer.core.model.Lyrics
import com.wxjxpp.musicplayer.core.model.LyricsFormat

/**
 * 歌词解析器契约。
 *
 * 每种格式一个实现（LRC / 增强型 LRC / TTML / SRT / ...），
 * 由 [LyricsParserRegistry] 按 [canParse] 自动挑选。
 *
 * 新增格式 = 新增一个 Parser + 注册，UI 与播放逻辑都不用动。
 */
interface LyricsParser {
    val format: LyricsFormat

    /** 能否解析这段内容。可以看文件扩展名，也可以嗅探内容特征。 */
    fun canParse(content: String, hint: String? = null): Boolean

    fun parse(content: String): Lyrics
}

/**
 * 解析器注册表。
 *
 * 解析时按注册顺序依次询问 [LyricsParser.canParse]，
 * 命中即用；全部不命中返回 [Lyrics.Empty]，不抛异常。
 */
class LyricsParserRegistry(
    parsers: List<LyricsParser> = emptyList(),
) {
    private val parsers = parsers.toMutableList()

    fun register(parser: LyricsParser) {
        parsers += parser
    }

    fun parse(content: String, hint: String? = null): Lyrics {
        if (content.isBlank()) return Lyrics.Empty
        val parser = parsers.firstOrNull { it.canParse(content, hint) } ?: return Lyrics.Empty
        return runCatching { parser.parse(content) }.getOrElse { Lyrics.Empty }
    }

    /**
     * 合并主歌词与独立提供的翻译 / 罗马音。
     *
     * 有些 API 把三者分开返回，这里按时间戳就近对齐。
     * TODO 接入真实数据后按需要调整对齐容差。
     */
    fun merge(
        main: Lyrics,
        translation: Lyrics? = null,
        romanization: Lyrics? = null,
        toleranceMs: Long = 400L,
    ): Lyrics {
        if (translation == null && romanization == null) return main
        val lines = main.lines.map { line ->
            line.copy(
                translation = line.translation
                    ?: translation?.lines?.nearest(line.startMs, toleranceMs)?.text,
                romanization = line.romanization
                    ?: romanization?.lines?.nearest(line.startMs, toleranceMs)?.text,
            )
        }
        return main.copy(lines = lines)
    }

    private fun List<com.wxjxpp.musicplayer.core.model.LyricLine>.nearest(
        timeMs: Long,
        toleranceMs: Long,
    ) = minByOrNull { kotlin.math.abs(it.startMs - timeMs) }
        ?.takeIf { kotlin.math.abs(it.startMs - timeMs) <= toleranceMs }
}