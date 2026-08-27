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
        val CLOCK_LINE = Regex("""\[(\d{1,3}):(\d{1,2})(?::(\d{1,2}))?(?:[.:](\d{1,4}))?]""")
        /** `[12340,2800]`：QRC 行时间戳（起点毫秒,时长毫秒） */
        val MILLIS_LINE = Regex("""\[(\d+),(-?\d+)]""")
        /** `<00:12.34>`：标准增强型逐字 */
        val CLOCK_WORD = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,4}))?>""")
        /** `<12340,300>` / `<12340,300,0>`：LX / KRC 逐字 */
        val MILLIS_WORD_ANGLE = Regex("""<(-?\d+),(-?\d+)(?:,-?\d+)?>""")
        /** `(12340,300)`：QRC 原始逐字 */
        val MILLIS_WORD_PAREN = Regex("""\((-?\d+),(-?\d+)(?:,-?\d+)?\)""")
        /** `[ti:标题]`：key 必须以字母开头，才不会和时间戳混淆 */
        val META = Regex("""^\[([a-zA-Z#][a-zA-Z0-9_#-]*):(.*)]$""")
        /** 任意逐字标记，用于剥离得到纯文本 */
        val ANY_WORD_TAG = Regex(
            """<\d{1,3}:\d{1,2}(?:[.:]\d{1,4})?>|<-?\d+,-?\d+(?:,-?\d+)?>|\(-?\d+,-?\d+(?:,-?\d+)?\)"""
        )
        /**
         * **行尾**的结束时间戳，例如
         * `[02:05.395]我一定是太过招摇 …… 反而给我打了气[02:08.168]`。
         *
         * AMLL / 增强型 LRC 用它标注该行的结束时间。必须与行首时间戳区别对待 ——
         * 旧实现用 `findAll` 扫全行收集起始时间，把这个结束时间也当成了一个
         * 独立的行起点，于是**翻译文本被复制进一个凭空多出的时间桶**，
         * 在界面上表现为"翻译变成了一条独立歌词"。
         */
        val TRAILING_CLOCK = Regex("""\[(\d{1,3}):(\d{1,2})(?::(\d{1,2}))?(?:[.:](\d{1,4}))?]\s*$""")
        /** 行尾 QRC 结束时间戳。 */
        val TRAILING_MILLIS = Regex("""\[(\d+),(-?\d+)]\s*$""")
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

            val parsedLine = parseLine(line) ?: return@forEach
            if (ANY_WORD_TAG.containsMatchIn(parsedLine.text)) hasWordTiming = true
            parsedLine.starts.forEach { start ->
                buckets.getOrPut(start) { mutableListOf() } += Raw(parsedLine.text, parsedLine.endMs)
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
                    // 结束时间来源优先级：本桶任意一条给出的行尾时间戳 → 逐字轴末尾。
                    // 用 firstNotNullOfOrNull 而不是只看 main：AMLL 常把结束时间只写在
                    // 翻译那一行的行尾（原文行以 <mm:ss.fff> 逐字标记收尾）。
                    endMs = raws.firstNotNullOfOrNull { it.endMs } ?: syllables.lastOrNull()?.endMs,
                    text = plain,
                    // 翻译判定只认**完全相同的时间戳**（同一个桶内的第二条文本）。
                    // 不做任何时间容差 —— 见 [Raw] 上方注释。
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

    /**
     * 一行的解析结果。
     *
     * [starts] 是该行的**起始**时间戳（可能多个，重复副歌写法）；
     * [endMs] 是行尾的**结束**时间戳（AMLL 增强型 LRC 写法），没有则为 null。
     */
    private data class ParsedLine(val starts: List<Long>, val endMs: Long?, val text: String)

    /**
     * 拆分一行为「起始时间戳 + 结束时间戳 + 正文」。
     *
     * ## 为什么必须区分行首与行尾时间戳
     *
     * AMLL 风格的增强型 LRC 会把行的结束时间写在**行尾**：
     * ```
     * [02:05.395]我一定是太过招摇 你们不该煽动大家 反而给我打了气[02:08.168]
     * ```
     * 旧实现用 `CLOCK_LINE.findAll(line)` 无差别扫全行收集起始时间，于是
     * `[02:08.168]` 被当成了又一个行起点，**同一份翻译文本被复制进一个凭空多出的
     * 时间桶**（125395 和 128168 两个桶都装了这句中文）。125395 桶里它是正常的
     * 翻译，128168 桶里它却成了该桶唯一的文本 —— 也就是一条独立歌词行。
     * 这才是"翻译又被当成歌词"的真正原因，与时间容差无关。
     */
    private fun parseLine(line: String): ParsedLine? {
        // 1. 先摘掉行尾结束时间戳（若有），避免它被误收为起点
        var body = line
        var endMs: Long? = null
        TRAILING_MILLIS.find(body)?.let { m ->
            val start = m.groupValues[1].toLongOrNull()
            val duration = m.groupValues[2].toLongOrNull()
            if (start != null) endMs = if (duration != null && duration > 0) start + duration else start
            body = body.removeRange(m.range).trimEnd()
        }
        if (endMs == null) {
            TRAILING_CLOCK.find(body)?.let { m ->
                // 只有当行首还存在时间戳时，行尾这个才是"结束时间"；
                // 否则说明整行只有一个时间戳，它就是起点，不能摘走。
                val head = body.take(m.range.first)
                if (CLOCK_LINE.containsMatchIn(head) || MILLIS_LINE.containsMatchIn(head)) {
                    endMs = m.clockToMillis()
                    body = body.removeRange(m.range).trimEnd()
                }
            }
        }

        // 2. 只收集**行首连续**的时间戳作为起点
        val starts = leadingStamps(body)
        if (starts.isEmpty()) return null

        val text = stripLeadingStamps(body)
        return ParsedLine(starts = starts.map { it.startMs }, endMs = endMs ?: starts.firstOrNull()?.endMs, text = text)
    }

    /**
     * 收集行首连续出现的时间戳。
     *
     * 逐个从字符串开头匹配，遇到第一个非时间戳字符就停止 —— 这样正文里出现的
     * 方括号内容（以及行尾结束时间戳）都不会被误认为行起点。
     */
    private fun leadingStamps(line: String): List<Stamp> {
        val result = mutableListOf<Stamp>()
        var index = 0
        while (index < line.length) {
            // 跳过时间戳之间的空白
            while (index < line.length && line[index].isWhitespace()) index++
            if (index >= line.length || line[index] != '[') break
            val rest = line.substring(index)
            val millis = MILLIS_LINE.find(rest)?.takeIf { it.range.first == 0 }
            if (millis != null) {
                val start = millis.groupValues[1].toLongOrNull()
                val duration = millis.groupValues[2].toLongOrNull() ?: 0L
                if (start == null) break
                result += Stamp(start, if (duration > 0) start + duration else null)
                index += millis.range.last + 1
                continue
            }
            val clock = CLOCK_LINE.find(rest)?.takeIf { it.range.first == 0 }
            if (clock != null) {
                result += Stamp(clock.clockToMillis(), null)
                index += clock.range.last + 1
                continue
            }
            break
        }
        return result
    }

    /** 剥掉行首连续时间戳，得到正文。 */
    private fun stripLeadingStamps(line: String): String {
        var index = 0
        while (index < line.length) {
            while (index < line.length && line[index].isWhitespace()) index++
            if (index >= line.length || line[index] != '[') break
            val rest = line.substring(index)
            val m = MILLIS_LINE.find(rest)?.takeIf { it.range.first == 0 }
                ?: CLOCK_LINE.find(rest)?.takeIf { it.range.first == 0 }
                ?: break
            index += m.range.last + 1
        }
        return line.substring(index).trim()
    }

    /**
     * [Raw] 是同一时间桶里的一条文本。
     *
     * 翻译配对策略（v7，用户明确要求）：**只认完全相同的时间戳**。
     * 歌词与其翻译在所有主流格式里都共用同一个行时间戳，因此第一条是原文、
     * 第二条即翻译、第三条为罗马音。不做任何时间容差 —— 任何"就近配对"都会在
     * 中英混写的歌里把相邻的两句独立歌词误合成原文 + 翻译（1500ms 容差在
     * 《苦咖啡·唯一》里实测误吞 7 处）。
     */
    private data class Raw(val text: String, val endMs: Long?)
    private data class Stamp(val startMs: Long, val endMs: Long?)

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

    /**
     * 秒的小数部分 → 毫秒，按**双精度浮点**换算后四舍五入。
     *
     * 各家 LRC 的小数位数完全不统一，必须按"小数"而非"整数"理解：
     * | 写法        | 含义      | 结果   |
     * |-------------|-----------|--------|
     * | `.5`        | 0.5 s     | 500ms  |
     * | `.55`       | 0.55 s    | 550ms  |
     * | `.395`      | 0.395 s   | 395ms  |
     * | `.3958`     | 0.3958 s  | 396ms  |
     *
     * 旧实现按位数写死三个分支（1 位 ×100 / 2 位 ×10 / 其余 take(3)），
     * 四位小数会被**截断**成 395 而非四舍五入到 396，且位数一多就没有定义。
     * 现在统一用 `"0.$raw".toDouble() * 1000` 再 [Math.round]：
     * 位数任意、精度取到毫秒的最近整数，AMLL 那种 `[02:05.395]` 的三位毫秒
     * 也能精确对齐（395ms 而不是 39ms 或 350ms 之类的误读）。
     */
    private fun fractionToMillis(raw: String): Long {
        if (raw.isEmpty()) return 0L
        val seconds = "0.$raw".toDoubleOrNull() ?: return 0L
        return Math.round(seconds * 1000.0)
    }
}