package com.wxjxpp.neiro.core.lyrics

import com.wxjxpp.neiro.core.model.LyricsFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LrcParser] 的翻译配对与时间精度回归测试。
 *
 * 三类真实数据：
 * 1. 中英混写的单语歌词（说唱/R&B 常见）——相邻两句语言系统不同、间隔一两秒，
 *    绝不能被判成"原文 + 翻译"；
 * 2. 时间戳完全相同的双语歌词 —— 必须配对；
 * 3. **行尾带结束时间戳**的 AMLL 增强型 LRC —— 行尾那个时间戳不能被当成行起点，
 *    否则翻译会被复制成一条独立歌词行。
 */
class LrcParserTranslationTest {

    private val parser = LrcParser()

    @Test
    fun `中英混写单语歌词不应被误判为翻译`() {
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

        assertEquals("十行歌词必须全部保留", 10, lyrics.lines.size)
        assertTrue("不应识别出任何翻译", !lyrics.hasTranslation)
        val target = lyrics.lines.first { it.text == "you are my only one" }
        assertNull("下一句中文是独立歌词，不是翻译", target.translation)
        assertTrue(
            "中文行必须作为独立一行存在",
            lyrics.lines.any { it.text == "我不能忘记她" },
        )
    }

    @Test
    fun `时间戳相同的双语歌词应配对为翻译`() {
        val lrc = """
            [00:10.00]Hello darkness my old friend
            [00:10.00]你好 黑暗 我的老朋友
            [00:14.00]I've come to talk with you again
            [00:14.00]我又来和你交谈
        """.trimIndent()

        val lyrics = parser.parse(lrc)

        assertEquals(2, lyrics.lines.size)
        assertTrue(lyrics.hasTranslation)
        assertEquals("你好 黑暗 我的老朋友", lyrics.lines[0].translation)
        assertEquals("我又来和你交谈", lyrics.lines[1].translation)
    }

    /** 时间戳差几十毫秒的不再配对：用户明确要求只认完全相同的时间戳。 */
    @Test
    fun `时间戳不完全相同则不配对`() {
        val lrc = """
            [00:10.00]Hello darkness my old friend
            [00:10.05]你好 黑暗 我的老朋友
            [00:14.00]I've come to talk with you again
            [00:14.06]我又来和你交谈
        """.trimIndent()

        val lyrics = parser.parse(lrc)

        assertEquals("零容差：4 行各自独立", 4, lyrics.lines.size)
        assertTrue(!lyrics.hasTranslation)
    }

    /**
     * 用户实测反例（AMLL 逐字 + 行尾结束时间戳）：
     * 行尾 `[02:08.168]` 是**结束时间**，不是又一个行起点。
     */
    @Test
    fun `行尾结束时间戳不应产生额外歌词行`() {
        val lrc = "[02:05.395] <02:05.395>I <02:05.617>must <02:05.816>be " +
            "<02:05.992>gettin' <02:06.192>too <02:06.378>flashy <02:06.601>y'all " +
            "<02:06.784>shouldn't <02:06.977>have <02:07.161>let <02:07.368>the " +
            "<02:07.568>world <02:07.792>gas <02:07.960>me <02:08.168>\n" +
            "[02:05.395]我一定是太过招摇 你们不该煽动大家 反而给我打了气[02:08.168]"

        val lyrics = parser.parse(lrc)

        assertEquals("只应有一行歌词（翻译挂在它上面）", 1, lyrics.lines.size)
        val line = lyrics.lines[0]
        assertEquals("行起点应精确到毫秒", 125_395L, line.startMs)
        assertEquals("行尾时间戳应作为 endMs", 128_168L, line.endMs)
        assertEquals(
            "翻译应正确配对",
            "我一定是太过招摇 你们不该煽动大家 反而给我打了气",
            line.translation,
        )
        assertTrue("英文原文应剥离逐字标记", line.text.startsWith("I must be gettin'"))
        assertEquals(LyricsFormat.EnhancedLrc, lyrics.format)
        assertTrue("逐字时间轴应解析出来", line.syllables.isNotEmpty())
    }

    /** 小数位数任意，均按「秒的小数」以双精度换算并四舍五入到毫秒。 */
    @Test
    fun `时间戳小数位换算精度`() {
        val lrc = """
            [00:01.5]一位小数
            [00:02.55]两位小数
            [00:03.395]三位小数
            [00:04.3958]四位小数
        """.trimIndent()

        val lyrics = parser.parse(lrc)
        val starts = lyrics.lines.map { it.startMs }

        assertEquals(
            listOf(1_500L, 2_550L, 3_395L, 4_396L),
            starts,
        )
    }

    @Test
    fun `纯LRC应识别为Lrc格式并解析元数据`() {
        val lrc = """
            [ti:测试标题]
            [ar:测试歌手]
            [offset:-300]
            [00:01.00]第一句
            [00:05.00]第二句
        """.trimIndent()

        val lyrics = parser.parse(lrc)

        assertEquals(LyricsFormat.Lrc, lyrics.format)
        assertEquals("测试标题", lyrics.metadata["ti"])
        assertEquals(-300L, lyrics.offsetMs)
        assertEquals(2, lyrics.lines.size)
    }

    /** 重复副歌：一行多个行首时间戳，应展开为多行。 */
    @Test
    fun `行首多时间戳应展开为多行`() {
        val lrc = "[00:10.00][00:30.00][00:50.00]副歌一句"

        val lyrics = parser.parse(lrc)

        assertEquals(3, lyrics.lines.size)
        assertEquals(listOf(10_000L, 30_000L, 50_000L), lyrics.lines.map { it.startMs })
        assertTrue(lyrics.lines.all { it.text == "副歌一句" })
    }
}