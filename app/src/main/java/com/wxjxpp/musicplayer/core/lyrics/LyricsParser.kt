package com.wxjxpp.musicplayer.core.lyrics

import com.wxjxpp.musicplayer.core.model.LyricLine
import com.wxjxpp.musicplayer.core.model.Lyrics
import com.wxjxpp.musicplayer.core.model.LyricsFormat

/**
 * 解析提示常量。
 *
 * 在线音源只能声明"格式名"而给不出文件名，因此统一用这些小写常量作为 hint，
 * 各 Parser 的 canParse 同时认扩展名与这些常量。
 */
object LyricsHints {
    const val LRC = "lrc"
    const val ENHANCED_LRC = "enhanced-lrc"
    const val TTML = "ttml"
    const val SRT = "srt"
    const val PLAIN = "plain"
}

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

    /** 能否解析这段内容。可以看文件扩展名/格式提示，也可以嗅探内容特征。 */
    fun canParse(content: String, hint: String? = null): Boolean

    fun parse(content: String): Lyrics
}

/**
 * 解析器注册表。
 *
 * 解析时按注册顺序依次询问 [LyricsParser.canParse]，
 * 命中即用；解析结果为空时继续询问后面的解析器，
 * 全部不命中返回 [Lyrics.Empty]，不抛异常。
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
        for (parser in parsers) {
            if (!parser.canParse(content, hint)) continue
            val parsed = runCatching { parser.parse(content) }.getOrNull() ?: continue
            // 命中但解析不出内容（例如误判）时继续尝试下一个，避免直接返回空歌词
            if (!parsed.isEmpty) return parsed
        }
        return Lyrics.Empty
    }

    /**
     * 合并主歌词与独立提供的翻译 / 罗马音。
     *
     * 很多音源把三者分开返回（lyric / tlyric / rlyric），这里按时间戳就近对齐。
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

    /**
     * 解析在线音源返回的一组歌词文本。
     *
     * `lxlyric`（逐字）优先作为主歌词，缺失时退回 `lyric`；
     * 翻译与罗马音按时间对齐挂到主歌词行上。
     */
    fun parseRemote(
        lyric: String?,
        translation: String? = null,
        romanization: String? = null,
        wordByWord: String? = null,
    ): Lyrics {
        val main = wordByWord?.takeIf { it.isNotBlank() }
            ?.let { parse(it, LyricsHints.ENHANCED_LRC) }
            ?.takeIf { !it.isEmpty && it.isWordByWord }
            ?: lyric?.takeIf { it.isNotBlank() }?.let { parse(it, LyricsHints.LRC) }
            ?: return Lyrics.Empty
        if (main.isEmpty) return Lyrics.Empty
        return merge(
            main = main,
            translation = translation?.takeIf { it.isNotBlank() }?.let { parse(it, LyricsHints.LRC) },
            romanization = romanization?.takeIf { it.isNotBlank() }?.let { parse(it, LyricsHints.LRC) },
        )
    }

    private fun List<LyricLine>.nearest(timeMs: Long, toleranceMs: Long) =
        minByOrNull { kotlin.math.abs(it.startMs - timeMs) }
            ?.takeIf { kotlin.math.abs(it.startMs - timeMs) <= toleranceMs }
}
