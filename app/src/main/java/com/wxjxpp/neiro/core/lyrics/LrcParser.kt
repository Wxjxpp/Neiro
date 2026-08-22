package com.wxjxpp.neiro.core.lyrics

import com.wxjxpp.neiro.core.model.LyricLine
import com.wxjxpp.neiro.core.model.LyricSyllable
import com.wxjxpp.neiro.core.model.Lyrics
import com.wxjxpp.neiro.core.model.LyricsFormat

/**
 * LRC / 增强型 LRC 解析器。
 *
 * 现实中的"增强型 LRC"至少有四种写法，这里全部兼容：
 *
 * 1. 标准增强型（A2 扩展）：`[00:12.34]<00:12.34>你<00:12.90>好`
 *    —— 逐字时间戳是"分:秒.毫秒"，绝对时间。
 * 2. LX / 酷狗 lxlyric：`[00:12.340]<12340,300>你<12640,280>好`
 *    —— `<绝对毫秒,持续毫秒>`。
 * 3. QRC（QQ 音乐）：`[12340,2800]你(12340,300)好(12640,280)`
 *    —— 行时间戳也是 `[绝对毫秒,持续毫秒]`，逐字用小括号。
 * 4. QRC 变体：`<12340,300,0>` 三段式，第三段忽略。
 *
 * 另外处理：
 * - 一行多个行时间戳（重复副歌）
 * - 元数据 `[ti:]` `[ar:]` `[al:]` `[by:]` `[offset:]`
 * - 同一时间戳出现两次时，第二条视为翻译（网易 / 酷我常见）
 */
class LrcParser : LyricsParser {

    override val format: LyricsFormat = LyricsFormat.Lrc

    private companion object {
        /** `[mm:ss.xx]` / `[mm:ss]` / `[h:mm:ss.xx]` */
        val CLOCK_LINE = Regex("""\[(\d{1,3}):(\d{1,2})(?::(\d{1,2}))?(?:[.:](\d{1,3}))?]""")

        /** `[12340,2800]`：QRC 行时间戳（起点毫秒,时长毫秒） */
        val MILLIS_LINE = Regex("""\[(\d+),(-?\d+)]""")

        /** `<00:12.34>`：标准增强型逐字 */
        val CLOCK_WORD = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")

        /** `<12340,300>` / `<12340,300,0>`：LX / KRC 逐字 */
        val MILLIS_WORD_ANGLE = Regex("""<(-?\d+),(-?\d+)(?:,-?\d+)?>""")

        /** `(12340,300)`：QRC 原始逐字 */
        val MILLIS_WORD_PAREN = Regex("""\((-?\d+),(-?\d+)(?:,-?\d+)?\)""")

        /** `[ti:标题]`：key 必须以字母开头，才不会和时间戳混淆 */
        val META = Regex("""^\[([a-zA-Z#][a-zA-Z0-9_#-]*):(.*)]$""")

        /** 任意逐字标记，用于剥离得到纯文本 */
        val ANY_WORD_TAG = Regex(
            """<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>|<-?\d+,-?\d+(?:,-?\d+)?>|\(-?\d+,-?\d+(?:,-?\d+)?\)"""
        )
    }

    override fun canParse(content: String, hint: String?): Boolean {
        val name = hint?.lowercase()
        if (name != null && (name.endsWith(".lrc") || name.endsWith(".qrc") || name.endsWith(".krc"))) return true
        if (name == LyricsHints.LRC || name == LyricsHints.ENHANCED_LRC) return true
        return CLOCK_LINE.containsMatchIn(content) || MILLIS_LINE.containsMatchIn(content)
    }

