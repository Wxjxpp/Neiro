package com.wxjxpp.neiro.core.lyrics

import com.wxjxpp.neiro.core.model.LyricLine
import com.wxjxpp.neiro.core.model.LyricSyllable
import com.wxjxpp.neiro.core.model.Lyrics
import com.wxjxpp.neiro.core.model.LyricsFormat

/**
 * LRC / 增强型 LRC / **SPL** 解析器。
 *
 * 以 SPL（Salt Player Lyrics）语法标准为准绳实现，见
 * <https://moriafly.com/standards/spl.html>（2025-11-14 修订版）。
 * SPL 本身兼容 LRC 与增强型 LRC，因此这一个解析器覆盖三者。
 *
 * ## SPL 规则与实现位置的对应关系
 *
 * | SPL 章节 | 规则 | 实现 |
 * |---|---|---|
 * | 时间戳 | `[分:秒.毫秒]`，分 1–3 位、秒 1–2 位、毫秒 1–6 位 | [CLOCK] |
 * | 数字规范 | 毫秒按**小数**理解：`.1`=100ms、`.02`=20ms | [fractionToMillis] |
 * | 歌词行 | 时间戳 + 后接文本 | [parseLine] |
 * | 显式行结尾 | 行尾时间戳 = 该行结束时间 | [splitTrailingStamp] |
 * | 显式行结尾 | **换行标记**：下一行只有时间戳、不带文本 | [LineKind.EndMark] |
 * | 隐式行结尾 | 未标结束时间则持续到下一句开始 | UI 侧按行序推进 |
 * | 重复行 | 一行多个行首时间戳 → 展开多行 | [leadingStamps] |
 * | 翻译识别 | **同时间戳**，在前为原文、在后为翻译 | 时间桶内的第 2 条 |
 * | 翻译识别 | 省略时间戳的翻译（紧挨主歌词的裸文本行） | [LineKind.Bare] |
 * | 多行翻译 | 连续多条裸文本行 | [Entry.extras] |
 * | 逐字歌词 | 行内 `[..]` 或 `<..>` 逐字时间戳 | [WORD_TAG] |
 * | 逐字歌词 | 首个逐字标记**之前**的文本从行起点开始唱 | [parseSyllables] 的 head 段 |
 * | 局限性 | 逐字标记必须递增，回退或越过行尾的**单个忽略** | [parseSyllables] |
 * | 局限性 | 重复行 + 逐字 → 仅首个展开保留逐字轴 | [Raw.allowSyllables] |
 * | 兼容性 | 行到达而首字未开始（`[t1]<t2>首字`） | 行起点取 t1，首音节取 t2 |
 *
 * ## 额外兼容的非 SPL 方言
 *
 * 现实中的"增强型 LRC"至少还有三种写法，一并支持：
 * 1. LX / 酷狗 lxlyric：`[00:12.340]<12340,300>你<12640,280>好` —— `<绝对毫秒,持续毫秒>`；
 * 2. QRC（QQ 音乐）：`[12340,2800]你(12340,300)好(12640,280)` —— 行时间戳也是
 *    `[绝对毫秒,持续毫秒]`，逐字用小括号；
 * 3. QRC 变体：`<12340,300,0>` 三段式，第三段忽略。
 *
 * ## 为什么翻译配对坚持零容差
 *
 * SPL 明确规定翻译"依靠**同时间戳**识别"。任何"就近配对"的容差都会在中英混写的
 * 歌里把相邻两句独立歌词误合成原文 + 翻译（1500ms 容差在《苦咖啡·唯一》实测误吞
 * 7 处）。因此这里只认两种翻译写法：完全相同的时间戳，或紧随其后的无时间戳裸行。
 */
class LrcParser : LyricsParser {

    override val format: LyricsFormat = LyricsFormat.Lrc

