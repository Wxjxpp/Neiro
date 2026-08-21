package com.wxjxpp.musicplayer.core.model

/**
 * 听歌记录 / 听歌日记 / 年度报告 相关模型。
 */

/** 单次播放事件。年度报告和听歌日记都基于这张流水表统计。 */
data class PlayEvent(
    val id: String,
    val songId: String,
    val startedAtMs: Long,
    /** 实际听了多久，用于区分"划过"与"真的听了"。 */
    val listenedMs: Long,
    val completed: Boolean,
)

/** 听歌日记条目：某天的听歌记录 + 用户手写内容。 */
data class DiaryEntry(
    val id: String,
    val dateMs: Long,
    val songId: String?,
    val mood: String? = null,
    val note: String = "",
    val createdAtMs: Long = 0L,
)

/** 年度/周期报告。统计维度先占位，具体算法在 feature 层实现。 */
data class ListeningReport(
    val periodLabel: String,
    val totalSongs: Int = 0,
    val totalDurationMs: Long = 0L,
    val topSongs: List<Song> = emptyList(),
    val topArtists: List<Artist> = emptyList(),
    val topAlbums: List<Album> = emptyList(),
    /** 0..23 小时分布，用于"你最常在深夜听歌"这类结论。 */
    val hourHistogram: List<Int> = emptyList(),
)

/**
 * 听歌热力图单日数据（GitHub 风格）。
 *
 * 等级划分：0 首=0 级；1-4 首=1 级；5-9 首=2 级；10-19 首=3 级；
 * 20-39 首=4 级；40+ 首=5 级。
 */
data class HeatmapDay(
    /** 当天 0 点的时间戳（毫秒）。 */
    val dateMs: Long,
    /** 当天播放事件数（听过的歌，划过 <5s 不计）。 */
    val playCount: Int,
    /** 当天应用启动次数。 */
    val launchCount: Int,
    /** 当天总收听时长（毫秒）。 */
    val listenedMs: Long,
    /** 当天听过的歌曲标签 → 次数（取自歌曲元数据 tags）。 */
    val topTags: Map<String, Int>,
) {
    val level: Int
        get() = when {
            playCount <= 0 -> 0
            playCount < 5 -> 1
            playCount < 10 -> 2
            playCount < 20 -> 3
            playCount < 40 -> 4
            else -> 5
        }
}