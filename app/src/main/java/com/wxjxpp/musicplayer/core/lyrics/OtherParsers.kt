package com.wxjxpp.musicplayer.core.lyrics

import com.wxjxpp.musicplayer.core.model.Lyrics
import com.wxjxpp.musicplayer.core.model.LyricsFormat

/**
 * TTML（Apple Music 风格逐字歌词）解析器占位。
 *
 * TODO 用 XmlPullParser 实现：
 *   - <p begin= end= ttm:agent=> 映射为 LyricLine
 *   - 内层 <span begin= end=> 映射为 LyricSyllable
 *   - 处理 clock-time / offset-time 两种时间格式
 * 骨架先放在这里，接入时只改这一个文件。
 */
class TtmlParser : LyricsParser {

    override val format: LyricsFormat = LyricsFormat.Ttml

    override fun canParse(content: String, hint: String?): Boolean {
        if (hint?.endsWith(".ttml", ignoreCase = true) == true) return true
        return content.contains("<tt", ignoreCase = true) &&
            content.contains("http://www.w3.org/ns/ttml", ignoreCase = true)
    }

    override fun parse(content: String): Lyrics = Lyrics(format = LyricsFormat.Ttml)
}

/**
 * SRT 字幕解析器占位。外挂字幕形式的歌词会用到。
 */
class SrtParser : LyricsParser {

    override val format: LyricsFormat = LyricsFormat.Srt

    override fun canParse(content: String, hint: String?): Boolean {
        if (hint?.endsWith(".srt", ignoreCase = true) == true) return true
        return Regex("""\d{2}:\d{2}:\d{2},\d{3}\s*-->""").containsMatchIn(content)
    }

    override fun parse(content: String): Lyrics = Lyrics(format = LyricsFormat.Srt)
}

/** 无时间戳纯文本兜底，保证歌词面板至少能显示内容。 */
class PlainTextLyricsParser : LyricsParser {

    override val format: LyricsFormat = LyricsFormat.Plain

    override fun canParse(content: String, hint: String?): Boolean = content.isNotBlank()

    override fun parse(content: String): Lyrics = Lyrics(
        format = LyricsFormat.Plain,
        lines = content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { com.wxjxpp.musicplayer.core.model.LyricLine(startMs = 0L, text = it) }
            .toList(),
    )
}

/**
 * 默认解析器组合。
 *
 * 顺序有意义：特征明确的放前面，纯文本兜底放最后。
 */
fun defaultLyricsParserRegistry(): LyricsParserRegistry = LyricsParserRegistry(
    listOf(
        TtmlParser(),
        SrtParser(),
        LrcParser(),
        PlainTextLyricsParser(),
    )
)