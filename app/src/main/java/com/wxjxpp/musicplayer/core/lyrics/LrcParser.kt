package com.wxjxpp.musicplayer.core.lyrics

import com.wxjxpp.musicplayer.core.model.LyricLine
import com.wxjxpp.musicplayer.core.model.LyricSyllable
import com.wxjxpp.musicplayer.core.model.Lyrics
import com.wxjxpp.musicplayer.core.model.LyricsFormat

/**
 * LRC / 增强型 LRC 解析器。
 *
 * 同时处理：
 * - 标准行时间戳 `[mm:ss.xx]`
 * - 一行多时间戳（重复副歌）
 * - 增强型逐字 `<mm:ss.xx>`
 * - 元数据 `[ti:]` `[ar:]` `[offset:]`
 * - 同一时间戳出现两次时，第二条视为翻译
 */
class LrcParser : LyricsParser {

    override val format: LyricsFormat = LyricsFormat.Lrc

    private val lineTimeRegex = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val wordTimeRegex = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val metaRegex = Regex("""^\[([a-zA-Z#]+):(.*)]$""")

    override fun canParse(content: String, hint: String?): Boolean {
        if (hint?.endsWith(".lrc", ignoreCase = true) == true) return true
        return lineTimeRegex.containsMatchIn(content)
    }

    override fun parse(content: String): Lyrics {
        val metadata = mutableMapOf<String, String>()
        var offsetMs = 0L
        // 同一时间戳可能有多行（原文 + 翻译）
        val buckets = linkedMapOf<Long, MutableList<String>>()
        var hasWordTiming = false

        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach

            metaRegex.matchEntire(line)?.let { m ->
                val key = m.groupValues[1].lowercase()
                val value = m.groupValues[2].trim()
                if (key == "offset") {
                    offsetMs = value.toLongOrNull() ?: 0L
                } else {
                    metadata[key] = value
                }
                return@forEach
            }

            val stamps = lineTimeRegex.findAll(line).map { it.toMillis() }.toList()
            if (stamps.isEmpty()) return@forEach

            val text = line.replace(lineTimeRegex, "").trim()
            if (wordTimeRegex.containsMatchIn(text)) hasWordTiming = true
            stamps.forEach { at ->
                buckets.getOrPut(at) { mutableListOf() } += text
            }
        }

        val lines = buckets.entries
            .sortedBy { it.key }
            .mapNotNull { (startMs, texts) ->
                val main = texts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val syllables = parseSyllables(main)
                LyricLine(
                    startMs = startMs,
                    text = stripWordTags(main),
                    translation = texts.getOrNull(1)
                        ?.let { stripWordTags(it) }
                        ?.takeIf { it.isNotBlank() },
                    syllables = syllables,
                )
            }

        return Lyrics(
            format = if (hasWordTiming) LyricsFormat.EnhancedLrc else LyricsFormat.Lrc,
            lines = lines,
            offsetMs = offsetMs,
            metadata = metadata,
        )
    }

    private fun parseSyllables(text: String): List<LyricSyllable> {
        val matches = wordTimeRegex.findAll(text).toList()
        if (matches.isEmpty()) return emptyList()

        val result = mutableListOf<LyricSyllable>()
        matches.forEachIndexed { index, match ->
            val start = match.toMillis()
            val from = match.range.last + 1
            val to = matches.getOrNull(index + 1)?.range?.first ?: text.length
            val word = text.substring(from, to)
            if (word.isNotEmpty()) {
                result += LyricSyllable(
                    text = word,
                    startMs = start,
                    endMs = matches.getOrNull(index + 1)?.toMillis(),
                )
            }
        }
        return result
    }

    private fun stripWordTags(text: String) = text.replace(wordTimeRegex, "").trim()

    private fun MatchResult.toMillis(): Long {
        val min = groupValues[1].toLongOrNull() ?: 0L
        val sec = groupValues[2].toLongOrNull() ?: 0L
        val fracRaw = groupValues.getOrNull(3).orEmpty()
        // ".5" -> 500ms, ".55" -> 550ms, ".555" -> 555ms
        val frac = when (fracRaw.length) {
            0 -> 0L
            1 -> fracRaw.toLong() * 100
            2 -> fracRaw.toLong() * 10
            else -> fracRaw.take(3).toLong()
        }
        return min * 60_000 + sec * 1_000 + frac
    }
}