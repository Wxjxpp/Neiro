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
 * 结构要点：
 * - `<p begin end ttm:agent>` 是一行
 * - `<span begin end>` 是音节；span 可以嵌套（父 span 只是分组）
 * - `<span ttm:role="x-translation">` 是翻译，`x-roman` 是罗马音，
 *   它们不属于正文，必须单独抽出来，否则正文会被污染
 * - `<span ttm:role="x-bg">` 是和声，按正文处理（通常自带括号）
 *
 * 时间格式支持 `hh:mm:ss.SSS` / `mm:ss.SSS` / `12.5s` / `120ms` / 纯秒数。
 */
class TtmlParser : LyricsParser {

    override val format: LyricsFormat = LyricsFormat.Ttml

    private companion object {
        const val ROLE_TRANSLATION = "x-translation"
        const val ROLE_ROMAN = "x-roman"
        val OFFSET_TIME = Regex("""^([\d.]+)(h|ms|m|s|f|t)$""")
    }

    override fun canParse(content: String, hint: String?): Boolean {
        val name = hint?.lowercase()
        if (name != null && (name.endsWith(".ttml") || name.endsWith(".xml") || name == LyricsHints.TTML)) {
            return content.contains("<tt", ignoreCase = true)
        }
        return content.contains("<tt", ignoreCase = true) && content.contains("<p", ignoreCase = true)
    }

    override fun parse(content: String): Lyrics {
        val parser = Xml.newPullParser().apply {
            // 关闭命名空间处理，属性名保留 `ttm:` 前缀，匹配更直接
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(content.trim()))
        }

        val lines = mutableListOf<LyricLine>()
        var timing: Boolean = false

