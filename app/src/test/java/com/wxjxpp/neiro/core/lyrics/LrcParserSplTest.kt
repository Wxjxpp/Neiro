package com.wxjxpp.neiro.core.lyrics

import com.wxjxpp.neiro.core.model.LyricsFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LrcParser] 对 SPL 标准的一致性测试。
 *
 * 每个用例都直接取自 SPL 语法标准（<https://moriafly.com/standards/spl.html>）
 * 的示例，测试名标注对应章节，便于日后标准修订时逐条复核。
 * 末尾另有两个真实数据回归用例（用户实测反例）。
 */
class LrcParserSplTest {

    private val parser = LrcParser()

    // ---------------- 时间戳 ----------------

    /** SPL「分、秒和毫秒的数字规范」：分 1–3 位、秒 1–2 位。 */
    @Test
    fun `时间戳 分秒位数任意`() {
        val lyrics = parser.parse("[103:3.405]a\n[3:12.5]b")

        assertEquals(
            listOf(3 * 60_000 + 12_500L, 103 * 60_000 + 3_405L),
            lyrics.lines.map { it.startMs },
        )
    }

    /** SPL：毫秒不足 3 位视为**后位省略 0**，即按小数理解。 */
    @Test
    fun `毫秒按小数换算`() {
        val lyrics = parser.parse(
            """
            [00:01.1]一位
            [00:02.02]两位
            [00:03.130]三位
            [00:04.450000]六位
            """.trimIndent(),
        )

        assertEquals(listOf(1_100L, 2_020L, 3_130L, 4_450L), lyrics.lines.map { it.startMs })
    }

    // ---------------- 歌词行 / 行结尾 ----------------

    /** SPL「显式行结尾」：`[05:20.22]你好椒盐音乐[05:21.22]`。 */
    @Test
    fun `显式行结尾 同行写法`() {
        val lyrics = parser.parse("[05:20.22]你好椒盐音乐[05:21.22]")

        assertEquals(1, lyrics.lines.size)
        assertEquals("你好椒盐音乐", lyrics.lines[0].text)
        assertEquals(321_220L, lyrics.lines[0].endMs)
    }

    /** SPL「显式行结尾」：结束时间也可以换行单独标记（该行不接任何文本）。 */
    @Test
    fun `显式行结尾 换行标记写法`() {
        val lyrics = parser.parse("[05:20.22]你好椒盐音乐\n[05:21.22]")

        assertEquals("换行结束标记不得产生额外歌词行", 1, lyrics.lines.size)
        assertEquals(321_220L, lyrics.lines[0].endMs)
    }

    /** SPL「隐式行结尾」：未标结束时间的行持续到下一句开始。 */
    @Test
    fun `隐式行结尾保留两行`() {
        val lyrics = parser.parse("[05:20.22]你好椒盐音乐\n[05:22.22]天天开心")

        assertEquals(2, lyrics.lines.size)
        assertNull(lyrics.lines[0].endMs)
    }

    /** SPL「重复行」：`[05:20.22][05:30.22]你好椒盐音乐`。 */
    @Test
    fun `重复行展开为多行`() {
        val lyrics = parser.parse("[05:20.22][05:30.22]你好椒盐音乐")

        assertEquals(2, lyrics.lines.size)
        assertEquals(listOf(320_220L, 330_220L), lyrics.lines.map { it.startMs })
        assertTrue(lyrics.lines.all { it.text == "你好椒盐音乐" })
    }

    // ---------------- 歌词翻译 ----------------

    /** SPL「翻译识别」：同时间戳，在前为原文、在后为翻译。 */
    @Test
    fun `同时间戳配对为翻译`() {
        val lyrics = parser.parse("[05:20.22]你好椒盐音乐\n[05:20.22]Hello Salt Player")

        assertEquals(1, lyrics.lines.size)
        assertEquals("Hello Salt Player", lyrics.lines[0].translation)
    }

    /** SPL「翻译识别」：译文可省略时间戳，但必须紧挨主歌词。 */
    @Test
    fun `省略时间戳的翻译`() {
        val lyrics = parser.parse("[05:20.22]你好椒盐音乐\nHello Salt Player")

        assertEquals(1, lyrics.lines.size)
        assertEquals("Hello Salt Player", lyrics.lines[0].translation)
    }

    /**
     * SPL「翻译识别」的原文示例：裸文本归属于**紧挨着**它的那一句，
     * 不是文档里的第一句。
     */
    @Test
    fun `裸文本归属紧挨着的上一句`() {
        val lyrics = parser.parse(
            """
            [05:20.22]你好椒盐音乐
            [05:21.22]不要糖醋放椒盐
            Hello Salt Player
            """.trimIndent(),
        )

        assertEquals(2, lyrics.lines.size)
        assertNull("第一句没有译文", lyrics.lines[0].translation)
        assertEquals("Hello Salt Player", lyrics.lines[1].translation)
    }

