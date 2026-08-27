package com.wxjxpp.neiro.core.lyrics

import com.wxjxpp.neiro.core.model.LyricsFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LrcParser] 的翻译配对回归测试。
 *
 * 覆盖两类真实数据：
 * 1. 中英混写的单语歌词（说唱/R&B 常见）——相邻两句语言系统不同、间隔一两秒，
 *    绝不能被判成"原文 + 翻译"；
 * 2. 真正的双语歌词——翻译行与原文行时间戳相同或仅差几十毫秒，必须正确配对。
 */
class LrcParserTranslationTest {

    private val parser = LrcParser()

    /** 用户实测反例：《苦咖啡·唯一》片段，全是独立歌词，没有任何翻译。 */
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
        // 重点校验用户举的那一对
        val target = lyrics.lines.first { it.text == "you are my only one" }
        assertNull("下一句中文是独立歌词，不是翻译", target.translation)
        assertTrue(
            "中文行必须作为独立一行存在",
            lyrics.lines.any { it.text == "我不能忘记她" },
        )
    }

    /** 时间戳完全相同的标准双语 LRC：必须配对成 原文 + 翻译。 */
    @Test
    fun `时间戳相同的双语歌词应配对为翻译`() {
        val lrc = """
            [00:10.00]Hello darkness my old friend
            [00:10.00]你好 黑暗 我的老朋友
            [00:14.00]I've come to talk with you again
            [00:14.00]我又来和你交谈
            [00:18.00]Because a vision softly creeping
            [00:18.00]因为有个幻影轻轻爬来
            [00:22.00]Left its seeds while I was sleeping
            [00:22.00]在我睡着时留下种子
        """.trimIndent()

        val lyrics = parser.parse(lrc)

        assertEquals("应合并为 4 行", 4, lyrics.lines.size)
        assertTrue(lyrics.hasTranslation)
        assertEquals("你好 黑暗 我的老朋友", lyrics.lines[0].translation)
        assertEquals("我又来和你交谈", lyrics.lines[1].translation)
    }

    /** 导出器抖动：翻译行比原文晚几十毫秒，仍应配对。 */
    @Test
    fun `时间戳相差数十毫秒的双语歌词应配对`() {
        val lrc = """
            [00:10.00]Hello darkness my old friend
            [00:10.05]你好 黑暗 我的老朋友
            [00:14.00]I've come to talk with you again
            [00:14.06]我又来和你交谈
            [00:18.00]Because a vision softly creeping
            [00:18.04]因为有个幻影轻轻爬来
            [00:22.00]Left its seeds while I was sleeping
            [00:22.07]在我睡着时留下种子
        """.trimIndent()

        val lyrics = parser.parse(lrc)

        assertEquals("应合并为 4 行", 4, lyrics.lines.size)
        assertTrue(lyrics.hasTranslation)
        assertEquals("你好 黑暗 我的老朋友", lyrics.lines[0].translation)
    }

    /** 元数据与格式识别不受配对逻辑影响。 */
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
}