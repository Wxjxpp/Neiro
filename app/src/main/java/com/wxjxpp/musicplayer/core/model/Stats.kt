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