    /** SPL「多行翻译」：连续多条裸文本都属于同一句。 */
    @Test
    fun `多行翻译`() {
        val lyrics = parser.parse(
            "[05:20.22]你好椒盐音乐\nHello Salt Player\nこんにちは Salt Player",
        )

        assertEquals(1, lyrics.lines.size)
        assertEquals(
            "Hello Salt Player\nこんにちは Salt Player",
            lyrics.lines[0].translation,
        )
    }

    // ---------------- 逐字歌词 ----------------

    /**
     * SPL「逐字标记时间戳」：`[05:20.22]你好[05:23.22]椒盐音乐[05:24.22]`
     *
     * 三个时间戳分别是行起点、逐字分隔、行结束 —— 三种含义，位置决定。
     */
    @Test
    fun `逐字方括号写法`() {
        val lyrics = parser.parse("[05:20.22]你好[05:23.22]椒盐音乐[05:24.22]")

        assertEquals(1, lyrics.lines.size)
        val line = lyrics.lines[0]
        assertEquals("你好椒盐音乐", line.text)
        assertEquals(320_220L, line.startMs)
        assertEquals(324_220L, line.endMs)
        assertEquals(listOf("你好", "椒盐音乐"), line.syllables.map { it.text })
        assertEquals(listOf(320_220L, 323_220L), line.syllables.map { it.startMs })
        assertEquals(LyricsFormat.EnhancedLrc, lyrics.format)
    }

    /** SPL「兼容性与延迟逐字标记」：中间的逐字标记可用 `<>` 包裹，语义等价。 */
    @Test
    fun `逐字尖括号写法等价`() {
        val bracket = parser.parse("[05:20.22]你好[05:23.22]椒盐音乐[05:24.22]")
        val angle = parser.parse("[05:20.22]你好<05:23.22>椒盐音乐[05:24.22]")

        assertEquals(
            bracket.lines[0].syllables.map { it.text to it.startMs },
            angle.lines[0].syllables.map { it.text to it.startMs },
        )
    }

    /** SPL「兼容性与延迟逐字标记」：行到达而首字未开始。 */
    @Test
    fun `延迟逐字标记`() {
        val lyrics = parser.parse("[05:20.22]<05:21.22>你好<05:23.22>椒盐音乐[05:24.22]")

        val line = lyrics.lines[0]
        assertEquals("行起点是 t1", 320_220L, line.startMs)
        assertEquals("首音节从 t2 才开始", 321_220L, line.syllables.first().startMs)
        assertEquals("你好", line.syllables.first().text)
    }

    /** SPL「局限性」：重复行 + 逐字 → 只有首个展开的逐字轴有效。 */
    @Test
    fun `重复行与逐字并用时仅首行保留逐字轴`() {
        val lyrics = parser.parse("[05:20.22][05:30.22]你好[05:23.22]椒盐音乐[05:24.22]")

        assertEquals(2, lyrics.lines.size)
        assertTrue("首行逐字有效", lyrics.lines[0].syllables.isNotEmpty())
        assertTrue("第二行逐字被忽略", lyrics.lines[1].syllables.isEmpty())
        assertEquals("两行文本相同", "你好椒盐音乐", lyrics.lines[1].text)
    }

    // ---------------- 元数据 ----------------

    @Test
    fun `元数据与offset`() {
        val lyrics = parser.parse(
            """
            [ti:测试标题]
            [ar:测试歌手]
            [offset:-300]
            [00:01.00]第一句
            [00:05.00]第二句
            """.trimIndent(),
        )

        assertEquals(LyricsFormat.Lrc, lyrics.format)
        assertEquals("测试标题", lyrics.metadata["ti"])
        assertEquals(-300L, lyrics.offsetMs)
        assertEquals(2, lyrics.lines.size)
    }

    // ---------------- 真实数据回归 ----------------

    /**
     * 用户实测反例（AMLL 逐字 + 行尾结束时间戳）。
     *
     * 早期实现把行尾 `[02:08.168]` 当成新的行起点，导致中文译文被复制成一条
     * 独立歌词行 —— 也就是用户看到的"翻译又被当成歌词"。
     */
    @Test
    fun `回归 行尾结束时间戳不产生额外歌词行`() {
        val lrc = "[02:05.395] <02:05.395>I <02:05.617>must <02:05.816>be " +
            "<02:05.992>gettin' <02:06.192>too <02:06.378>flashy <02:06.601>y'all " +
            "<02:06.784>shouldn't <02:06.977>have <02:07.161>let <02:07.368>the " +
            "<02:07.568>world <02:07.792>gas <02:07.960>me <02:08.168>\n" +
            "[02:05.395]我一定是太过招摇 你们不该煽动大家 反而给我打了气[02:08.168]"

        val lyrics = parser.parse(lrc)

        assertEquals("只应有一行歌词", 1, lyrics.lines.size)
        val line = lyrics.lines[0]
        assertEquals(125_395L, line.startMs)
        assertEquals(128_168L, line.endMs)
        assertEquals(
            "我一定是太过招摇 你们不该煽动大家 反而给我打了气",
            line.translation,
        )
        assertTrue(line.text.startsWith("I must be gettin'"))
        assertTrue(line.syllables.isNotEmpty())
    }