    override fun parse(content: String): Lyrics {
        val metadata = mutableMapOf<String, String>()
        var offsetMs = 0L
        // 同一时间戳可能有多行（原文 + 翻译），保持插入顺序
        val buckets = linkedMapOf<Long, MutableList<Raw>>()
        var hasWordTiming = false

        content.lineSequence().forEach { raw ->
            val line = raw.trim().removePrefix("\uFEFF")
            if (line.isEmpty()) return@forEach

            META.matchEntire(line)?.let { m ->
                val key = m.groupValues[1].lowercase()
                val value = m.groupValues[2].trim()
                if (key == "offset") {
                    offsetMs = value.toLongOrNull() ?: 0L
                } else {
                    metadata[key] = value
                }
                return@forEach
            }

            val stamps = parseLineStamps(line)
            if (stamps.isEmpty()) return@forEach

            val text = stripLineStamps(line)
            if (ANY_WORD_TAG.containsMatchIn(text)) hasWordTiming = true
            stamps.forEach { stamp ->
                buckets.getOrPut(stamp.startMs) { mutableListOf() } += Raw(text, stamp.endMs)
            }
        }

        val lines = buckets.entries
            .sortedBy { it.key }
            .mapNotNull { (startMs, raws) ->
                val main = raws.firstOrNull() ?: return@mapNotNull null
                val plain = stripWordTags(main.text)
                val syllables = parseSyllables(main.text, startMs)
                if (plain.isEmpty()) return@mapNotNull null
                LyricLine(
                    startMs = startMs,
                    endMs = main.endMs ?: syllables.lastOrNull()?.endMs,
                    text = plain,
                    translation = raws.getOrNull(1)
                        ?.let { stripWordTags(it.text) }
                        ?.takeIf { it.isNotEmpty() },
                    romanization = raws.getOrNull(2)
                        ?.let { stripWordTags(it.text) }
                        ?.takeIf { it.isNotEmpty() },
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

    private data class Raw(val text: String, val endMs: Long?)

    private data class Stamp(val startMs: Long, val endMs: Long?)

    /** 收集行首的所有时间戳，同时兼容 clock 与 millis 两种写法。 */
    private fun parseLineStamps(line: String): List<Stamp> {
        val result = mutableListOf<Stamp>()
        MILLIS_LINE.findAll(line).forEach { m ->
            val start = m.groupValues[1].toLongOrNull() ?: return@forEach
            val duration = m.groupValues[2].toLongOrNull() ?: 0L
            result += Stamp(start, if (duration > 0) start + duration else null)
        }
        CLOCK_LINE.findAll(line).forEach { m ->
            result += Stamp(m.clockToMillis(), null)
        }
        return result
    }

    private fun stripLineStamps(line: String): String =
        line.replace(MILLIS_LINE, "").replace(CLOCK_LINE, "").trim()

    /**
     * 解析逐字时间戳。
     *
     * 关键点：`<a,b>` 形式的 a 在不同平台含义不同 ——
     * 酷我的 lxlyric 是**绝对毫秒**，咪咕 / 酷狗 KRC 转出来的是**相对行首的偏移**。
     * 因此用 [lineStartMs] 判别：小于行首时间的一律视为相对偏移再加回行首。
     * 单调递增校验能挡住少数把两种混写的脏数据。
     */
    private fun parseSyllables(text: String, lineStartMs: Long): List<LyricSyllable> {
        val matches = ANY_WORD_TAG.findAll(text).toList()
        if (matches.isEmpty()) return emptyList()

        val parsed = mutableListOf<LyricSyllable>()
        matches.forEachIndexed { index, match ->
            val timing = parseWordTag(match.value, lineStartMs) ?: return@forEachIndexed
            val from = match.range.last + 1
            val to = matches.getOrNull(index + 1)?.range?.first ?: text.length
            if (from > to) return@forEachIndexed
            val word = text.substring(from, to)
            if (word.isEmpty()) return@forEachIndexed
            parsed += LyricSyllable(text = word, startMs = timing.first, endMs = timing.second)
        }
        if (parsed.isEmpty()) return emptyList()

        // 标准增强型没有 duration，用下一个音节的起点补齐
        val filled = parsed.mapIndexed { index, syllable ->
            if (syllable.endMs != null) syllable
            else syllable.copy(endMs = parsed.getOrNull(index + 1)?.startMs)
        }.filter { it.startMs >= 0 }

        if (filled.isEmpty()) return emptyList()
        // 时间必须单调不减，否则说明混用了两种坐标系，逐字信息不可信
        val monotonic = filled.zipWithNext().all { (a, b) -> a.startMs <= b.startMs }
        return if (monotonic) filled else emptyList()
    }

    /**
     * 返回 (startMs, endMs?)。
     *
     * [lineStartMs] 用于把相对偏移换算成绝对时间。
     */
    private fun parseWordTag(tag: String, lineStartMs: Long): Pair<Long, Long?>? {
        (MILLIS_WORD_ANGLE.matchEntire(tag) ?: MILLIS_WORD_PAREN.matchEntire(tag))?.let { m ->
            val raw = m.groupValues[1].toLongOrNull() ?: return null
            val duration = m.groupValues[2].toLongOrNull() ?: 0L
            // 小于行首 → 相对偏移；否则已经是绝对时间
            val start = if (raw < lineStartMs) lineStartMs + raw else raw
            return start to (start + duration).takeIf { duration > 0 }
        }
        CLOCK_WORD.matchEntire(tag)?.let { m ->
            val min = m.groupValues[1].toLongOrNull() ?: 0L
            val sec = m.groupValues[2].toLongOrNull() ?: 0L
            val frac = fractionToMillis(m.groupValues.getOrNull(3).orEmpty())
            return (min * 60_000 + sec * 1_000 + frac) to null
        }
        return null
    }

    private fun stripWordTags(text: String) = text.replace(ANY_WORD_TAG, "").trim()

    /** `[h:]mm:ss[.fff]` → 毫秒。 */
    private fun MatchResult.clockToMillis(): Long {
        val g1 = groupValues[1].toLongOrNull() ?: 0L
        val g2 = groupValues[2].toLongOrNull() ?: 0L
        val g3 = groupValues.getOrNull(3).orEmpty()
        val frac = fractionToMillis(groupValues.getOrNull(4).orEmpty())
        return if (g3.isNotEmpty()) {
            // 三段式：时:分:秒
            val sec = g3.toLongOrNull() ?: 0L
            g1 * 3_600_000 + g2 * 60_000 + sec * 1_000 + frac
        } else {
            g1 * 60_000 + g2 * 1_000 + frac
        }
    }

    /** ".5" -> 500ms，".55" -> 550ms，".555" -> 555ms。 */
    private fun fractionToMillis(raw: String): Long = when (raw.length) {
        0 -> 0L
        1 -> (raw.toLongOrNull() ?: 0L) * 100
        2 -> (raw.toLongOrNull() ?: 0L) * 10
        else -> raw.take(3).toLongOrNull() ?: 0L
    }
}