package com.wxjxpp.musicplayer.core.model

/**
 * 歌词模型。
 *
 * 设计目标：一套模型同时承载 LRC / 增强型 LRC（逐字）/ TTML / SRT。
 * 解析器只负责把各种格式转成 [Lyrics]，UI 只渲染 [Lyrics]，
 * 新增格式不需要改渲染层。
 */

enum class LyricsFormat { Lrc, EnhancedLrc, Ttml, Srt, Plain, Unknown }

/**
 * 逐字/音节时间戳。增强型 LRC 的 `<00:12.34>` 和 TTML 的 span 都映射到这里。
 * 普通 LRC 解析后该列表为空。
 */
data class LyricSyllable(
    val text: String,
    val startMs: Long,
    val endMs: Long? = null,
)

/**
 * 一行歌词。
 *
 * [translation] 与 [romanization] 独立字段，
 * 便于"原文 / 翻译 / 罗马音"三行同时显示或按设置隐藏。
 */
data class LyricLine(
    val startMs: Long,
    val endMs: Long? = null,
    val text: String,
    val translation: String? = null,
    val romanization: String? = null,
    val syllables: List<LyricSyllable> = emptyList(),
    /** TTML 里的演唱者标记，可用于对唱左右分栏显示。 */
    val agent: String? = null,
) {
    val isWordByWord: Boolean get() = syllables.isNotEmpty()
}

data class Lyrics(
    val format: LyricsFormat = LyricsFormat.Unknown,
    val lines: List<LyricLine> = emptyList(),
    /** 整体时间偏移（毫秒），对应 LRC 的 [offset:] 标签。 */
    val offsetMs: Long = 0L,
    val metadata: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean get() = lines.isEmpty()
    val hasTranslation: Boolean get() = lines.any { it.translation != null }
    val hasRomanization: Boolean get() = lines.any { it.romanization != null }
    val isWordByWord: Boolean get() = lines.any { it.isWordByWord }

    companion object {
        val Empty = Lyrics()
    }
}