    /**
     * 用户实测反例（《苦咖啡·唯一》）：中英混写的单语歌词。
     *
     * 相邻两句语言不同、间隔一两秒，任何时间容差都会把它们误合成原文 + 译文。
     * SPL 规定翻译只依靠**同时间戳**识别，所以这里必须零配对。
     */
    @Test
    fun `回归 中英混写单语歌词零配对`() {
        val lrc = """
            [00:56.21]you are my only，girl
            [00:58.50]漂亮姑娘名叫卓玛
            [00:59.69]一直在我梦里，girl
            [01:01.48]我不断寻找那个唯一
            [01:03.43]不见踪影，girl
            [01:05.05]you are my only one
            [01:06.39]我不能忘记她
            [01:08.26]you are my only
            [01:11.32]one love in my heart bae
            [01:12.86]想要保护她的纯粹
        """.trimIndent()

        val lyrics = parser.parse(lrc)

        assertEquals("十行全部保留", 10, lyrics.lines.size)
        assertFalse("不应识别出任何翻译", lyrics.hasTranslation)
        assertTrue(lyrics.lines.any { it.text == "我不能忘记她" && it.translation == null })
    }

    /**
     * 用户实测反例：**译文写在原文之前**，导致"翻译显示在歌词之上"。
     *
     * 部分转换工具会把 tlyric 拼在 lyric 前面。此时纯按文档顺序取主歌词就会
     * 把译文当原文。判据取"谁带逐字时间轴" —— 逐字标记只会打在演唱的原文上。
     */
    @Test
    fun `回归 译文在前时仍以逐字行为主歌词`() {
        val lrc = "[02:05.395]我一定是太过招摇\n" +
            "[02:05.395]<02:05.395>I <02:05.617>must <02:05.816>be[02:08.168]"

        val lyrics = parser.parse(lrc)

        assertEquals(1, lyrics.lines.size)
        assertEquals("I must be", lyrics.lines[0].text)
        assertEquals("我一定是太过招摇", lyrics.lines[0].translation)
    }

    /** 没有逐字标记时不做任何猜测，严格按 SPL 的文档顺序定主次。 */
    @Test
    fun `无逐字标记时保持文档顺序`() {
        val lyrics = parser.parse("[00:10.00]Hello\n[00:10.00]你好")

        assertEquals("Hello", lyrics.lines[0].text)
        assertEquals("你好", lyrics.lines[0].translation)
    }

    /**
     * 用户实测 bug：**逐字歌词的词间空格被吃掉**。
     *
     * `KaraokeText` 把每个音节渲染成独立的 `Text` 再用 `FlowRow` 横向拼接，
     * 所以音节自带的尾随空格就是词与词的分隔。之前切分音节时统一做了 trim，
     * 拼出来变成 "Imustbegettin'"。
     */
    @Test
    fun `逐字音节保留词间空格`() {
        val lyrics = parser.parse(
            "[02:05.395]<02:05.395>I <02:05.617>must <02:05.816>be <02:05.992>gettin'[02:08.168]",
        )

        val line = lyrics.lines[0]
        assertEquals(
            "音节拼接后必须与原句一致",
            "I must be gettin'",
            line.syllables.joinToString("") { it.text },
        )
        assertEquals("整行文本首尾不留空白", "I must be gettin'", line.text)
    }

    // ---------------- 主歌词位次判定（译文写在原文之前）----------------

    /**
     * **逐句**双语且译文在前：靠 `[ti:]` 标题的语种纠正主次。
     *
     * 逐句歌词没有逐字标记，所以不能靠"谁带逐字轴"判断 —— 这是上一版的漏洞。
     */
    @Test
    fun `逐句双语 译文在前 靠标题语种纠正`() {
        val lrc = """
            [ti:The Sound of Silence]
            [00:10.00]你好 黑暗 我的老朋友
            [00:10.00]Hello darkness my old friend
            [00:14.00]我又来和你交谈
            [00:14.00]I've come to talk with you again
            [00:18.00]因为有个幻象轻柔潜入
            [00:18.00]Because a vision softly creeping
            [00:22.00]在我入睡时留下种子
            [00:22.00]Left its seeds while I was sleeping
            [00:26.00]那幻象植入我的脑海
            [00:26.00]And the vision that was planted in my brain
            [00:30.00]仍然留存
            [00:30.00]Still remains
        """.trimIndent()

        val lyrics = parser.parse(lrc)

        assertEquals(6, lyrics.lines.size)
        assertEquals("Hello darkness my old friend", lyrics.lines[0].text)
        assertEquals("你好 黑暗 我的老朋友", lyrics.lines[0].translation)
        assertEquals("Still remains", lyrics.lines.last().text)
    }