        // 当前行状态
        var lineBegin: Long? = null
        var lineEnd: Long? = null
        var agent: String? = null
        var inLine = false
        val lineText = StringBuilder()
        val translation = StringBuilder()
        val romanization = StringBuilder()
        val syllables = mutableListOf<LyricSyllable>()
        val stack = ArrayDeque<Frame>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.localName()) {
                    "p" -> {
                        inLine = true
                        lineBegin = parseTime(parser.attr("begin"))
                        lineEnd = parseTime(parser.attr("end"))
                        agent = parser.attr("ttm:agent") ?: parser.attr("agent")
                        lineText.setLength(0)
                        translation.setLength(0)
                        romanization.setLength(0)
                        syllables.clear()
                        stack.clear()
                    }

                    "span" -> if (inLine) {
                        val parent = stack.lastOrNull()
                        val role = (parser.attr("ttm:role") ?: parser.attr("role"))
                            ?.lowercase()
                            ?: parent?.role
                        stack.addLast(
                            Frame(
                                role = role,
                                begin = parseTime(parser.attr("begin")),
                                end = parseTime(parser.attr("end")),
                            )
                        )
                    }

                    "br" -> if (inLine) {
                        (stack.lastOrNull()?.text ?: lineText).append('\n')
                    }
                }

                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inLine) {
                    val text = parser.text ?: ""
                    if (text.isNotEmpty()) (stack.lastOrNull()?.text ?: lineText).append(text)
                }

                XmlPullParser.ENTITY_REF -> if (inLine) {
                    val text = parser.text ?: ""
                    if (text.isNotEmpty()) (stack.lastOrNull()?.text ?: lineText).append(text)
                }

                XmlPullParser.END_TAG -> when (parser.localName()) {
                    "span" -> if (inLine && stack.isNotEmpty()) {
                        val frame = stack.removeLast()
                        val text = frame.text.toString()
                        when (frame.role) {
                            ROLE_TRANSLATION -> translation.appendSegment(text)
                            ROLE_ROMAN, "x-romanization" -> romanization.appendSegment(text)
                            else -> {
                                (stack.lastOrNull()?.text ?: lineText).append(text)
                                // 只有叶子 span 才是真正的音节，避免父子重复计时
                                if (!frame.hasTimedChild && frame.begin != null && text.isNotBlank()) {
                                    syllables += LyricSyllable(
                                        text = text,
                                        startMs = frame.begin,
                                        endMs = frame.end,
                                    )
                                    timing = true
                                }
                            }
                        }
                        if (frame.begin != null) stack.lastOrNull()?.hasTimedChild = true
                    }

                    "p" -> if (inLine) {
                        inLine = false
                        // 未闭合的 span 兜底并入正文
                        while (stack.isNotEmpty()) {
                            val frame = stack.removeLast()
                            (stack.lastOrNull()?.text ?: lineText).append(frame.text)
                        }
                        val text = lineText.toString().trim()
                        val start = lineBegin ?: syllables.firstOrNull()?.startMs
                        if (text.isNotEmpty() && start != null) {
                            lines += LyricLine(
                                startMs = start,
                                endMs = lineEnd ?: syllables.lastOrNull()?.endMs,
                                text = text,
                                translation = translation.toString().trim().takeIf { it.isNotEmpty() },
                                romanization = romanization.toString().trim().takeIf { it.isNotEmpty() },
                                syllables = syllables.toList(),
                                agent = agent,
                            )
                        }
                    }
                }
            }
            event = parser.next()
        }

        return Lyrics(
            format = LyricsFormat.Ttml,
            lines = lines.sortedBy { it.startMs },
            metadata = if (timing) mapOf("wordByWord" to "true") else emptyMap(),
        )
    }

    private class Frame(
        val role: String?,
        val begin: Long?,
        val end: Long?,
        val text: StringBuilder = StringBuilder(),
        var hasTimedChild: Boolean = false,
    )

    /** 追加一段翻译/罗马音，多段之间补空格。 */
    private fun StringBuilder.appendSegment(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (isNotEmpty()) append(' ')
        append(trimmed)
    }

    /** 关闭命名空间后 name 形如 `tt:p`，这里统一取冒号后的部分。 */
    private fun XmlPullParser.localName(): String {
        val raw = name ?: return ""
        val index = raw.indexOf(':')
        return if (index >= 0) raw.substring(index + 1) else raw
    }

    /** 取属性：先按带前缀的原名，再退化为无前缀匹配。 */
    private fun XmlPullParser.attr(name: String): String? {
        getAttributeValue(null, name)?.let { return it.takeIf { v -> v.isNotBlank() } }
        val bare = name.substringAfter(':')
        for (i in 0 until attributeCount) {
            val attrName = getAttributeName(i) ?: continue
            if (attrName == name || attrName.substringAfter(':') == bare) {
                return getAttributeValue(i)?.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    /** 支持 `hh:mm:ss.SSS` / `mm:ss.SSS` / `12.5s` / `120ms` / `12.5`。 */
    private fun parseTime(raw: String?): Long? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        OFFSET_TIME.find(value)?.let { m ->
            val amount = m.groupValues[1].toDoubleOrNull() ?: return null
            return when (m.groupValues[2]) {
                "h" -> (amount * 3_600_000).toLong()
                "m" -> (amount * 60_000).toLong()
                "s" -> (amount * 1_000).toLong()
                "ms" -> amount.toLong()
                // f = 帧、t = tick，缺少 frameRate 无法换算，按 0 处理
                else -> null
            }
        }
        val parts = value.split(":")
        return runCatching {
            when (parts.size) {
                3 -> parts[0].toLong() * 3_600_000 +
                    parts[1].toLong() * 60_000 +
                    (parts[2].toDouble() * 1000).toLong()

                2 -> parts[0].toLong() * 60_000 + (parts[1].toDouble() * 1000).toLong()
                1 -> (parts[0].toDouble() * 1000).toLong()
                else -> null
            }
        }.getOrNull()
    }
}

/** SRT 字幕解析器：外挂字幕形式的歌词。 */
class SrtParser : LyricsParser {

    override val format: LyricsFormat = LyricsFormat.Srt

    private val cueRegex = Regex(
        """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})"""
    )

    override fun canParse(content: String, hint: String?): Boolean {
        val name = hint?.lowercase()
        if (name != null && (name.endsWith(".srt") || name.endsWith(".vtt") || name == LyricsHints.SRT)) return true
        return cueRegex.containsMatchIn(content)
    }

    override fun parse(content: String): Lyrics {
        val lines = mutableListOf<LyricLine>()
        // 以空行分块
        content.split(Regex("\\r?\\n\\s*\\r?\\n")).forEach { block ->
            val blockLines = block.trim().lines().filter { it.isNotBlank() }
            if (blockLines.isEmpty()) return@forEach
            val match = blockLines.firstNotNullOfOrNull { cueRegex.find(it) } ?: return@forEach
            val g = match.groupValues
            val start = g[1].toLong() * 3_600_000 + g[2].toLong() * 60_000 +
                g[3].toLong() * 1000 + g[4].padEnd(3, '0').toLong()
            val end = g[5].toLong() * 3_600_000 + g[6].toLong() * 60_000 +
                g[7].toLong() * 1000 + g[8].padEnd(3, '0').toLong()
            val body = blockLines
                .dropWhile { !cueRegex.containsMatchIn(it) }
                .drop(1)
                .map { it.replace(Regex("</?[a-zA-Z/][^>]*>"), "").trim() }
                .filter { it.isNotEmpty() }
            if (body.isEmpty()) return@forEach
            lines += LyricLine(
                startMs = start,
                endMs = end,
                text = body.first(),
                // 双语字幕常见写法：第二行就是译文
                translation = body.getOrNull(1),
            )
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