    private companion object {
        /**
         * SPL 时间戳：`[分:秒.毫秒]`。
         *
         * 分 1–3 位、秒 1–2 位、毫秒 1–6 位（SPL「分、秒和毫秒的数字规范」）。
         * 额外容忍 `[时:分:秒.毫秒]` 三段式（部分工具导出的长音频歌词）。
         */
        val CLOCK = Regex("""\[(\d{1,3}):(\d{1,2})(?::(\d{1,2}))?(?:[.:](\d{1,6}))?]""")

        /** `[12340,2800]`：QRC 行时间戳（起点毫秒,时长毫秒）。 */
        val MILLIS_LINE = Regex("""\[(\d+),(-?\d+)]""")

        /** 整行只有一个时间戳、不带任何文本 —— SPL 的"换行标记结束时间"。 */
        val END_MARK_ONLY = Regex("""^\s*(\[\d{1,3}:\d{1,2}(?:[.:]\d{1,6})?]|\[\d+,-?\d+])\s*$""")

        /** `[ti:标题]`：key 必须以字母开头，才不会和时间戳混淆。 */
        val META = Regex("""^\[([a-zA-Z#][a-zA-Z0-9_#-]*):(.*)]$""")

        /** `<00:12.34>`：增强型 LRC 逐字标记（SPL 的「兼容性与延迟逐字标记」）。 */
        val ANGLE_CLOCK_WORD = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,6}))?>""")

        /** `[00:12.34]`：SPL 正文写法的行内逐字标记。 */
        val BRACKET_CLOCK_WORD = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,6}))?]""")

        /** `<12340,300>` / `<12340,300,0>`：LX / KRC 逐字。 */
        val MILLIS_WORD_ANGLE = Regex("""<(-?\d+),(-?\d+)(?:,-?\d+)?>""")

        /** `(12340,300)`：QRC 原始逐字。 */
        val MILLIS_WORD_PAREN = Regex("""\((-?\d+),(-?\d+)(?:,-?\d+)?\)""")

        /**
         * 正文里的任意逐字标记。
         *
         * **只用于已剥掉行首与行尾时间戳的 body**，所以把 SPL 的方括号写法
         * 一并纳入是安全的 —— 此时残留在正文中间的方括号时间戳按标准就是逐字标记。
         */
        val WORD_TAG = Regex(
            """<\d{1,3}:\d{1,2}(?:[.:]\d{1,6})?>""" +
                """|\[\d{1,3}:\d{1,2}(?:[.:]\d{1,6})?]""" +
                """|<-?\d+,-?\d+(?:,-?\d+)?>""" +
                """|\(-?\d+,-?\d+(?:,-?\d+)?\)""",
        )

        /** 行尾的结束时间戳（SPL「显式行结尾」的同行写法）。 */
        val TRAILING_CLOCK = Regex("""\[(\d{1,3}):(\d{1,2})(?::(\d{1,2}))?(?:[.:](\d{1,6}))?]\s*$""")

        /** 行尾 QRC 结束时间戳。 */
        val TRAILING_MILLIS = Regex("""\[(\d+),(-?\d+)]\s*$""")

        /**
         * 启用主歌词位次判定所需的最少配对行数（见 [resolveMainIndex]）。
         *
         * 单语歌词里偶尔会有两行撞上同一个时间戳（用户那份《苦咖啡·唯一》
         * 95 行里就有 1 处），这种孤例不能用来推断整份文件的排列方式。
         */
        const val MIN_PAIRS_FOR_DETECTION = 4

        /** 同上，配对行占全部行的最低比例：真双语文件几乎每行都配对。 */
        const val MIN_PAIR_RATIO_FOR_DETECTION = 0.5f
    }

    override fun canParse(content: String, hint: String?): Boolean {
        val name = hint?.lowercase()
        if (name != null &&
            (
                name.endsWith(".lrc") || name.endsWith(".spl") ||
                    name.endsWith(".qrc") || name.endsWith(".krc")
                )
        ) {
            return true
        }
        if (name == LyricsHints.LRC || name == LyricsHints.ENHANCED_LRC) return true
        return CLOCK.containsMatchIn(content) || MILLIS_LINE.containsMatchIn(content)
    }

    override fun parse(content: String): Lyrics {
        val metadata = mutableMapOf<String, String>()
        var offsetMs = 0L
        // 按**文档顺序**先建立条目表：裸文本行要挂到它前面那条歌词上，
        // 结束时间标记行要回填给前一条，这些都依赖顺序，不能边读边分桶。
        val entries = mutableListOf<Entry>()
        // 下一条无时间戳裸文本若正好出现在这个行号，才算"紧挨着"上一条歌词。
        // -1 表示当前位置不允许再挂译文。
        var attachableAt = -1

        content.lineSequence().forEachIndexed { lineIndex, rawLine ->
            val line = rawLine.trim().removePrefix("\uFEFF")
            // 空行会打断"紧挨着"的连续性（见下方 attachableAt 的说明），
            // 所以这里只跳过内容、不更新锚点。
            if (line.isEmpty()) return@forEachIndexed

            META.matchEntire(line)?.let { m ->
                val key = m.groupValues[1].lowercase()
                val value = m.groupValues[2].trim()
                if (key == "offset") offsetMs = value.toLongOrNull() ?: 0L else metadata[key] = value
                return@forEachIndexed
            }

            when (val kind = parseLine(line)) {
                is LineKind.Timed -> {
                    entries += Entry(starts = kind.starts, endMs = kind.endMs, body = kind.body)
                    attachableAt = lineIndex + 1
                }
                // SPL「显式行结尾」的换行写法：只有时间戳、没有文本
                is LineKind.EndMark -> {
                    entries.lastOrNull()?.let { if (it.endMs == null) it.endMs = kind.timeMs }
                    attachableAt = -1
                }
                // SPL「翻译识别」省略时间戳的写法 + 「多行翻译」。
                //
                // 标准要求译文**必须紧挨着**主歌词句子，所以这里用文档行号做严格
                // 相邻校验：只有正好是上一条歌词（或上一条译文）的下一行才收下。
                // 否则一份只在开头有时间戳、后面全是纯文本的脏文件，会把整篇正文
                // 都塞成第一句的译文。
                is LineKind.Bare -> {
                    if (lineIndex == attachableAt) {
                        entries.lastOrNull()?.extras?.add(kind.text)
                        attachableAt = lineIndex + 1
                    } else {
                        attachableAt = -1
                    }
                }
                null -> attachableAt = -1
            }
        }

        // 同一时间戳可能有多行（原文 + 翻译 + 罗马音），保持插入顺序
        val buckets = linkedMapOf<Long, MutableList<Raw>>()
        entries.forEach { entry ->
            entry.starts.forEachIndexed { index, stamp ->
                buckets.getOrPut(stamp.startMs) { mutableListOf() } += Raw(
                    text = entry.body,
                    endMs = entry.endMs ?: stamp.endMs,
                    extras = entry.extras.toList(),
                    // SPL「局限性」：重复行的第 2 个及之后的展开，逐字轴必然与
                    // 新起点不匹配（时间戳早于行起点），标准规定忽略。
                    allowSyllables = index == 0,
                )
            }
        }

        var hasWordTiming = false
        // 主歌词位次**按整份文件统一判定一次**，不逐行猜（见 resolveMainIndex）
        val mainIndex = resolveMainIndex(
            buckets = buckets.values,
            titleScript = metadata["ti"]?.let { scriptOf(it) },
        )
        val lines = buckets.entries
            .sortedBy { it.key }
            .mapNotNull { (startMs, bucket) ->
                val raws = bucket.moveToFront(mainIndex)
                val main = raws.firstOrNull() ?: return@mapNotNull null
                val plain = stripWordTags(main.text)
                if (plain.isEmpty()) return@mapNotNull null
                // 行结束时间来源优先级：本桶任意一条给出的显式结束时间 → 逐字轴末尾。
                // 用 firstNotNullOfOrNull 而不是只看 main：AMLL 常把结束时间只写在
                // 翻译那一行的行尾（原文行以 <mm:ss.fff> 逐字标记收尾）。
                val explicitEnd = raws.firstNotNullOfOrNull { it.endMs }
                val syllables = if (main.allowSyllables) {
                    parseSyllables(main.text, startMs, explicitEnd)
                } else {
                    emptyList()
                }
                if (syllables.isNotEmpty()) hasWordTiming = true

                // 翻译候选：① 同时间戳的后续条目（SPL「翻译识别」）
                //           ② 紧随主歌词的裸文本行（SPL 省略时间戳写法 / 多行翻译）
                val sameStamp = raws.drop(1).map { stripWordTags(it.text) }.filter { it.isNotEmpty() }
                val bare = main.extras.map { it.trim() }.filter { it.isNotEmpty() }
                val translation = sameStamp.firstOrNull()
                    ?: bare.takeIf { it.isNotEmpty() }?.joinToString("\n")
                val romanization = when {
                    sameStamp.size >= 2 -> sameStamp[1]
                    // 同时间戳给出了翻译，裸行就顺位成为第三行（罗马音位）
                    sameStamp.size == 1 && bare.isNotEmpty() -> bare.joinToString("\n")
                    else -> null
                }

                LyricLine(
                    startMs = startMs,
                    endMs = explicitEnd ?: syllables.lastOrNull()?.endMs,
                    text = plain,
                    translation = translation,
                    romanization = romanization,
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
     * 判定同时间桶内**哪一个位次是主歌词**（返回 0 表示按 SPL 默认的文档顺序）。
     *
     * ## 为什么必须全局判定一次，而不是逐行判断
     *
     * SPL 规定"在前的一句为主歌词文本，后的一句为翻译歌词文本"，绝大多数文件
     * 都遵守，此时返回 0 即可。但确实存在把译文整体拼在原文**之前**的产物
     * （部分工具把 tlyric 接在 lyric 前面），表现为"翻译显示在歌词之上"。
     *
     * 关键在于：**一份文件里两者的相对顺序是固定的**（同一个拼接流程产出）。
     * 所以这是一个「整份文件二选一」的问题，不是逐行问题。逐行判断反而危险 ——
     * 一旦某几行判反，画面就会中英来回跳。
     *
     * ## 判据（按可靠性排序，命中即返回）
     *
     * 1. **逐字时间轴**：逐字标记只会打在演唱的原文上，译文不可能有。
     *    这是格式层面的硬事实。
     * 2. **孤立行的语种**：原文一定比译文多几行 —— 词曲信息、和声、语气词
     *    通常不翻译，它们在合并后是**只有一条文本的时间桶**。因此"与孤立行
     *    同语种"的那一侧就是原文。这同样是结构事实，不依赖语言学假设。
     * 3. **与曲目元数据 `[ti:]` 的语种一致性**：标题语种即原文语种。
     *
     * 三条都无法判定（例如同语种的原文 + 注释）时返回 0，严格退回 SPL 的
     * 文档顺序 —— 不做无根据的猜测，宁可保持标准行为也不引入随机翻转。
     */
    private fun resolveMainIndex(buckets: Collection<List<Raw>>, titleScript: Script?): Int {
        val pairs = buckets.filter { it.size >= 2 }
        if (pairs.isEmpty()) return 0

        // 判据 1：逐字时间轴。这是格式事实而非统计推断，样本再少也成立，
        // 因此不受下面的样本量门槛限制。
        val timedAt0 = pairs.count { WORD_TAG.containsMatchIn(it[0].text) }
        val timedAt1 = pairs.count { WORD_TAG.containsMatchIn(it[1].text) }
        if (timedAt0 != timedAt1) return if (timedAt0 > timedAt1) 0 else 1

        // 以下判据基于语种统计，需要足够的样本才有意义。
        //
        // 真正的双语文件里**几乎每行都有译文**。若只有零星几个桶配对，那本质是
        // 单语歌词里两行偶然撞上同一时间戳（用户那份 95 行的《苦咖啡·唯一》
        // 就只有 1 处），拿这种孤例做统计只会得到噪声 —— 直接退回文档顺序。
        if (pairs.size < MIN_PAIRS_FOR_DETECTION) return 0
        if (pairs.size.toFloat() / buckets.size < MIN_PAIR_RATIO_FOR_DETECTION) return 0

        // 判据 2：孤立行（未被翻译的行）的主导语种
        val soloScript = buckets.filter { it.size == 1 }
            .mapNotNull { scriptOf(it[0].text) }
            .dominantOrNull()
        soloScript?.let { solo ->
            val matchAt0 = pairs.count { scriptOf(it[0].text) == solo }
            val matchAt1 = pairs.count { scriptOf(it[1].text) == solo }
            if (matchAt0 != matchAt1) return if (matchAt0 > matchAt1) 0 else 1
        }

        // 判据 3：与 [ti:] 标题语种一致的一侧是原文
        titleScript?.let { title ->
            val sameAt0 = pairs.count { scriptOf(it[0].text) == title }
            val sameAt1 = pairs.count { scriptOf(it[1].text) == title }
            if (sameAt0 != sameAt1) return if (sameAt0 > sameAt1) 0 else 1
        }

        return 0
    }

    /** 出现次数最多的元素；并列或空列表返回 null（并列说明这个信号不可用）。 */
    private fun <T> List<T>.dominantOrNull(): T? {
        if (isEmpty()) return null
        val counts = groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
        if (counts.size >= 2 && counts[0].value == counts[1].value) return null
        return counts.first().key
    }

    /**
     * 粗粒度语种判定：只区分「以 CJK 为主」「以拉丁字母为主」「假名」。
     *
     * 不追求准确的语言识别 —— 这里只需要一个**稳定的分类**用于两侧对比统计，
     * 逐行准确率并不重要，统计量级上的差异才是判据。
     * 无法归类（纯符号、数字）返回 null，不参与统计。
     */
    private fun scriptOf(text: String): Script? {
        var cjk = 0
        var latin = 0
        var kana = 0
        text.forEach { ch ->
            when {
                ch in '\u4E00'..'\u9FFF' -> cjk++
                ch in '\u3040'..'\u30FF' -> kana++
                ch in 'A'..'Z' || ch in 'a'..'z' -> latin++
            }
        }
        // 假名优先：日文里汉字与假名混排，出现假名即判为日文
        if (kana > 0) return Script.Kana
        val total = cjk + latin
        if (total == 0) return null
        return if (cjk >= latin) Script.Cjk else Script.Latin
    }

    private enum class Script { Cjk, Latin, Kana }

    /** 把第 [index] 项移到首位，其余保持相对顺序。越界或 0 时原样返回。 */
    private fun List<Raw>.moveToFront(index: Int): List<Raw> {
        if (index <= 0 || index >= size) return this
        return buildList {
            // 逐字轴只在原始的首个展开上有效（见 Raw.allowSyllables），换位时一并带走
            add(this@moveToFront[index].copyWith(allowSyllables = this@moveToFront[0].allowSyllables))
            this@moveToFront.forEachIndexed { i, raw -> if (i != index) add(raw) }
        }
    }

    /** 文档顺序上的一条歌词，[extras] 是挂在它下面的无时间戳翻译行。 */
    private class Entry(
        val starts: List<Stamp>,
        var endMs: Long?,
        val body: String,
        val extras: MutableList<String> = mutableListOf(),
    )

    /**
     * 时间桶里的一条文本。
     *
     * [allowSyllables] = false 表示这是重复行的非首个展开，按 SPL「局限性」
     * 丢弃逐字轴（否则逐字时间戳会早于行起点，进度条直接跑满）。
     */
    private class Raw(
        val text: String,
        val endMs: Long?,
        val extras: List<String>,
        val allowSyllables: Boolean,
    ) {
        fun copyWith(allowSyllables: Boolean) =
            Raw(text = text, endMs = endMs, extras = extras, allowSyllables = allowSyllables)
    }

    private data class Stamp(val startMs: Long, val endMs: Long?)

    /** 一行的三种可能形态。 */
    private sealed interface LineKind {
        /** 正常歌词行：行首时间戳（可多个）+ 正文（可含逐字标记）+ 可选行尾结束时间。 */
        class Timed(val starts: List<Stamp>, val endMs: Long?, val body: String) : LineKind
        /** 只有时间戳、没有文本 —— 上一行的结束时间。 */
        class EndMark(val timeMs: Long) : LineKind
        /** 完全没有时间戳的裸文本 —— 上一行的翻译。 */
        class Bare(val text: String) : LineKind
    }

    /**
     * 拆分一行为「行首时间戳 + 正文 + 行尾结束时间戳」。
     *
     * ## 为什么必须区分行首、行内与行尾时间戳
     *
     * 同一个 `[mm:ss.fff]` 在三个位置有三种完全不同的含义（SPL 分别定义在
     * 「歌词行」「逐字标记时间戳」「显式行结尾」三节）：
     * ```
     * [02:05.395]你好[02:06.100]椒盐音乐[02:08.168]
     *  ↑行起点      ↑逐字分隔        ↑行结束
     * ```
     * 早期实现用 `CLOCK.findAll(line)` 无差别扫全行收集起点，于是行尾与行内的
     * 时间戳都被当成了新的行起点 —— 翻译文本被复制进凭空多出的时间桶，界面上
     * 就是"翻译变成一条独立歌词"。处理顺序因此必须是：**先摘行尾 → 再摘行首 →
     * 剩下的方括号时间戳才是逐字标记**。
     */
    private fun parseLine(line: String): LineKind? {
        // 0. 整行只有时间戳：SPL 的换行式结束标记
        END_MARK_ONLY.matchEntire(line)?.let {
            val stamp = leadingStamps(line).firstOrNull() ?: return null
            return LineKind.EndMark(stamp.startMs)
        }

        // 1. 先摘掉行尾结束时间戳（若有），避免它被误收为起点
        val (bodyAfterTrailing, endMs) = splitTrailingStamp(line)

        // 2. 只收集**行首连续**的时间戳作为起点
        val starts = leadingStamps(bodyAfterTrailing)
        if (starts.isEmpty()) {
            // 无时间戳的裸文本：SPL 里它是上一句的翻译
            val bare = bodyAfterTrailing.trim()
            return if (bare.isEmpty()) null else LineKind.Bare(bare)
        }

        val body = stripLeadingStamps(bodyAfterTrailing)
        return LineKind.Timed(
            starts = starts,
            endMs = endMs ?: starts.firstOrNull()?.endMs,
            body = body,
        )
    }

    /**
     * 摘除行尾结束时间戳，返回（剩余正文, 结束时间）。
     *
     * 只有当行首仍存在时间戳时，行尾那个才是"结束时间"；否则说明整行只有一个
     * 时间戳，它就是起点，不能摘走。
     */
    private fun splitTrailingStamp(line: String): Pair<String, Long?> {
        TRAILING_MILLIS.find(line)?.let { m ->
            val head = line.take(m.range.first)
            if (CLOCK.containsMatchIn(head) || MILLIS_LINE.containsMatchIn(head)) {
                val start = m.groupValues[1].toLongOrNull()
                val duration = m.groupValues[2].toLongOrNull()
                val end = start?.let { if (duration != null && duration > 0) it + duration else it }
                return line.removeRange(m.range).trimEnd() to end
            }
        }
        TRAILING_CLOCK.find(line)?.let { m ->
            val head = line.take(m.range.first)
            if (CLOCK.containsMatchIn(head) || MILLIS_LINE.containsMatchIn(head)) {
                return line.removeRange(m.range).trimEnd() to m.clockToMillis()
            }
        }
        return line to null
    }

    /**
     * 收集行首连续出现的时间戳。
     *
     * 逐个从字符串开头匹配，遇到第一个非时间戳字符就停止 —— 这样正文里的
     * 逐字标记与行尾结束时间戳都不会被误认为行起点。
     */
    private fun leadingStamps(line: String): List<Stamp> {
        val result = mutableListOf<Stamp>()
        var index = 0
        while (index < line.length) {
            while (index < line.length && line[index].isWhitespace()) index++
            if (index >= line.length || line[index] != '[') break
            val rest = line.substring(index)
            val millis = MILLIS_LINE.find(rest)?.takeIf { it.range.first == 0 }
            if (millis != null) {
                val start = millis.groupValues[1].toLongOrNull() ?: break
                val duration = millis.groupValues[2].toLongOrNull() ?: 0L
                result += Stamp(start, if (duration > 0) start + duration else null)
                index += millis.range.last + 1
                continue
            }
            val clock = CLOCK.find(rest)?.takeIf { it.range.first == 0 }
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
                ?: CLOCK.find(rest)?.takeIf { it.range.first == 0 }
                ?: break
            index += m.range.last + 1
        }
        return line.substring(index).trim()
    }

    /**
     * 解析逐字时间轴。
     *
     * [text] 必须是**已剥掉行首与行尾时间戳**的正文，因此其中剩余的
     * `[..]` / `<..>` / `(..)` 时间戳按 SPL 都是逐字标记。
     *
     * 三条 SPL 规则在这里落地：
     * 1. **首个标记之前的文本**从行起点唱到首个标记（`[t]你好[t2]椒盐音乐`
     *    里的"你好"）。早期实现只取标记**之后**的文本，这段直接丢了。
     * 2. **逐字标记需递增**：回退的、或超过行结束时间的标记**单个忽略**，
     *    其后文本并入前一个音节（早期实现是整行逐字信息全丢，粒度太粗）。
     * 3. `<a,b>` 形式的 a 在不同平台含义不同 —— 酷我 lxlyric 是绝对毫秒，
     *    咪咕 / KRC 是相对行首的偏移。用 [lineStartMs] 判别。
     */
    private fun parseSyllables(text: String, lineStartMs: Long, lineEndMs: Long?): List<LyricSyllable> {
        val matches = WORD_TAG.findAll(text).toList()
        if (matches.isEmpty()) return emptyList()

        // 逐个校验单调性，只保留合法标记
        class Tag(val startMs: Long, val endMs: Long?, val range: IntRange)
        val kept = mutableListOf<Tag>()
        var lastMs = lineStartMs
        matches.forEach { m ->
            val timing = parseWordTag(m.value, lineStartMs) ?: return@forEach
            if (timing.first < lastMs) return@forEach
            if (lineEndMs != null && timing.first > lineEndMs) return@forEach
            kept += Tag(timing.first, timing.second, m.range)
            lastMs = timing.first
        }
        if (kept.isEmpty()) return emptyList()

        val result = mutableListOf<LyricSyllable>()
        // 规则 1：首个标记之前的文本
        val head = removeWordTags(text.substring(0, kept.first().range.first))
        if (head.isNotBlank()) {
            result += LyricSyllable(text = head, startMs = lineStartMs, endMs = kept.first().startMs)
        }
        kept.forEachIndexed { index, tag ->
            val from = tag.range.last + 1
            val to = kept.getOrNull(index + 1)?.range?.first ?: text.length
            if (from > to) return@forEachIndexed
            // 只删标记、**不 trim**：音节文本里的空格是词与词的分隔符。
            // KaraokeText 把每个音节渲染成独立的 Text 再用 FlowRow 排列，
            // trim 掉尾随空格会让整句变成 "Imustbegettin'tooflashy"。
            // 被忽略的非法标记也在这里一并清掉，其后文本自然并入当前音节（规则 2）。
            val word = removeWordTags(text.substring(from, to))
            if (word.isEmpty()) return@forEachIndexed
            result += LyricSyllable(
                text = word,
                startMs = tag.startMs,
                endMs = tag.endMs ?: kept.getOrNull(index + 1)?.startMs ?: lineEndMs,
            )
        }
        return result.filter { it.startMs >= 0 }
    }

    /**
     * 单个逐字标记 → (startMs, endMs?)。
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
        (ANGLE_CLOCK_WORD.matchEntire(tag) ?: BRACKET_CLOCK_WORD.matchEntire(tag))?.let { m ->
            val min = m.groupValues[1].toLongOrNull() ?: 0L
            val sec = m.groupValues[2].toLongOrNull() ?: 0L
            val frac = fractionToMillis(m.groupValues.getOrNull(3).orEmpty())
            return (min * 60_000 + sec * 1_000 + frac) to null
        }
        return null
    }

    /**
     * 删除逐字标记但**保留所有空白**，用于切分音节文本。
     *
     * 与 [stripWordTags] 的区别只在 trim：音节是拿去逐个渲染再横向拼接的，
     * 词间空格必须原样保留，否则整句会粘成一串。
     */
    private fun removeWordTags(text: String) = text.replace(WORD_TAG, "")

    /**
     * 删除逐字标记并去掉首尾空白，用于整行的可读文本。
     *
     * 行级文本前后的空白没有语义（很多文件在时间戳后带一个空格），
     * 而且行内空格本来就完整保留，所以这里 trim 是安全的。
     */
    private fun stripWordTags(text: String) = removeWordTags(text).trim()

    /** `[时:]分:秒[.毫秒]` → 毫秒。 */
    private fun MatchResult.clockToMillis(): Long {
        val g1 = groupValues[1].toLongOrNull() ?: 0L
        val g2 = groupValues[2].toLongOrNull() ?: 0L
        val g3 = groupValues.getOrNull(3).orEmpty()
        val frac = fractionToMillis(groupValues.getOrNull(4).orEmpty())
        return if (g3.isNotEmpty()) {
            val sec = g3.toLongOrNull() ?: 0L
            g1 * 3_600_000 + g2 * 60_000 + sec * 1_000 + frac
        } else {
            g1 * 60_000 + g2 * 1_000 + frac
        }
    }

    /**
     * 秒的小数部分 → 毫秒，按**双精度浮点**换算后四舍五入。
     *
     * SPL「分、秒和毫秒的数字规范」明确：毫秒位不足 3 位视为**后位省略 0**，
     * 即按小数而非整数理解。
     *
     * | 写法    | SPL 含义 | 结果   |
     * |---------|----------|--------|
     * | `.1`    | 0.1 s    | 100ms  |
     * | `.02`   | 0.02 s   | 20ms   |
     * | `.395`  | 0.395 s  | 395ms  |
     * | `.3958` | 0.3958 s | 396ms  |
     *
     * 统一用 `"0.$raw".toDouble() * 1000` 再 [Math.round]：位数任意（SPL 允许 1–6 位），
     * 精度取到毫秒的最近整数。早期按位数写死分支的实现会把四位小数**截断**成 395。
     */
    private fun fractionToMillis(raw: String): Long {
        if (raw.isEmpty()) return 0L
        val seconds = "0.$raw".toDoubleOrNull() ?: return 0L
        return Math.round(seconds * 1000.0)
    }
}