    /**
     * **逐句**双语、译文在前、且没有 `[ti:]`：靠**孤立行**的语种纠正。
     *
     * 和声与语气词通常不翻译，它们在合并后是只有一条文本的时间桶，
     * 语种必然与原文一致 —— 这是结构事实，不依赖语言学假设。
     */
    @Test
    fun `逐句双语 译文在前 无标题时靠孤立行语种纠正`() {
        val lrc = """
            [00:10.00]你好 黑暗 我的老朋友
            [00:10.00]Hello darkness my old friend
            [00:14.00]我又来和你交谈
            [00:14.00]I've come to talk with you again
            [00:18.00]因为有个幻象轻柔潜入
            [00:18.00]Because a vision softly creeping
            [00:22.00]在我入睡时留下种子
            [00:22.00]Left its seeds while I was sleeping
            [00:26.00]那幻象植入我的脑海
            [00:26.00]And the vision that was planted in my brain
            [00:30.00]仍然留存
            [00:30.00]Still remains
            [00:34.00]Oh oh oh
            [00:36.00]Yeah yeah
            [00:38.00]La la la la
        """.trimIndent()

        val lyrics = parser.parse(lrc)

        assertEquals("Hello darkness my old friend", lyrics.lines[0].text)
        assertEquals("你好 黑暗 我的老朋友", lyrics.lines[0].translation)
    }

    /** 正常顺序的逐句双语绝不能被"纠正"反了。 */
    @Test
    fun `逐句双语 正常顺序不被误改`() {
        val lrc = """
            [ti:The Sound of Silence]
            [00:10.00]Hello darkness my old friend
            [00:10.00]你好 黑暗 我的老朋友
            [00:14.00]I've come to talk with you again
            [00:14.00]我又来和你交谈
            [00:18.00]Because a vision softly creeping
            [00:18.00]因为有个幻象轻柔潜入
            [00:22.00]Left its seeds while I was sleeping
            [00:22.00]在我入睡时留下种子
            [00:26.00]Still remains
            [00:26.00]仍然留存
        """.trimIndent()

        val lyrics = parser.parse(lrc)

        assertEquals("Hello darkness my old friend", lyrics.lines[0].text)
        assertEquals("你好 黑暗 我的老朋友", lyrics.lines[0].translation)
    }

    /**
     * 原文与"译文"同语种（中文歌 + 中文注释）：所有判据都失效，
     * 必须退回 SPL 的文档顺序，不能随机翻转。
     */
    @Test
    fun `同语种双语退回文档顺序`() {
        val lrc = """
            [00:10.00]第一句原文
            [00:10.00]第一句注释
            [00:14.00]第二句原文
            [00:14.00]第二句注释
            [00:18.00]第三句原文
            [00:18.00]第三句注释
            [00:22.00]第四句原文
            [00:22.00]第四句注释
            [00:26.00]第五句原文
            [00:26.00]第五句注释
        """.trimIndent()

        val lyrics = parser.parse(lrc)

        assertEquals("第一句原文", lyrics.lines[0].text)
        assertEquals("第一句注释", lyrics.lines[0].translation)
    }

    /**
     * 单语歌词里两行**偶然**撞上同一时间戳时，不足以触发主次判定。
     *
     * 用户那份《苦咖啡·唯一》95 行里只有 1 处这样的碰撞。若拿这种孤例做语种
     * 统计，就会把整份文件的主次判反。因此语种类判据设了样本量门槛。
     */
    @Test
    fun `零星时间戳碰撞不触发主次判定`() {
        val lrc = """
            [00:14.77]your lov's like 苦咖啡
            [00:17.14]让我成谜彻夜难眠bae
            [00:18.77]我一个人煮咖啡
            [00:20.04]像落队的大雁自己往南边飞
            [00:22.31]im on my own
            [00:23.29]frozen heart不会再为谁跳动
            [00:14.77]（确定 你就是我的唯一)
        """.trimIndent()

        val lyrics = parser.parse(lrc)

        val collided = lyrics.lines.first { it.startMs == 14_770L }
        assertEquals("先出现的仍是主歌词", "your lov's like 苦咖啡", collided.text)
        assertEquals("（确定 你就是我的唯一)", collided.translation)
    }
}