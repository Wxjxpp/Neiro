package com.wxjxpp.musicplayer.core.lyrics

import android.util.Xml
import com.wxjxpp.musicplayer.core.model.LyricLine
import com.wxjxpp.musicplayer.core.model.LyricSyllable
import com.wxjxpp.musicplayer.core.model.Lyrics
import com.wxjxpp.musicplayer.core.model.LyricsFormat
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * TTML 解析器（Apple Music 风格逐字歌词）。
 *
 * 结构：`<p begin end ttm:agent>` 为一行，内部 `<span begin end>` 为逐字音节。
 * 时间格式同时支持 `mm:ss.SSS` / `hh:mm:ss.SSS` 与 `12.5s` 这类 offset-time。
 */
class TtmlParser : LyricsParser {

    override val format: LyricsFormat = LyricsFormat.Ttml

    override fun canParse(content: String, hint: String?): Boolean {
        if (hint?.endsWith(".ttml", ignoreCase = true) == true) return true
        return content.contains("<tt", ignoreCase = true) && content.contains("ttml", ignoreCase = true)
    }

    override fun parse(content: String): Lyrics {
        val lines = mutableListOf<LyricLine>()
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(content))
        }

        var lineBegin: Long? = null
        var lineEnd: Long? = null
        var agent: String? = null
        val lineText = StringBuilder()
        val syllables = mutableListOf<LyricSyllable>()

        var spanBegin: Long? = null
        var spanEnd: Long? = null
        val spanText = StringBuilder()
        var inSpan = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "p" -> {
                        lineBegin = parseTime(parser.getAttributeValue(null, "begin"))
                        lineEnd = parseTime(parser.getAttributeValue(null, "end"))
                        agent = parser.getAttributeValue(null, "ttm:agent")
                            ?: parser.getAttributeValue(null, "agent")
                        lineText.setLength(0)
                        syllables.clear()
                    }

                    "span" -> {
                        inSpan = true
                        spanBegin = parseTime(parser.getAttributeValue(null, "begin"))
                        spanEnd = parseTime(parser.getAttributeValue(null, "end"))
                        spanText.setLength(0)
                    }

                    "br" -> lineText.append('\n')
                }

                XmlPullParser.TEXT -> {
                    val text = parser.text ?: ""
                    if (inSpan) spanText.append(text) else lineText.append(text)
                }

                XmlPullParser.END_TAG -> when (parser.name) {
                    "span" -> {
                        inSpan = false
                        val word = spanText.toString()
                        if (word.isNotEmpty()) {
                            lineText.append(word)
                            if (spanBegin != null) {
                                syllables += LyricSyllable(word, spanBegin, spanEnd)
                            }
                        }
                        spanText.setLength(0)
                    }

                    "p" -> {
                        val text = lineText.toString().trim()
                        if (text.isNotEmpty() && lineBegin != null) {
                            lines += LyricLine(
                                startMs = lineBegin,
                                endMs = lineEnd,
                                text = text,
                                syllables = syllables.toList(),
                                agent = agent,
                            )
                        }
                        lineText.setLength(0)
                        syllables.clear()
                    }
                }
            }
            event = parser.next()
        }

        return Lyrics(format = LyricsFormat.Ttml, lines = lines.sortedBy { it.startMs })
    }

    /** 支持 `hh:mm:ss.SSS` / `mm:ss.SSS` / `12.5s` / `120ms`。 */
    private fun parseTime(raw: String?): Long? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // offset-time：数字 + 单位
        Regex("""^([\d.]+)(h|m|s|ms|f|t)$""").find(value)?.let { m ->
            val amount = m.groupValues[1].toDoubleOrNull() ?: return null
            return when (m.groupValues[2]) {
                "h" -> (amount * 3_600_000).toLong()
                "m" -> (amount * 60_000).toLong()
                "s" -> (amount * 1_000).toLong()
                "ms" -> amount.toLong()
                else -> amount.toLong()
            }
        }
        // clock-time
        val parts = value.split(":")
        return runCatching {
            when (parts.size) {
                3 -> {
                    val h = parts[0].toLong()
                    val m = parts[1].toLong()
                    val s = parts[2].toDouble()
                    h * 3_600_000 + m * 60_000 + (s * 1000).toLong()
                }

                2 -> {
                    val m = parts[0].toLong()
                    val s = parts[1].toDouble()
                    m * 60_000 + (s * 1000).toLong()
                }

                1 -> (parts[0].toDouble() * 1000).toLong()
                else -> null
            }
        }.getOrNull()
    }
}

/** SRT 字幕解析器：外挂字幕形式的歌词。 */
class SrtParser : LyricsParser {

    override val format: LyricsFormat = LyricsFormat.Srt

    private val cueRegex = Regex("""(\d{2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,.](\d{3})""")

    override fun canParse(content: String, hint: String?): Boolean {
        if (hint?.endsWith(".srt", ignoreCase = true) == true) return true
        return cueRegex.containsMatchIn(content)
    }

    override fun parse(content: String): Lyrics {
        val lines = mutableListOf<LyricLine>()
        // SRT 以空行分块
        content.split(Regex("\\n\\s*\\n")).forEach { block ->
            val blockLines = block.trim().lines().filter { it.isNotBlank() }
            if (blockLines.isEmpty()) return@forEach
            val match = blockLines.firstNotNullOfOrNull { cueRegex.find(it) } ?: return@forEach
            val g = match.groupValues
            val start = g[1].toLong() * 3_600_000 + g[2].toLong() * 60_000 + g[3].toLong() * 1000 + g[4].toLong()
            val end = g[5].toLong() * 3_600_000 + g[6].toLong() * 60_000 + g[7].toLong() * 1000 + g[8].toLong()
            val text = blockLines
                .dropWhile { !cueRegex.containsMatchIn(it) }
                .drop(1)
                .joinToString("\n")
                .trim()
            if (text.isNotEmpty()) lines += LyricLine(startMs = start, endMs = end, text = text)
        }
        return Lyrics(format = LyricsFormat.Srt, lines = lines.sortedBy { it.startMs })
    }
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
            .map { LyricLine(startMs = 0L, text = it) }
            .toList(),
    )
}

/**
 * 默认解析器组合。顺序有意义：特征明确的放前面，纯文本兜底放最后。
 */
fun defaultLyricsParserRegistry(): LyricsParserRegistry = LyricsParserRegistry(
    listOf(
        TtmlParser(),
        SrtParser(),
        LrcParser(),
        PlainTextLyricsParser(),
    )
)